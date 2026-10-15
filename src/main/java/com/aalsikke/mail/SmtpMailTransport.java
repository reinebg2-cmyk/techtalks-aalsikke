package com.aalsikke.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;

/** KAN-361: sends over SMTP with JavaMailSender (the behaviour before KAN-361). The default transport. */
@Component
@ConditionalOnProperty(name = "app.mail.transport", havingValue = "smtp", matchIfMissing = true)
public class SmtpMailTransport implements MailTransport {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpMailTransport(JavaMailSender mailSender, @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String text, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from, "Aal Sikke");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
            mailSender.send(message);   // throws MailException (unchecked) when sending fails
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new MailPreparationException("Could not build email: " + e.getMessage(), e);
        }
    }
}