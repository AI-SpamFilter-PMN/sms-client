package com.spamfilter.smsclient;

import com.spamfilter.smsclient.ai.AiClassifierClient;
import com.spamfilter.smsclient.config.AppConfig;
import com.spamfilter.smsclient.servlet.HealthServlet;
import com.spamfilter.smsclient.servlet.MessagesServlet;
import com.spamfilter.smsclient.servlet.SendSmsServlet;
import com.spamfilter.smsclient.smpp.SmppService;
import com.spamfilter.smsclient.store.MessageStore;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Entry point for the SMS client: a self-contained Java SE app that starts
 * an embedded Tomcat and exposes a small REST API in front of an SMPP ESME.
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        AppConfig config = new AppConfig();
        MessageStore store = new MessageStore();
        AiClassifierClient aiClient = new AiClassifierClient(config);
        SmppService smppService = new SmppService(config, store, aiClient);

        smppService.start();

        Tomcat tomcat = new Tomcat();
        Path baseDir = Files.createTempDirectory("sms-client-tomcat");
        tomcat.setBaseDir(baseDir.toString());
        tomcat.setPort(config.serverPort());
        tomcat.getConnector();

        Context ctx = tomcat.addContext("", baseDir.toFile().getAbsolutePath());

        Tomcat.addServlet(ctx, "sendSms", new SendSmsServlet(store, aiClient, smppService, config));
        ctx.addServletMappingDecoded("/api/sms/send", "sendSms");

        Tomcat.addServlet(ctx, "messages", new MessagesServlet(store));
        ctx.addServletMappingDecoded("/api/sms/messages", "messages");

        Tomcat.addServlet(ctx, "health", new HealthServlet(smppService));
        ctx.addServletMappingDecoded("/api/health", "health");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down SMS client...");
            smppService.stop();
            try {
                tomcat.stop();
            } catch (Exception e) {
                log.warn("Error stopping Tomcat", e);
            }
        }));

        tomcat.start();
        log.info("SMS client listening on http://localhost:{}", config.serverPort());
        tomcat.getServer().await();
    }
}
