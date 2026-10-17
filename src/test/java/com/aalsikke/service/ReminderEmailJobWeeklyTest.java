package com.aalsikke.service;

import com.aalsikke.mail.EmailSender;
import com.aalsikke.model.RecurrenceRule;
import com.aalsikke.model.Reminder;
import com.aalsikke.model.ScheduleItem;
import com.aalsikke.model.User;
import com.aalsikke.repository.OccurrenceCompletionRepository;
import com.aalsikke.repository.RecurrenceRuleRepository;
import com.aalsikke.repository.ReminderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** KAN-370: reminders on a weekly class fire every week, not only for the first date. */
class ReminderEmailJobWeeklyTest {

    private ReminderRepository reminders;
    private EmailSender emailSender;
    private OccurrenceCompletionRepository completions;
    private ReminderEmailJob job;
    private ScheduleItem item;
    private Reminder reminder;

    @BeforeEach
    void setUp() {
        reminders = mock(ReminderRepository.class);
        emailSender = mock(EmailSender.class);
        RecurrenceRuleRepository rules = mock(RecurrenceRuleRepository.class);
        completions = mock(OccurrenceCompletionRepository.class);
        job = new ReminderEmailJob(reminders, emailSender, rules, completions);

        User user = new User();
        user.setEmail("a@test.com");
        user.setFullName("Rami");
        user.setEmailVerified(true);

        // Weekly class: Fri Oct 16 2026, 10:00-11:00 Beirut (07:00Z-08:00Z), Due = end, every Friday
        item = new ScheduleItem();
        item.setId(UUID.randomUUID());
        item.setUser(user);
        item.setTitle("Java class");
        item.setStartTime(at("2026-10-16T07:00:00Z"));
        item.setEndTime(at("2026-10-16T08:00:00Z"));
        item.setDeadline(at("2026-10-16T08:00:00Z"));
        item.setRecurring(true);
        item.setReminderEnabled(true);

        RecurrenceRule rule = new RecurrenceRule();
        rule.setScheduleItem(item);
        rule.setFrequency("WEEKLY");
        rule.setIntervalValue(1);
        rule.setDaysOfWeek("FRI");
        when(rules.findByScheduleItemId(item.getId())).thenReturn(Optional.of(rule));

        // Reminder 30 min before the first class: 09:30 Beirut = 06:30Z
        reminder = new Reminder();
        reminder.setId(UUID.randomUUID());
        reminder.setScheduleItem(item);
        reminder.setRemindAt(at("2026-10-16T06:30:00Z"));
    }

    @Test
    void afterTheClass_reminderMovesToNextWeek_andCanFireAgain() {
        reminder.setEmailedAt(at("2026-10-16T06:31:00Z"));
        reminder.setTriggered(true);
        when(reminders.findPastOnRecurringItems(any())).thenReturn(List.of(reminder));

        job.rollWeeklyReminders(at("2026-10-16T09:00:00Z"));   // 12:00 Beirut, the class is over

        assertEquals(at("2026-10-23T06:30:00Z").toInstant(), reminder.getRemindAt().toInstant());
        assertNull(reminder.getEmailedAt());
        assertFalse(reminder.isTriggered());
        verify(reminders).save(reminder);
    }

    @Test
    void secondWeek_emailShowsThatWeeksTime() {
        reminder.setRemindAt(at("2026-10-23T06:30:00Z"));
        when(reminders.findDueForEmail(any())).thenReturn(List.of(reminder));

        job.sendDueReminderEmails(at("2026-10-23T06:31:00Z"));

        // Due = the end of THIS week's class (Oct 23, 11:00 Beirut), not the first one's (Oct 16)
        verify(emailSender).sendReminderEmail(eq("a@test.com"), eq("Rami"), eq("Java class"),
                argThat((OffsetDateTime due) -> due.toInstant().equals(at("2026-10-23T08:00:00Z").toInstant())));
    }

    @Test
    void weekMarkedDone_getsNoEmail() {
        reminder.setRemindAt(at("2026-10-23T06:30:00Z"));
        when(reminders.findDueForEmail(any())).thenReturn(List.of(reminder));
        when(completions.existsByScheduleItemIdAndOccurrenceDate(item.getId(), LocalDate.of(2026, 10, 23)))
                .thenReturn(true);

        job.sendDueReminderEmails(at("2026-10-23T06:31:00Z"));

        verify(emailSender, never()).sendReminderEmail(any(), any(), any(), any());
        verify(reminders).markEmailed(eq(reminder.getId()), any());   // handled; it moves on after the class
    }

    @Test
    void firstDateDone_doesNotSilenceLaterWeeks() {
        item.setStatus("COMPLETED");   // only the first date (Oct 16) is done
        reminder.setRemindAt(at("2026-10-23T06:30:00Z"));
        when(reminders.findDueForEmail(any())).thenReturn(List.of(reminder));

        job.sendDueReminderEmails(at("2026-10-23T06:31:00Z"));

        verify(emailSender).sendReminderEmail(eq("a@test.com"), any(), any(), any());
    }

    private static OffsetDateTime at(String iso) {
        return OffsetDateTime.parse(iso);
    }
}