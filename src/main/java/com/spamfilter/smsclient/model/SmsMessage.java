package com.spamfilter.smsclient.model;

import java.time.Instant;
import java.util.UUID;

public class SmsMessage {

    private final String id;
    private final Direction direction;
    private final String source;
    private final String destination;
    private final String body;
    private final Instant timestamp;
    private volatile Classification classification;
    private volatile String status;

    public SmsMessage(Direction direction, String source, String destination, String body) {
        this.id = UUID.randomUUID().toString();
        this.direction = direction;
        this.source = source;
        this.destination = destination;
        this.body = body;
        this.timestamp = Instant.now();
        this.status = "PENDING";
    }

    public String getId() {
        return id;
    }

    public Direction getDirection() {
        return direction;
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

    public Classification getClassification() {
        return classification;
    }

    public void setClassification(Classification classification) {
        this.classification = classification;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
