package com.spamfilter.smsclient.smpp;

import com.spamfilter.smsclient.ai.AiClassifierClient;
import com.spamfilter.smsclient.config.AppConfig;
import com.spamfilter.smsclient.model.Classification;
import com.spamfilter.smsclient.model.Direction;
import com.spamfilter.smsclient.model.SmsMessage;
import com.spamfilter.smsclient.store.MessageStore;
import org.jsmpp.InvalidResponseException;
import org.jsmpp.PDUException;
import org.jsmpp.bean.AlertNotification;
import org.jsmpp.bean.Alphabet;
import org.jsmpp.bean.BindType;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.DeliverSm;
import org.jsmpp.bean.ESMClass;
import org.jsmpp.bean.GeneralDataCoding;
import org.jsmpp.bean.MessageClass;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.RegisteredDelivery;
import org.jsmpp.bean.SMSCDeliveryReceipt;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.extra.NegativeResponseException;
import org.jsmpp.extra.ResponseTimeoutException;
import org.jsmpp.extra.SessionState;
import org.jsmpp.session.BindParameter;
import org.jsmpp.session.DataSmResult;
import org.jsmpp.session.MessageReceiverListener;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Binds to the Osmocom SMSC (osmo-msc's built-in SMPP interface) as a
 * transceiver ESME: submits outbound SMS and receives inbound SMS for
 * classification. If the SMSC isn't reachable yet, it keeps retrying in
 * the background instead of failing startup - useful since the network
 * team's Osmocom stack may come up after this app does.
 */
public class SmppService {

    private static final Logger log = LoggerFactory.getLogger(SmppService.class);
    private static final long RETRY_DELAY_SECONDS = 15;

    private final AppConfig config;
    private final MessageStore store;
    private final AiClassifierClient aiClient;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "smpp-bind-retry");
        t.setDaemon(true);
        return t;
    });

    private volatile SMPPSession session;
    private volatile boolean stopping = false;

    public SmppService(AppConfig config, MessageStore store, AiClassifierClient aiClient) {
        this.config = config;
        this.store = store;
        this.aiClient = aiClient;
    }

    public void start() {
        if (!config.smppEnabled()) {
            log.info("SMPP disabled via config (smpp.enabled=false); SMS client will not bind to an SMSC");
            return;
        }
        executor.submit(this::connectWithRetry);
    }

    private void connectWithRetry() {
        while (!stopping) {
            try {
                bind();
                return;
            } catch (Exception e) {
                log.warn("SMPP bind to {}:{} failed ({}); retrying in {}s",
                        config.smppHost(), config.smppPort(), e.getMessage(), RETRY_DELAY_SECONDS);
                sleep();
            }
        }
    }

    private void sleep() {
        try {
            Thread.sleep(TimeUnit.SECONDS.toMillis(RETRY_DELAY_SECONDS));
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void bind() throws IOException {
        SMPPSession newSession = new SMPPSession();
        newSession.setMessageReceiverListener(new IncomingSmsListener());
        newSession.addSessionStateListener((newState, oldState, source) ->
                log.info("SMPP session state changed: {} -> {}", oldState, newState));

        newSession.connectAndBind(config.smppHost(), config.smppPort(),
                new BindParameter(BindType.BIND_TRX, config.smppSystemId(), config.smppPassword(),
                        config.smppSystemType(), TypeOfNumber.UNKNOWN, NumberingPlanIndicator.UNKNOWN, null));

        this.session = newSession;
        log.info("SMPP bound to {}:{} as {}", config.smppHost(), config.smppPort(), config.smppSystemId());
    }

    public boolean isBound() {
        SMPPSession s = session;
        return s != null && s.getSessionState() == SessionState.BOUND_TRX;
    }

    public String submit(String source, String destination, String body) {
        SMPPSession s = session;
        if (s == null || !isBound()) {
            throw new IllegalStateException("SMPP session is not bound to the SMSC");
        }
        try {
            return s.submitShortMessage(
                    "CMT",
                    TypeOfNumber.UNKNOWN, NumberingPlanIndicator.UNKNOWN, source,
                    TypeOfNumber.UNKNOWN, NumberingPlanIndicator.UNKNOWN, destination,
                    new ESMClass(),
                    (byte) 0,
                    (byte) 0,
                    null,
                    null,
                    new RegisteredDelivery(SMSCDeliveryReceipt.DEFAULT),
                    (byte) 0,
                    new GeneralDataCoding(Alphabet.ALPHA_DEFAULT, MessageClass.CLASS1, false),
                    (byte) 0,
                    body.getBytes(StandardCharsets.UTF_8))
                    .getMessageId();
        } catch (PDUException | ResponseTimeoutException | InvalidResponseException
                 | NegativeResponseException | IOException e) {
            throw new RuntimeException("SMPP submit_sm failed: " + e.getMessage(), e);
        }
    }

    public void stop() {
        stopping = true;
        executor.shutdownNow();
        SMPPSession s = session;
        if (s != null) {
            s.unbindAndClose();
        }
    }

    private class IncomingSmsListener implements MessageReceiverListener {

        @Override
        public void onAcceptDeliverSm(DeliverSm deliverSm) {
            try {
                String text = new String(deliverSm.getShortMessage(), StandardCharsets.UTF_8);
                SmsMessage message = new SmsMessage(Direction.RECEIVED,
                        deliverSm.getSourceAddr(), deliverSm.getDestAddress(), text);

                Classification classification = aiClient.classify(text);
                message.setClassification(classification);
                message.setStatus(classification.isSpam() ? "FLAGGED_SPAM" : "DELIVERED");
                store.add(message);

                if (classification.isSpam()) {
                    log.warn("Incoming SMS from {} flagged as SPAM (score={}): \"{}\"",
                            deliverSm.getSourceAddr(), classification.getScore(), text);
                } else {
                    log.info("Incoming SMS from {} to {} stored ({})",
                            deliverSm.getSourceAddr(), deliverSm.getDestAddress(), classification.getLabel());
                }
            } catch (Exception e) {
                log.error("Failed to process incoming deliver_sm", e);
            }
        }

        @Override
        public void onAcceptAlertNotification(AlertNotification alertNotification) {
            log.debug("Received alert_notification: {}", alertNotification);
        }

        @Override
        public DataSmResult onAcceptDataSm(DataSm dataSm, Session source) {
            log.debug("Received data_sm: {}", dataSm);
            return null;
        }
    }
}
