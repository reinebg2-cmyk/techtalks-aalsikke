package com.aalsikke.service;

import com.aalsikke.dto.ChangePasswordRequest;
import com.aalsikke.dto.UpdateProfileRequest;
import com.aalsikke.exception.InvalidCurrentPasswordException;
import com.aalsikke.mail.EmailSender;
import com.aalsikke.model.User;
import com.aalsikke.repository.UserRepository;
import com.aalsikke.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** KAN-337: security emails on password and email change. */
class AuthServiceSecurityAlertTest {

    private UserRepository users;
    private PasswordEncoder encoder;
    private EmailSender emailSender;
    private AuthService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        encoder = mock(PasswordEncoder.class);
        emailSender = mock(EmailSender.class);
        service = new AuthService(users, encoder, mock(JwtService.class), mock(AuthenticationManager.class),
                mock(EmailVerificationService.class), emailSender);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("ahmad@gmail.com");
        user.setFullName("Ahmad");
        user.setPasswordHash("hash");
        when(users.findByEmail("ahmad@gmail.com")).thenReturn(Optional.of(user));
        when(encoder.matches("right", "hash")).thenReturn(true);
        when(encoder.encode(any())).thenReturn("new-hash");
    }

    private static ChangePasswordRequest passwordChange(String current) {
        ChangePasswordRequest r = new ChangePasswordRequest();
        r.setCurrentPassword(current);
        r.setNewPassword("brand-new-password");
        return r;
    }

    private static UpdateProfileRequest profile(String email, String currentPassword) {
        UpdateProfileRequest r = new UpdateProfileRequest();
        r.setFullName("Ahmad");
        r.setEmail(email);
        r.setProfileMode("STUDENT");
        r.setCurrentPassword(currentPassword);
        return r;
    }

    @Test
    void passwordChanged_alertsTheAccountEmail() {
        service.changePassword("ahmad@gmail.com", passwordChange("right"));
        verify(emailSender).sendSecurityAlert(eq("ahmad@gmail.com"), eq("Ahmad"),
                eq("Your password was changed"), anyString());
    }

    @Test
    void wrongCurrentPassword_noAlert() {
        assertThrows(InvalidCurrentPasswordException.class,
                () -> service.changePassword("ahmad@gmail.com", passwordChange("wrong")));
        verifyNoInteractions(emailSender);
    }

    @Test
    void emailChanged_alertsTheOldAddress_withTheNewOneMasked() {
        service.updateProfile("ahmad@gmail.com", profile("new.mail@outlook.com", "right"));
        verify(emailSender).sendSecurityAlert(eq("ahmad@gmail.com"), eq("Ahmad"),
                eq("Your account email was changed"), contains("n***l@outlook.com"));
    }

    @Test
    void emailTaken_noAlert() {
        when(users.existsByEmail("taken@outlook.com")).thenReturn(true);
        assertThrows(RuntimeException.class,
                () -> service.updateProfile("ahmad@gmail.com", profile("taken@outlook.com", "right")));
        verifyNoInteractions(emailSender);
    }

    @Test
    void sameEmail_noAlert() {
        service.updateProfile("ahmad@gmail.com", profile("ahmad@gmail.com", null));
        verify(emailSender, never()).sendSecurityAlert(any(), any(), any(), any());
    }

    @Test
    void maskEmail_keepsFirstAndLastLetter() {
        assertEquals("a***d@gmail.com", AuthService.maskEmail("ahmad@gmail.com"));
        assertEquals("j***@x.com", AuthService.maskEmail("jo@x.com"));
        assertEquals("***", AuthService.maskEmail("not-an-email"));
    }
}