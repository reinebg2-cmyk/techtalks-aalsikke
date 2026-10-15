package com.aalsikke.mail;

/**
 * KAN-361: how a finished email leaves the app. SmtpEmailSender builds the content
 * (subject, plain text, HTML) and hands it to the active transport:
 * - smtp (default): JavaMailSender, used locally with Mailpit or any SMTP server
 * - brevo: Brevo's HTTPS API, for hosts that block SMTP ports (Railway Trial/Hobby)
 * Selected with app.mail.transport (env MAIL_TRANSPORT).
 * A failure is thrown as a MailException; the caller decides whether to swallow it or retry.
 */
public interface MailTransport {

    void send(String to, String subject, String text, String html);
}