package com.aalsikke.service;

import com.aalsikke.mail.EmailSender;
import com.aalsikke.model.EmailVerificationToken;
import com.aalsikke.model.User;
import com.aalsikke.repository.EmailVerificationTokenRepository;
import com.aalsikke.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** KAN-362: earlier verification links keep working until they expire or the email is verified. */
class EmailVerificationServiceTest {

    private EmailVerificationTokenRepository tokenRepository;
    private UserRepository userRepository;
    private EmailSender emailSender;
    private EmailVerificationService service;
    private User user;

    @BeforeEach
    void setUp() {
        tokenRepository = mock(EmailVerificationTokenRepository.class);
        userRepository = mock(UserRepository.class);
        emailSender = mock(EmailSender.class);
        service = new EmailVerificationService(tokenRepository, userRepository, emailSender, "https://app.test");

        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("student@test.com");
        user.setEmailVerified(false);
    }

    @Test
    void sendingANewLinkDoesNotCancelEarlierLinks() {
        service.sendVerification(user);

        verify(tokenRepository, never()).invalidateOpenTokens(any(), any());
        verify(emailSender).sendVerificationEmail(eq("student@test.com"), any(), eq("en"));
    }

    @Test
    void verifyingClosesEveryOtherOpenLink() {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserId(user.getId());
        token.setEmail("student@test.com");
        token.setExpiresAt(OffsetDateTime.now().plusMinutes(30));
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        service.verify("any-raw-token");

        verify(tokenRepository).invalidateOpenTokens(eq(user.getId()), any());
    }

    @Test
    void loginSendsNoNewLinkWhileOneIsStillValid() {
        when(tokenRepository.existsByUserIdAndEmailIgnoreCaseAndUsedAtIsNullAndExpiresAtAfter(
                eq(user.getId()), eq("student@test.com"), any())).thenReturn(true);

        service.sendIfNoOpenLink(user);

        verify(emailSender, never()).sendVerificationEmail(any(), any());
    }

    @Test
    void loginSendsALinkWhenNoneIsValid() {
        when(tokenRepository.existsByUserIdAndEmailIgnoreCaseAndUsedAtIsNullAndExpiresAtAfter(
                eq(user.getId()), eq("student@test.com"), any())).thenReturn(false);

        service.sendIfNoOpenLink(user);

        verify(emailSender).sendVerificationEmail(eq("student@test.com"), any(), eq("en"));
        service.sendVerification(user, "ar");

        verify(emailSender).sendVerificationEmail(eq("student@test.com"), any(), eq("ar"));
    }
}