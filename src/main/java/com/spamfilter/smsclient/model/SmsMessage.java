package com.spamfilter.smsclient.model;

import java.time.Instant;
import java.util.UUID;

public class SmsMessage {

    private final String id;
    private final String source;
    private final String destination;
    private final String body;
    private final Instant timestamp;
    private volatile String status;
    private volatile String smppMessageId;

    public SmsMessage(String source, String destination, String body) {
        this.id = UUID.randomUUID().toString();
        this.source = source;
        this.destination = destination;
        this.body = body;
        this.timestamp = Instant.now();
        this.status = "PENDING";
    }

    public String getId() {
        return id;
    }

    public String getSource() {
        return source;
    }

    public String getDestination() {
        return destination;
    }

    public String getBody() {
        return body;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSmppMessageId() {
        return smppMessageId;
    }

    public void setSmppMessageId(String smppMessageId) {
        this.smppMessageId = smppMessageId;
    }
}
