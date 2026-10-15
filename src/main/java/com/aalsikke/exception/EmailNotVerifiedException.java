package com.aalsikke.exception;

/** KAN-274: correct password, but the email isn't verified yet, so login is refused. */
public class EmailNotVerifiedException extends RuntimeException {

    private final String email;

    public EmailNotVerifiedException(String email) {
        super("Please verify your email before logging in. Check your inbox for the link.");
        this.email = email;
    }

    public String getEmail() { return email; }
}