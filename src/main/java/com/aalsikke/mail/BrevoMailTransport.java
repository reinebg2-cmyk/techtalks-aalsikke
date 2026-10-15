package com.aalsikke.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailSendException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * KAN-361: sends through Brevo's transactional email API over HTTPS (port 443).
 * Used where SMTP ports are blocked (Railway Trial/Hobby). Enable with MAIL_TRANSPORT=brevo
 * and BREVO_API_KEY. MAIL_FROM must be a sender verified in Brevo.
 */
@Component
@ConditionalOnProperty(name = "app.mail.transport", havingValue = "brevo")
public class BrevoMailTransport implements MailTransport {

    static final URI ENDPOINT = URI.create("https://api.brevo.com/v3/smtp/email");
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final String apiKey;
    private final String from;

    public BrevoMailTransport(@Value("${app.mail.brevo-api-key}") String apiKey,
                              @Value("${app.mail.from}") String from) {
        this.apiKey = apiKey;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String text, String html) {
        HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                .timeout(TIMEOUT)
                .header("api-key", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body(from, to, subject, text, html), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new MailSendException("Brevo API not reachable: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MailSendException("Interrupted while sending email through Brevo", e);
        }

        // Brevo answers 201 Created with a messageId; anything else is a failure.
        if (response.statusCode() / 100 != 2) {
            throw new MailSendException("Brevo API returned " + response.statusCode() + ": " + response.body());
        }
    }

    /** The JSON body Brevo expects. Built by hand so no JSON library is needed. */
    static String body(String from, String to, String subject, String text, String html) {
        return "{\"sender\":{\"name\":\"Aal Sikke\",\"email\":" + json(from) + "},"
                + "\"to\":[{\"email\":" + json(to) + "}],"
                + "\"subject\":" + json(subject) + ","
                + "\"textContent\":" + json(text) + ","
                + "\"htmlContent\":" + json(html) + "}";
    }

    /** A JSON string literal: quotes, backslashes and control characters are escaped. */
    static String json(String value) {
        StringBuilder out = new StringBuilder(value.length() + 16).append('"');
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}