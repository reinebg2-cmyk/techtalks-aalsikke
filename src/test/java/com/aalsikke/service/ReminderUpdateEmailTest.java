package com.aalsikke.service;

import com.aalsikke.dto.UpdateReminderRequest;
import com.aalsikke.model.Reminder;
import com.aalsikke.model.ScheduleItem;
import com.aalsikke.repository.ReminderRepository;
import com.aalsikke.repository.ScheduleItemRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** KAN-336: moving a reminder to a new time must make it eligible for email again. */
class ReminderUpdateEmailTest {

    private static final OffsetDateTime BASE =
            OffsetDateTime.of(2026, 10, 10, 10, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void changingTheTime_clearsEmailedAt_soTheNewTimeIsEmailed() {
        ReminderRepository reminders = mock(ReminderRepository.class);
        ScheduleItemRepository items = mock(ScheduleItemRepository.class);
        ReminderService service = new ReminderService(reminders, items,
                mock(com.aalsikke.repository.UserRepository.class));

        ScheduleItem item = new ScheduleItem();
        item.setId(UUID.randomUUID());
        item.setStartTime(BASE);
        item.setDeadline(BASE.plusDays(1));

        Reminder reminder = new Reminder();
        reminder.setId(UUID.randomUUID());
        reminder.setScheduleItem(item);
        reminder.setRemindAt(BASE.plusHours(1));
        reminder.setTriggered(true);
        reminder.setEmailedAt(BASE.plusHours(1));   // already emailed for the old time

        when(reminders.findByIdAndOwner(reminder.getId(), "user@test.com")).thenReturn(Optional.of(reminder));
        when(reminders.findByScheduleItemId(item.getId())).thenReturn(List.of(reminder));
        when(reminders.save(any(Reminder.class))).thenAnswer(call -> call.getArgument(0));

        service.updateReminder(reminder.getId(), new UpdateReminderRequest(BASE.plusHours(5)), "user@test.com");

        assertFalse(reminder.isTriggered());
        assertNull(reminder.getEmailedAt(), "a reminder moved to a new time must be emailed again");
    }
}