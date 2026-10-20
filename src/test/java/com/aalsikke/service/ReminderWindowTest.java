package com.aalsikke.service;

import com.aalsikke.model.ScheduleItem;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Reminders may come before a task starts (that's what they're for), but not after its due time. */
class ReminderWindowTest {

    // Class: Wed 21 Oct 2026, 10:00-11:30 Beirut (UTC+3)
    private static ScheduleItem classAt10() {
        ScheduleItem item = new ScheduleItem();
        item.setStartTime(OffsetDateTime.parse("2026-10-21T10:00:00+03:00"));
        item.setEndTime(OffsetDateTime.parse("2026-10-21T11:30:00+03:00"));
        return item;
    }

    @Test
    void sameMorningBeforeStart_isAllowed() {
        assertDoesNotThrow(() -> ReminderService.validateWithinTimeline(
                classAt10(), OffsetDateTime.parse("2026-10-21T07:00:00+03:00")));
    }

    @Test
    void dayBefore_isAllowed() {
        assertDoesNotThrow(() -> ReminderService.validateWithinTimeline(
                classAt10(), OffsetDateTime.parse("2026-10-20T20:00:00+03:00")));
    }

    @Test
    void duringTheClass_isAllowed() {
        assertDoesNotThrow(() -> ReminderService.validateWithinTimeline(
                classAt10(), OffsetDateTime.parse("2026-10-21T10:45:00+03:00")));
    }

    @Test
    void afterTheEnd_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> ReminderService.validateWithinTimeline(
                classAt10(), OffsetDateTime.parse("2026-10-21T12:00:00+03:00")));
    }
}