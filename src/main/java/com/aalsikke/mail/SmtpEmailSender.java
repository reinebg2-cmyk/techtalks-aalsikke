package com.aalsikke.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * S5-02: sends the app's real emails (verification, reminders, security alerts, password reset).
 * Active only when app.mail.enabled=true (env MAIL_ENABLED); otherwise LoggingEmailSender is used.
 * KAN-361: this class builds the content; the active MailTransport delivers it
 * (SMTP by default, Brevo's HTTPS API with MAIL_TRANSPORT=brevo).
 * A sending failure is logged and swallowed, so registration still succeeds and the user can press Resend.
 * Reminders are the exception: their failure is thrown so the reminder job retries.
 */
@Component
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    /** KAN-336: reminder times are shown in Lebanon time (the DB stores UTC). */
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Beirut");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH);

    private final MailTransport transport;
    private final String baseUrl;

    public SmtpEmailSender(MailTransport transport,
                           @Value("${app.base-url}") String baseUrl) {
        this.transport = transport;
        this.baseUrl = baseUrl;
    }

    @Override
    public void sendVerificationEmail(String to, String verificationLink) {
        try {
            transport.send(to, "Verify your Aal Sikke email", plainText(verificationLink), html(verificationLink));
            log.info("[EMAIL] Verification email sent to {}", to);
        } catch (MailException e) {
            // Don't fail registration or resend: the user can request a new link later.
            log.error("[EMAIL] Could not send verification email to {}: {}", to, e.getMessage());
        }
    }

    @Override
    public void sendReminderEmail(String to, String name, String taskTitle, OffsetDateTime dueAt) {
        // Thrown on failure (MailException is not caught): the reminder job retries next minute.
        String title = taskTitle.replaceAll("[\\r\\n]+", " ");   // keep the subject on one line
        String due = dueAt == null ? null : WHEN.format(dueAt.atZoneSameInstant(APP_ZONE));
        transport.send(to, "Reminder: " + title, reminderText(name, title, due), reminderHtml(name, title, due));
        log.info("[EMAIL] Reminder email sent to {}", to);
    }

    @Override
    public void sendSecurityAlert(String to, String name, String title, String detail) {
        try {
            String when = WHEN.format(OffsetDateTime.now().atZoneSameInstant(APP_ZONE));
            transport.send(to, "Security alert: " + title,
                    securityText(name, title, detail, when), securityHtml(name, title, detail, when));
            log.info("[EMAIL] Security alert sent to {}", to);
        } catch (MailException e) {
            // KAN-337: the change already happened; a failed alert must not undo or block it.
            log.error("[EMAIL] Could not send security alert to {}: {}", to, e.getMessage());
        }
    }

    private String securityText(String name, String title, String detail, String when) {
        return """
                Hi %s,

                %s
                %s
                When: %s (Beirut time)

                If this was you, you can ignore this email.
                If it wasn't, change your password right away: %s/settings.html
                """.formatted(name, title, detail, when, baseUrl);
    }

    private String securityHtml(String name, String title, String detail, String when) {
        return """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;color:#1f2937">
                  <h2 style="color:#b42318">Security alert</h2>
                  <p>Hi %s,</p>
                  <div style="border-left:4px solid #b42318;padding:8px 12px;background:#fdf3f2;border-radius:4px">
                    <p style="margin:0;font-size:16px"><strong>%s</strong></p>
                    <p style="margin:4px 0 0;color:#6b7280">%s</p>
                    <p style="margin:4px 0 0;color:#6b7280">When: %s (Beirut time)</p>
                  </div>
                  <p>If this was you, you can ignore this email.</p>
                  <p style="margin:24px 0">
                    <a href="%s/settings.html" style="background:#b42318;color:#ffffff;padding:12px 20px;border-radius:8px;text-decoration:none;font-weight:bold">This wasn't me</a>
                  </p>
                  <p style="font-size:13px;color:#6b7280">The button opens Settings, where you can change your password.</p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(name), HtmlUtils.htmlEscape(title), HtmlUtils.htmlEscape(detail),
                HtmlUtils.htmlEscape(when), HtmlUtils.htmlEscape(baseUrl));
    }

    private String reminderText(String name, String title, String due) {
        return """
                Hi %s,

                This is your reminder for: %s
                %s
                Open Aal Sikke: %s/dashboard.html
                """.formatted(name, title, due == null ? "" : "Due: " + due + "\n", baseUrl);
    }

    private String reminderHtml(String name, String title, String due) {
        String dueLine = due == null ? ""
                : "<p style=\"margin:4px 0 0;color:#6b7280\">Due: <strong>" + HtmlUtils.htmlEscape(due) + "</strong></p>";
        return """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;color:#1f2937">
                  <h2 style="color:#2C6E9E">Reminder</h2>
                  <p>Hi %s,</p>
                  <div style="border-left:4px solid #2C6E9E;padding:8px 12px;background:#f3f6fa;border-radius:4px">
                    <p style="margin:0;font-size:16px"><strong>%s</strong></p>
                    %s
                  </div>
                  <p style="margin:24px 0">
                    <a href="%s/dashboard.html" style="background:#2C6E9E;color:#ffffff;padding:12px 20px;border-radius:8px;text-decoration:none;font-weight:bold">Open Aal Sikke</a>
                  </p>
                  <p style="font-size:13px;color:#6b7280">You can turn reminder emails off in Settings &rarr; Event alerts.</p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(name), HtmlUtils.htmlEscape(title), dueLine,
                HtmlUtils.htmlEscape(baseUrl));
    }

    private static String plainText(String link) {
        return """
                Welcome to Aal Sikke!

                Please confirm your email address by opening this link:
                %s

                The link is valid for 1 hour and works once.
                If you didn't create an account, you can ignore this email.
                """.formatted(link);
    }

    private static String html(String link) {
        String safe = HtmlUtils.htmlEscape(link);
        return """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;color:#1f2937">
                  <h2 style="color:#2C6E9E">Welcome to Aal Sikke!</h2>
                  <p>Please confirm your email address to start using your account.</p>
                  <p style="margin:24px 0">
                    <a href="%s" style="background:#2C6E9E;color:#ffffff;padding:12px 20px;border-radius:8px;text-decoration:none;font-weight:bold">Verify my email</a>
                  </p>
                  <p style="font-size:13px;color:#6b7280">Or copy this link into your browser:<br>%s</p>
                  <p style="font-size:13px;color:#6b7280">The link is valid for 1 hour and works once.
                  If you didn't create an account, you can ignore this email.</p>
                </div>
                """.formatted(safe, safe);
    }

    @Override
    public void sendPasswordResetEmail(String to, String resetLink) {
        try {
            transport.send(to, "Reset your Aal Sikke password", resetText(resetLink), resetHtml(resetLink));
            log.info("[EMAIL] Password reset email sent to {}", to);
        } catch (MailException e) {
            // Silent for the user (so the response never reveals whether the account exists).
            log.error("[EMAIL] Could not send password reset email to {}: {}", to, e.getMessage());
        }
    }

    private static String resetText(String link) {
        return """
                We received a request to reset your Aal Sikke password.

                Choose a new password by opening this link:
                %s

                The link is valid for 30 minutes and works once.
                If you didn't ask for this, you can ignore this email. Your password will not change.
                """.formatted(link);
    }

    private static String resetHtml(String link) {
        String safe = HtmlUtils.htmlEscape(link);
        return """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;color:#1f2937">
                  <h2 style="color:#2C6E9E">Reset your password</h2>
                  <p>We received a request to reset your Aal Sikke password. Choose a new one with the button below.</p>
                  <p style="margin:24px 0">
                    <a href="%s" style="background:#2C6E9E;color:#ffffff;padding:12px 20px;border-radius:8px;text-decoration:none;font-weight:bold">Reset my password</a>
                  </p>
                  <p style="font-size:13px;color:#6b7280">Or copy this link into your browser:<br>%s</p>
                  <p style="font-size:13px;color:#6b7280">The link is valid for 30 minutes and works once.
                  If you didn't ask for this, you can ignore this email. Your password will not change.</p>
                </div>
                """.formatted(safe, safe);
    }

}