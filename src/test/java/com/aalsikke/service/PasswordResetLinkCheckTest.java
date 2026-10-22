package com.aalsikke.service;

import com.aalsikke.mail.EmailSender;
import com.aalsikke.model.PasswordResetToken;
import com.aalsikke.model.User;
import com.aalsikke.repository.PasswordResetTokenRepository;
import com.aalsikke.repository.UserRepository;
import com.aalsikke.util.VerificationTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** KAN-386: opening a used or expired reset link must say so right away, and checking must not use up the link. */
@ExtendWith(MockitoExtension.class)
class PasswordResetLinkCheckTest {

    private static final String RAW = "raw-reset-token-from-the-email";
    private static final String EMAIL = "lara@example.com";

    @Mock PasswordResetTokenRepository tokens;
    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;
    @Mock EmailSender emailSender;

    private PasswordResetService service;
    private PasswordResetToken token;
    private User lara;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(tokens, users, encoder, emailSender, "http://localhost:8080");

        lara = new User();
        lara.setEmail(EMAIL);

        token = new PasswordResetToken();
        token.setTokenHash(VerificationTokens.sha256Hex(RAW));
        token.setEmail(EMAIL);
        token.setExpiresAt(OffsetDateTime.now().plusMinutes(20));
    }

    @Test
    void check_usedLink_isRejected() {
        token.setUsedAt(OffsetDateTime.now().minusMinutes(5));
        when(tokens.findByTokenHash(VerificationTokens.sha256Hex(RAW))).thenReturn(Optional.of(token));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.checkResetLink(RAW));
        assertTrue(e.getMessage().contains("invalid or has expired"));
    }

    @Test
    void check_expiredLink_isRejected() {
        token.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        when(tokens.findByTokenHash(VerificationTokens.sha256Hex(RAW))).thenReturn(Optional.of(token));

        assertThrows(IllegalArgumentException.class, () -> service.checkResetLink(RAW));
    }

    @Test
    void check_missingToken_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.checkResetLink(null));
        assertThrows(IllegalArgumentException.class, () -> service.checkResetLink("  "));
    }

    @Test
    void check_validLink_isAccepted_andNotUsedUp() {
        when(tokens.findByTokenHash(VerificationTokens.sha256Hex(RAW))).thenReturn(Optional.of(token));
        when(users.findById(any())).thenReturn(Optional.of(lara));

        assertDoesNotThrow(() -> service.checkResetLink(RAW));

        // checking only: no password change, link stays usable, no email
        verify(users, never()).save(any());
        verify(tokens, never()).invalidateOpenTokens(any(), any());
        verifyNoInteractions(emailSender, encoder);
    }
}