package com.aalsikke.mail;

/**
 * KAN-272: how verification emails leave the app. The only implementation for now
 * logs the link to the console (LoggingEmailSender); a real SMTP sender can be added
 * later behind this interface without touching the verification code.
 */
public interface EmailSender {

    void sendVerificationEmail(String to, String verificationLink);
}