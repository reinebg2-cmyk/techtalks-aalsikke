package com.aalsikke.service;

import com.aalsikke.mail.EmailSender;
import com.aalsikke.model.Reminder;
import com.aalsikke.model.ScheduleItem;
import com.aalsikke.model.User;
import com.aalsikke.repository.ReminderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** KAN-336: ReminderEmailJob sends each due reminder once and keeps going when one email fails. */
class ReminderEmailJobTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 10, 10, 10, 0, 0, 0, ZoneOffset.UTC);

    private ReminderRepository reminders;
    private EmailSender emailSender;
    private ReminderEmailJob job;

    @BeforeEach
    void setUp() {
        reminders = mock(ReminderRepository.class);
        emailSender = mock(EmailSender.class);
        job = new ReminderEmailJob(reminders, emailSender,
                mock(com.aalsikke.repository.RecurrenceRuleRepository.class),
                mock(com.aalsikke.repository.OccurrenceCompletionRepository.class));
    }

    private static Reminder reminderFor(String email, String title, OffsetDateTime deadline, OffsetDateTime endTime) {
        User user = new User();
        user.setEmail(email);
        user.setFullName("Test " + email);

        ScheduleItem item = new ScheduleItem();
        item.setId(UUID.randomUUID());
        item.setUser(user);
        item.setTitle(title);
        item.setDeadline(deadline);
        item.setEndTime(endTime);

        Reminder reminder = new Reminder();
        reminder.setId(UUID.randomUUID());
        reminder.setScheduleItem(item);
        reminder.setRemindAt(NOW.minusMinutes(1));
        return reminder;
    }

    @Test
    void nothingDue_sendsNothing() {
        when(reminders.findDueForEmail(NOW)).thenReturn(List.of());

        assertEquals(0, job.sendDueReminderEmails(NOW));
        verifyNoInteractions(emailSender);
    }

    @Test
    void sendsAndMarksEachDueReminder() {
        Reminder r = reminderFor("a@test.com", "Essay", NOW.plusDays(1), null);
        when(reminders.findDueForEmail(NOW)).thenReturn(List.of(r));

        assertEquals(1, job.sendDueReminderEmails(NOW));
        verify(emailSender).sendReminderEmail("a@test.com", "Test a@test.com", "Essay", NOW.plusDays(1));
        verify(reminders).markEmailed(eq(r.getId()), any(OffsetDateTime.class));
    }

    @Test
    void oneFailedEmail_doesNotStopTheOthers_andIsNotMarked() {
        Reminder failing = reminderFor("down@test.com", "Lab report", NOW.plusDays(1), null);
        Reminder working = reminderFor("ok@test.com", "Shift", NOW.plusDays(2), null);
        when(reminders.findDueForEmail(NOW)).thenReturn(List.of(failing, working));
        doThrow(new MailSendException("SMTP down"))
                .when(emailSender).sendReminderEmail(eq("down@test.com"), any(), any(), any());

        assertEquals(1, job.sendDueReminderEmails(NOW));
        verify(emailSender).sendReminderEmail(eq("ok@test.com"), any(), any(), any());
        verify(reminders).markEmailed(eq(working.getId()), any(OffsetDateTime.class));
        verify(reminders, never()).markEmailed(eq(failing.getId()), any(OffsetDateTime.class));   // retried next run
    }

    @Test
    void noDeadline_usesEndTimeAsDueTime() {
        Reminder r = reminderFor("a@test.com", "Work shift", null, NOW.plusHours(8));
        when(reminders.findDueForEmail(NOW)).thenReturn(List.of(r));

        job.sendDueReminderEmails(NOW);
        verify(emailSender).sendReminderEmail("a@test.com", "Test a@test.com", "Work shift", NOW.plusHours(8));
    }
}