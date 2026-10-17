package com.aalsikke.service;

import com.aalsikke.dto.LoginRequest;
import com.aalsikke.exception.EmailNotVerifiedException;
import com.aalsikke.mail.EmailSender;
import com.aalsikke.model.User;
import com.aalsikke.repository.UserRepository;
import com.aalsikke.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** KAN-369: welcome email once, after the first successful login. */
class AuthServiceWelcomeEmailTest {

    private static final String EMAIL = "new.user@gmail.com";

    private UserRepository users;
    private EmailSender emailSender;
    private AuthService service;
    private User user;
    private LoginRequest request;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        emailSender = mock(EmailSender.class);
        JwtService jwt = mock(JwtService.class);
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        service = new AuthService(users, mock(PasswordEncoder.class), jwt, authManager,
                mock(EmailVerificationService.class), emailSender);

        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(EMAIL);
        user.setFullName("Rami");
        user.setEmailVerified(true);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(EMAIL);
        when(authManager.authenticate(any())).thenReturn(authentication);
        when(jwt.generateToken(EMAIL)).thenReturn("token");

        request = mock(LoginRequest.class);
        when(request.getEmail()).thenReturn(EMAIL);
        when(request.getPassword()).thenReturn("right");
    }

    @Test
    void firstLogin_sendsTheWelcomeEmail_andRemembersIt() {
        service.login(request);

        verify(emailSender).sendWelcomeEmail(EMAIL, "Rami");
        assertNotNull(user.getWelcomeEmailSentAt());
        verify(users).save(user);
    }

    @Test
    void laterLogin_sendsNoWelcomeEmail() {
        user.setWelcomeEmailSentAt(OffsetDateTime.now().minusDays(1));

        service.login(request);

        verify(emailSender, never()).sendWelcomeEmail(any(), any());
    }

    @Test
    void unverifiedLogin_sendsNoWelcomeEmail() {
        user.setEmailVerified(false);

        assertThrows(EmailNotVerifiedException.class, () -> service.login(request));

        verify(emailSender, never()).sendWelcomeEmail(any(), any());
    }
}