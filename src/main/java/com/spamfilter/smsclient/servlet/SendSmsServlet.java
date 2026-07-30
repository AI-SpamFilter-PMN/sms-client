package com.spamfilter.smsclient.servlet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spamfilter.smsclient.ai.AiClassifierClient;
import com.spamfilter.smsclient.config.AppConfig;
import com.spamfilter.smsclient.model.Classification;
import com.spamfilter.smsclient.model.Direction;
import com.spamfilter.smsclient.model.SmsMessage;
import com.spamfilter.smsclient.smpp.SmppService;
import com.spamfilter.smsclient.store.MessageStore;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * POST /api/sms/send  { "source": "...", "destination": "...", "body": "..." }
 *
 * Classifies the body with the AI service, then submits it to the SMSC via
 * SMPP unless it's spam and sms.blockSpam=true.
 */
public class SendSmsServlet extends HttpServlet {

    private final MessageStore store;
    private final AiClassifierClient aiClient;
    private final SmppService smppService;
    private final AppConfig config;
    private final ObjectMapper mapper = new ObjectMapper();

    public SendSmsServlet(MessageStore store, AiClassifierClient aiClient, SmppService smppService, AppConfig config) {
        this.store = store;
        this.aiClient = aiClient;
        this.smppService = smppService;
        this.config = config;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonNode json = mapper.readTree(req.getInputStream());
        String source = json.path("source").asText(null);
        String destination = json.path("destination").asText(null);
        String body = json.path("body").asText(null);

        if (isBlank(source) || isBlank(destination) || isBlank(body)) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST, "source, destination and body are required");
            return;
        }

        SmsMessage message = new SmsMessage(Direction.SENT, source, destination, body);
        Classification classification = aiClient.classify(body);
        message.setClassification(classification);

        boolean blocked = classification.isSpam() && config.blockSpam();
        if (blocked) {
            message.setStatus("BLOCKED_SPAM");
        } else {
            try {
                smppService.submit(source, destination, body);
                message.setStatus("SENT");
            } catch (RuntimeException e) {
                message.setStatus("FAILED");
                store.add(message);
                writeError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, e.getMessage());
                return;
            }
        }

        store.add(message);
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("application/json");
        mapper.writeValue(resp.getOutputStream(), toJson(message));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private void writeError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        mapper.writeValue(resp.getOutputStream(), Map.of("error", message));
    }

    static Map<String, Object> toJson(SmsMessage m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", m.getId());
        map.put("direction", m.getDirection());
        map.put("source", m.getSource());
        map.put("destination", m.getDestination());
        map.put("body", m.getBody());
        map.put("status", m.getStatus());
        map.put("timestamp", m.getTimestamp().toString());
        if (m.getClassification() != null) {
            map.put("classification", Map.of(
                    "label", m.getClassification().getLabel(),
                    "score", m.getClassification().getScore()));
        }
        return map;
    }
}
