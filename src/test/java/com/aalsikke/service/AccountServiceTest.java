package com.aalsikke.service;

import com.aalsikke.exception.InvalidCurrentPasswordException;
import com.aalsikke.mail.EmailSender;
import com.aalsikke.model.User;
import com.aalsikke.repository.CourseRepository;
import com.aalsikke.repository.DailyReviewRepository;
import com.aalsikke.repository.NotificationPreferenceRepository;
import com.aalsikke.repository.NotificationRepository;
import com.aalsikke.repository.RecurrenceRuleRepository;
import com.aalsikke.repository.ScheduleItemRepository;
import com.aalsikke.repository.UserRepository;
import com.aalsikke.repository.WorkplaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** KAN-338: delete account (password + "DELETE") and the data export. */
class AccountServiceTest {

    private UserRepository users;
    private PasswordEncoder encoder;
    private EmailSender emailSender;
    private AccountService service;
    private User user;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        encoder = mock(PasswordEncoder.class);
        emailSender = mock(EmailSender.class);
        service = new AccountService(users, mock(ScheduleItemRepository.class), mock(RecurrenceRuleRepository.class),
                mock(CourseRepository.class), mock(WorkplaceRepository.class), mock(DailyReviewRepository.class),
                mock(NotificationRepository.class), mock(NotificationPreferenceRepository.class),
                encoder, emailSender);

        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("ahmad@gmail.com");
        user.setFullName("Ahmad");
        user.setPasswordHash("hash");
        when(users.findByEmail("ahmad@gmail.com")).thenReturn(Optional.of(user));
        when(encoder.matches("right", "hash")).thenReturn(true);
    }

    @Test
    void rightPasswordAndWordDeletesTheAccountAndSendsAnEmail() {
        service.deleteAccount("ahmad@gmail.com", "right", "DELETE");

        verify(users).delete(user);
        verify(emailSender).sendSecurityAlert(eq("ahmad@gmail.com"), eq("Ahmad"),
                eq("Your Aal Sikke account was deleted"), anyString());
    }

    @Test
    void wrongPasswordDeletesNothing() {
        assertThrows(InvalidCurrentPasswordException.class,
                () -> service.deleteAccount("ahmad@gmail.com", "wrong", "DELETE"));

        verify(users, never()).delete(any(User.class));
        verify(emailSender, never()).sendSecurityAlert(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void blankPasswordDeletesNothing() {
        assertThrows(InvalidCurrentPasswordException.class,
                () -> service.deleteAccount("ahmad@gmail.com", " ", "DELETE"));

        verify(users, never()).delete(any(User.class));
    }

    @Test
    void missingOrWrongWordDeletesNothing() {
        assertThrows(IllegalArgumentException.class, () -> service.deleteAccount("ahmad@gmail.com", "right", null));
        assertThrows(IllegalArgumentException.class, () -> service.deleteAccount("ahmad@gmail.com", "right", "delete"));
        assertThrows(IllegalArgumentException.class, () -> service.deleteAccount("ahmad@gmail.com", "right", "yes"));

        verify(users, never()).delete(any(User.class));
        verify(emailSender, never()).sendSecurityAlert(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void confirmationWord() {
        assertTrue(AccountService.isConfirmed("DELETE"));
        assertTrue(AccountService.isConfirmed("  DELETE "));
        assertFalse(AccountService.isConfirmed("delete"));
        assertFalse(AccountService.isConfirmed(""));
        assertFalse(AccountService.isConfirmed(null));
    }

    @Test
    void exportFileName() {
        assertEquals("aal-sikke-data-2026-10-10.json", AccountService.exportFileName(LocalDate.of(2026, 10, 10)));
    }

    @Test
    void exportedProfileNeverContainsThePassword() {
        Map<String, Object> profile = AccountService.profileOf(user);

        assertEquals("ahmad@gmail.com", profile.get("email"));
        assertEquals("Ahmad", profile.get("fullName"));
        assertFalse(profile.containsKey("passwordHash"));
        assertFalse(profile.containsValue("hash"));
    }

    @Test
    void exportHasEverySectionEvenForANewUser() {
        Map<String, Object> data = service.exportData("ahmad@gmail.com");

        for (String section : new String[] {"format", "exportedAt", "profile", "notificationPreferences",
                "courses", "workplaces", "tasks", "dailyReviews", "notifications"}) {
            assertTrue(data.containsKey(section), "missing section: " + section);
        }
        assertFalse(data.toString().contains("hash"));
    }
}