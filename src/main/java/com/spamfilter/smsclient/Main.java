package com.spamfilter.smsclient;

import com.spamfilter.smsclient.config.AppConfig;
import com.spamfilter.smsclient.db.Database;
import com.spamfilter.smsclient.db.UserRepository;
import com.spamfilter.smsclient.servlet.HealthServlet;
import com.spamfilter.smsclient.servlet.IndexServlet;
import com.spamfilter.smsclient.servlet.LoginServlet;
import com.spamfilter.smsclient.servlet.LogoutServlet;
import com.spamfilter.smsclient.servlet.MessageHistoryServlet;
import com.spamfilter.smsclient.servlet.NumbersServlet;
import com.spamfilter.smsclient.servlet.RegisterServlet;
import com.spamfilter.smsclient.servlet.SendSmsServlet;
import com.spamfilter.smsclient.smpp.SmppService;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Entry point for the SMS client: a self-contained Java SE app that starts
 * an embedded Tomcat, serves a login-protected web UI for composing an SMS
 * from one of the user's own numbers, and submits it via SMPP to the
 * teammate's SMPP server (which classifies it, forwards to Osmocom or
 * blocks it, and records the result in Neon).
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        AppConfig config = new AppConfig();
        SmppService smppService = new SmppService(config);
        DataSource dataSource = Database.connect(config);
        UserRepository userRepository = new UserRepository(dataSource);

        smppService.start();

        Tomcat tomcat = new Tomcat();
        Path baseDir = Files.createTempDirectory("sms-client-tomcat");
        tomcat.setBaseDir(baseDir.toString());
        tomcat.setPort(config.serverPort());
        tomcat.getConnector();

        Context ctx = tomcat.addContext("", baseDir.toFile().getAbsolutePath());

        Tomcat.addServlet(ctx, "index", new IndexServlet(userRepository));
        ctx.addServletMappingDecoded("/", "index");

        Tomcat.addServlet(ctx, "register", new RegisterServlet(userRepository));
        ctx.addServletMappingDecoded("/register", "register");

        Tomcat.addServlet(ctx, "login", new LoginServlet(userRepository));
        ctx.addServletMappingDecoded("/login", "login");

        Tomcat.addServlet(ctx, "logout", new LogoutServlet());
        ctx.addServletMappingDecoded("/logout", "logout");

        Tomcat.addServlet(ctx, "numbers", new NumbersServlet(userRepository));
        ctx.addServletMappingDecoded("/numbers", "numbers");

        Tomcat.addServlet(ctx, "sendSms", new SendSmsServlet(smppService, userRepository));
        ctx.addServletMappingDecoded("/api/sms/send", "sendSms");

        Tomcat.addServlet(ctx, "health", new HealthServlet(smppService));
        ctx.addServletMappingDecoded("/api/health", "health");

        Tomcat.addServlet(ctx, "history", new MessageHistoryServlet(userRepository));
        ctx.addServletMappingDecoded("/api/sms/history", "history");

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
