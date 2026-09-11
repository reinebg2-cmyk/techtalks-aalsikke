package com.aalsikke.dto;

/**
 * Mirrors the fields posted by login.js: the "identifier" field accepts
 * either an email address or a phone number, plus the password.
 */
public class LoginRequest {

    private String identifier;
    private String password;

    public LoginRequest() {}

    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
