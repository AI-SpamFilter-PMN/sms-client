package com.spamfilter.smsclient.model;

public class Classification {

    public static final Classification UNKNOWN = new Classification("unknown", 0.0);

    private final String label;
    private final double score;

    public Classification(String label, double score) {
        this.label = label;
        this.score = score;
    }

    public String getLabel() {
        return label;
    }

    public double getScore() {
        return score;
    }

    public boolean isSpam() {
        return "spam".equalsIgnoreCase(label);
    }
}
