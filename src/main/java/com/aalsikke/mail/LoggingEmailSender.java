package com.aalsikke.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Local development: prints the verification link in the IntelliJ console instead of emailing it.
 *  Used unless app.mail.enabled=true (then SmtpEmailSender sends real emails). */
@Component
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void sendVerificationEmail(String to, String verificationLink) {
        log.info("[EMAIL] Verify {} -> {}", to, verificationLink);
    }

    @Override
    public void sendReminderEmail(String to, String name, String taskTitle, java.time.OffsetDateTime dueAt) {
        log.info("[EMAIL] Reminder to {}: \"{}\" (due {})", to, taskTitle, dueAt);
    }

    @Override
    public void sendReminderEmail(String to, String name, String taskTitle,
                                  java.time.OffsetDateTime dueAt, String lang) {
        log.info("[EMAIL] Reminder ({}) to {}: \"{}\" (due {})", lang, to, taskTitle, dueAt);
    }

    @Override
    public void sendSecurityAlert(String to, String name, String title, String detail) {
        log.info("[EMAIL] Security alert to {}: {} - {}", to, title, detail);
    }

    @Override
    public void sendSecurityAlert(String to, String name, String title, String detail, String lang) {
        log.info("[EMAIL] Security alert ({}) to {}: {} - {}", lang, to, title, detail);
    }

    @Override
    public void sendWelcomeEmail(String to, String name) {
        log.info("[EMAIL] Welcome email to {} ({})", to, name);
    }

    @Override
    public void sendWelcomeEmail(String to, String name, String lang) {
        log.info("[EMAIL] Welcome email ({}) to {} ({})", lang, to, name);
    }

    @Override
    public void sendPasswordResetEmail(String to, String resetLink) {
        log.info("[EMAIL] Password reset link for {}: {}", to, resetLink);
    }
}