package com.aalsikke.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Local development: prints the verification link in the IntelliJ console instead of emailing it. */
@Component
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void sendVerificationEmail(String to, String verificationLink) {
        log.info("[EMAIL] Verify {} -> {}", to, verificationLink);
    }
}