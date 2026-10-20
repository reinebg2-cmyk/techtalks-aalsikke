package com.aalsikke.service;

import com.aalsikke.mail.EmailSender;
import com.aalsikke.model.EmailVerificationToken;
import com.aalsikke.model.User;
import com.aalsikke.repository.EmailVerificationTokenRepository;
import com.aalsikke.repository.UserRepository;
import com.aalsikke.util.VerificationTokens;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.aalsikke.exception.CodedException;
import com.aalsikke.util.EmailLang;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * KAN-262 email verification:
 * - a link is valid for 1 hour and works once,
 * - KAN-362: earlier links keep working until they expire; verifying closes all of them,
 * - the link stops working if the user changes their email in the meantime,
 * - resend has a 60-second cooldown and never reveals whether an email exists.
 */
@Service
public class EmailVerificationService {

    static final Duration LINK_LIFETIME = Duration.ofHours(1);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailSender emailSender;
    private final String baseUrl;

    public EmailVerificationService(EmailVerificationTokenRepository tokenRepository,
                                    UserRepository userRepository,
                                    EmailSender emailSender,
                                    @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.emailSender = emailSender;
        this.baseUrl = baseUrl;
    }

    /** KAN-272: create a new link for the user's current email and send it. */
    @Transactional
    public void sendVerification(User user) {
        sendVerification(user, "en");
    }

    /** KAN-272: create a new link for the user's current email and send it. */
    @Transactional
    public void sendVerification(User user, String lang) {
        OffsetDateTime now = OffsetDateTime.now();
        String rawToken = VerificationTokens.newRawToken();

        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserId(user.getId());
        token.setTokenHash(VerificationTokens.sha256Hex(rawToken));
        token.setEmail(user.getEmail());
        token.setExpiresAt(now.plus(LINK_LIFETIME));
        tokenRepository.save(token);

        emailSender.sendVerificationEmail(user.getEmail(),
                baseUrl + "/verify-email.html?token=" + rawToken,
                EmailLang.normalize(lang));
    }


    /**
     * KAN-362: used when an unverified user logs in. Sends a link only if they have no valid one
     * for their current email, so repeated logins don't pile up emails. The Resend button uses resend().
     */
    @Transactional
    public void sendIfNoOpenLink(User user) {
        sendIfNoOpenLink(user, "en");
    }

    @Transactional
    public void sendIfNoOpenLink(User user, String lang) {
        if (user.isEmailVerified()) {
            return;
        }
        boolean hasOpenLink = tokenRepository.existsByUserIdAndEmailIgnoreCaseAndUsedAtIsNullAndExpiresAtAfter(
                user.getId(), user.getEmail(), OffsetDateTime.now());
        if (!hasOpenLink) {
            sendVerification(user, lang);
        }
    }
    /** KAN-273: the link was opened. Marks the email verified, or throws a 400 with a clear message. */
    @Transactional
    public void verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidLink();
        }

        EmailVerificationToken token = tokenRepository
                .findByTokenHash(VerificationTokens.sha256Hex(rawToken.trim()))
                .orElseThrow(this::invalidLink);

        OffsetDateTime now = OffsetDateTime.now();
        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(now)) {
            throw invalidLink();
        }

        User user = userRepository.findById(token.getUserId()).orElseThrow(this::invalidLink);

        // The user changed their email after this link was sent.
        if (user.getEmail() == null || !user.getEmail().equalsIgnoreCase(token.getEmail())) {
            throw invalidLink();
        }

        user.setEmailVerified(true);
        // KAN-362: close every other open link of this user, so leftover emails can't be reused.
        tokenRepository.invalidateOpenTokens(user.getId(), now);
        token.setUsedAt(now);
        userRepository.save(user);
        tokenRepository.save(token);
    }

    /**
     * KAN-275: send a new link if the account exists, isn't verified yet and the last link
     * is older than the cooldown. Silent otherwise, so it can't be used to find accounts.
     */
    @Transactional
    public void resend(String email) {
        resend(email, "en");
    }

    @Transactional
    public void resend(String email, String lang) {
        if (email == null || email.isBlank()) {
            return;
        }
        userRepository.findByEmail(email.trim())
                .filter(user -> !user.isEmailVerified())
                .filter(this::cooldownPassed)
                .ifPresent(user -> sendVerification(user, lang));
    }

    private boolean cooldownPassed(User user) {
        OffsetDateTime cutoff = OffsetDateTime.now().minus(RESEND_COOLDOWN);
        return tokenRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId())
                .map(last -> last.getCreatedAt() == null || last.getCreatedAt().isBefore(cutoff))
                .orElse(true);
    }

    private IllegalArgumentException invalidLink() {
        return new CodedException(
                "VERIFICATION_LINK_INVALID",
                "This verification link is invalid or has expired. Request a new one.");
    }
}