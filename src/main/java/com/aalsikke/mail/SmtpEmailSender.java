package com.aalsikke.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * S5-02: sends the verification link as a real email over SMTP.
 * Active only when app.mail.enabled=true (env MAIL_ENABLED); otherwise LoggingEmailSender is used.
 * A sending failure is logged and swallowed, so registration still succeeds and the user can press Resend.
 */
@Component
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailSender(JavaMailSender mailSender, @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendVerificationEmail(String to, String verificationLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from, "Aal Sikke");
            helper.setTo(to);
            helper.setSubject("Verify your Aal Sikke email");
            helper.setText(plainText(verificationLink), html(verificationLink));
            mailSender.send(message);
            log.info("[EMAIL] Verification email sent to {}", to);
        } catch (MailException | MessagingException | java.io.UnsupportedEncodingException e) {
            // Don't fail registration or resend: the user can request a new link later.
            log.error("[EMAIL] Could not send verification email to {}: {}", to, e.getMessage());
        }
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
}