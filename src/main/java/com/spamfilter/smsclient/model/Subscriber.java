package com.spamfilter.smsclient.model;

/** A subscriber registered on the private mobile network. */
public class Subscriber {

    private final String id;
    private final String msisdn;
    private final String imsi;
    private final String displayName;
    private final String status;

    public Subscriber(String id, String msisdn, String imsi, String displayName, String status) {
        this.id = id;
        this.msisdn = msisdn;
        this.imsi = imsi;
        this.displayName = displayName;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public String getImsi() {
        return imsi;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getStatus() {
        return status;
    }
}
