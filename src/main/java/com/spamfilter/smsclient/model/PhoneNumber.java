package com.spamfilter.smsclient.model;

public class PhoneNumber {

    private final String id;
    private final String msisdn;
    private final String label;

    public PhoneNumber(String id, String msisdn, String label) {
        this.id = id;
        this.msisdn = msisdn;
        this.label = label;
    }

    public String getId() {
        return id;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public String getLabel() {
        return label;
    }
}
