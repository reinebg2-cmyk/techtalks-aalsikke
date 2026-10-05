package com.aalsikke.service;

import com.aalsikke.model.Reminder;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReminderMinGapTest {

    private static final OffsetDateTime BASE = OffsetDateTime.parse("2026-10-20T10:00:00Z");

    private static Reminder at(OffsetDateTime time) {
        Reminder r = new Reminder();
        r.setRemindAt(time);
        return r;
    }

    @Test
    void noExistingReminders_isAllowed() {
        assertDoesNotThrow(() -> ReminderService.validateMinGap(BASE, List.of()));
    }

    @Test
    void exactlyTenMinutesAfter_isAllowed() {
        assertDoesNotThrow(() ->
                ReminderService.validateMinGap(BASE.plusMinutes(10), List.of(at(BASE))));
    }

    @Test
    void exactlyTenMinutesBefore_isAllowed() {
        assertDoesNotThrow(() ->
                ReminderService.validateMinGap(BASE.minusMinutes(10), List.of(at(BASE))));
    }

    @Test
    void nineMinutesAfter_isRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                ReminderService.validateMinGap(BASE.plusMinutes(9), List.of(at(BASE))));
    }

    @Test
    void nineMinutesBefore_isRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                ReminderService.validateMinGap(BASE.minusMinutes(9), List.of(at(BASE))));
    }

    @Test
    void sameTime_isRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                ReminderService.validateMinGap(BASE, List.of(at(BASE))));
    }

    @Test
    void checksAgainstEveryExistingReminder() {
        List<Reminder> existing = List.of(at(BASE), at(BASE.plusMinutes(30)));
        // 10 minutes after the first, but only 5 minutes before the second
        assertThrows(IllegalArgumentException.class, () ->
                ReminderService.validateMinGap(BASE.plusMinutes(25), existing));
    }
}