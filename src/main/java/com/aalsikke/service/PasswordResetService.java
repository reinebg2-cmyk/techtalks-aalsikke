package com.aalsikke.service;

import com.aalsikke.mail.EmailSender;
import com.aalsikke.model.PasswordResetToken;
import com.aalsikke.model.User;
import com.aalsikke.repository.PasswordResetTokenRepository;
import com.aalsikke.repository.UserRepository;
import com.aalsikke.util.VerificationTokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.aalsikke.util.EmailLang;
import com.aalsikke.exception.CodedException;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Forgot password:
 * - a link is valid for 30 minutes and works once,
 * - a new link replaces the previous ones,
 * - the link stops working if the user changes their email in the meantime,
 * - requests have a 60-second cooldown and never reveal whether an email exists.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    static final Duration LINK_LIFETIME = Duration.ofMinutes(30);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final String baseUrl;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository,
                                UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                EmailSender emailSender,
                                @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.baseUrl = baseUrl;
    }

    /** Step 1: send a reset link if the account exists. Silent otherwise. */
    @Transactional
    public void requestReset(String email, String lang) {
        if (email == null || email.isBlank()) {
            return;
        }
        String l = EmailLang.normalize(lang);
        userRepository.findByEmail(email.trim())
                .filter(user -> user.getDeletedAt() == null)
                .filter(this::cooldownPassed)
                .ifPresent(user -> sendResetLink(user, l));
    }

    /** Step 2: the user opened the link and chose a new password. Throws a 400 if the link is bad. */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidLink();
        }

        PasswordResetToken token = tokenRepository
                .findByTokenHash(VerificationTokens.sha256Hex(rawToken.trim()))
                .orElseThrow(this::invalidLink);

        OffsetDateTime now = OffsetDateTime.now();
        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(now)) {
            throw invalidLink();
        }

        User user = userRepository.findById(token.getUserId()).orElseThrow(this::invalidLink);

        // The account was deleted, or the user changed their email after this link was sent.
        if (user.getDeletedAt() != null
                || user.getEmail() == null
                || !user.getEmail().equalsIgnoreCase(token.getEmail())) {
            throw invalidLink();
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // marks this link (and any other open one) as used: single use
        tokenRepository.invalidateOpenTokens(user.getId(), now);
        String lang = EmailLang.normalize(user.getPreferredLanguage());
        boolean ar = "ar".equals(lang);
        emailSender.sendSecurityAlert(user.getEmail(), user.getFullName(),
                ar ? "تم تغيير كلمة المرور" : "Your password was changed",
                ar ? "تمت إعادة تعيين كلمة المرور عبر رابط «نسيت كلمة المرور»."
                        : "Your password was reset using the Forgot password link.",
                lang);
    }


    private void sendResetLink(User user, String lang) {
        OffsetDateTime now = OffsetDateTime.now();
        tokenRepository.invalidateOpenTokens(user.getId(), now);

        String rawToken = VerificationTokens.newRawToken();

        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getId());
        token.setTokenHash(VerificationTokens.sha256Hex(rawToken));
        token.setEmail(user.getEmail());
        token.setExpiresAt(now.plus(LINK_LIFETIME));
        tokenRepository.save(token);

        try {
            emailSender.sendPasswordResetEmail(user.getEmail(),
                    baseUrl + "/reset-password.html?token=" + rawToken, lang);
        } catch (Exception e) {
            // never let a mail failure change the response: that would reveal the account exists
            log.warn("Could not send the password reset email", e);
        }
    }

    private boolean cooldownPassed(User user) {
        OffsetDateTime cutoff = OffsetDateTime.now().minus(RESEND_COOLDOWN);
        return tokenRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId())
                .map(last -> last.getCreatedAt() == null || last.getCreatedAt().isBefore(cutoff))
                .orElse(true);
    }

    private IllegalArgumentException invalidLink() {
        return new CodedException("RESET_LINK_INVALID",
                "This reset link is invalid or has expired. Request a new one.");
    }
}