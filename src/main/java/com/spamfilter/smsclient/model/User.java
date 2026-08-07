package com.spamfilter.smsclient.model;

public class User {

    private final String id;
    private final String email;
    private final String displayName;

    public User(String id, String email, String displayName) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }
}
