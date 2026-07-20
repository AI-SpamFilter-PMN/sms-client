package com.spamfilter.smsclient.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads application.properties from the classpath; any key can be overridden
 * with a matching -Dkey=value system property (handy for local dev / demos).
 */
public class AppConfig {

    private final Properties props = new Properties();

    public AppConfig() {
        this("application.properties");
    }

    public AppConfig(String resourceName) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + resourceName + " on the classpath");
            }
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + resourceName, e);
        }
    }

    private String get(String key) {
        String override = System.getProperty(key);
        return override != null ? override : props.getProperty(key);
    }

    public int serverPort() {
        return Integer.parseInt(get("server.port"));
    }

    public boolean smppEnabled() {
        return Boolean.parseBoolean(get("smpp.enabled"));
    }

    public String smppHost() {
        return get("smpp.host");
    }

    public int smppPort() {
        return Integer.parseInt(get("smpp.port"));
    }

    public String smppSystemId() {
        return get("smpp.systemId");
    }

    public String smppPassword() {
        return get("smpp.password");
    }

    public String smppSystemType() {
        return get("smpp.systemType");
    }

    public String aiClassifyUrl() {
        return get("ai.classify.url");
    }

    public int aiConnectTimeoutMs() {
        return Integer.parseInt(get("ai.connectTimeoutMs"));
    }

    public int aiReadTimeoutMs() {
        return Integer.parseInt(get("ai.readTimeoutMs"));
    }

    public boolean blockSpam() {
        return Boolean.parseBoolean(get("sms.blockSpam"));
    }
}
