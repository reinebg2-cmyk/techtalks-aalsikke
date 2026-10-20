package com.aalsikke.mail;

/**
 * KAN-272: how verification emails leave the app. The only implementation for now
 * logs the link to the console (LoggingEmailSender); a real SMTP sender can be added
 * later behind this interface without touching the verification code.
 */
public interface EmailSender {

    void sendVerificationEmail(String to, String verificationLink);

    /**
     * KAN-336: a task reminder. dueAt may be null (task with no deadline or end time).
     * Unlike verification, a failure is thrown, so the caller can retry later instead of
     * marking the reminder as sent.
     */
    void sendReminderEmail(String to, String name, String taskTitle, java.time.OffsetDateTime dueAt);

    default void sendReminderEmail(String to, String name, String taskTitle,
                                   java.time.OffsetDateTime dueAt, String lang) {
        sendReminderEmail(to, name, taskTitle, dueAt);
    }
    /**
     * KAN-337: tells the user something sensitive changed on their account (password, email).
     * Never throws: the change itself has already happened and must not fail because of email.
     */
    void sendSecurityAlert(String to, String name, String title, String detail);
    default void sendSecurityAlert(String to, String name, String title, String detail, String lang) {
        sendSecurityAlert(to, name, title, detail);
    }
    void sendPasswordResetEmail(String to, String resetLink);

    void sendWelcomeEmail(String to, String name);

    /** KAN-369: "your account is ready", sent once after the first successful login. Never throws. */
    default void sendWelcomeEmail(String to, String name, String lang) {
        sendWelcomeEmail(to, name);
    }

    default void sendVerificationEmail(String to, String verificationLink, String lang) {
        sendVerificationEmail(to, verificationLink);
    }

    default void sendPasswordResetEmail(String to, String resetLink, String lang) {
        sendPasswordResetEmail(to, resetLink);
    }
}