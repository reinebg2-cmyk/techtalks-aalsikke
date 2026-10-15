package com.aalsikke.service;

import com.aalsikke.model.Reminder;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReminderServiceTest {

    private static final OffsetDateTime BASE =
            OffsetDateTime.of(2026, 10, 10, 10, 0, 0, 0, ZoneOffset.UTC);

    private static Reminder reminderAt(OffsetDateTime time) {
        Reminder reminder = new Reminder();
        reminder.setRemindAt(time);
        return reminder;
    }

    @Test
    void noExistingReminders_isAllowed() {
        assertDoesNotThrow(() -> ReminderService.validateMinGap(BASE, List.of()));
    }

    @Test
    void exactlyTenMinutesAfter_isAllowed() {
        assertDoesNotThrow(() ->
                ReminderService.validateMinGap(BASE.plusMinutes(10), List.of(reminderAt(BASE))));
    }

    @Test
    void exactlyTenMinutesBefore_isAllowed() {
        assertDoesNotThrow(() ->
                ReminderService.validateMinGap(BASE.minusMinutes(10), List.of(reminderAt(BASE))));
    }

    @Test
    void oneSecondUnderTenMinutesAfter_isRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                ReminderService.validateMinGap(BASE.plusMinutes(10).minusSeconds(1), List.of(reminderAt(BASE))));
        assertEquals("Reminders on the same task must be at least 10 minutes apart.", error.getMessage());
    }

    @Test
    void oneSecondUnderTenMinutesBefore_isRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                ReminderService.validateMinGap(BASE.minusMinutes(10).plusSeconds(1), List.of(reminderAt(BASE))));
    }

    @Test
    void oneSecondOverTenMinutes_isAllowed() {
        assertDoesNotThrow(() ->
                ReminderService.validateMinGap(BASE.plusMinutes(10).plusSeconds(1), List.of(reminderAt(BASE))));
    }

    @Test
    void sameInstant_isRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                ReminderService.validateMinGap(BASE, List.of(reminderAt(BASE))));
    }

    @Test
    void sameInstantInDifferentTimeZone_isRejected() {
        OffsetDateTime sameMomentInBeirut = BASE.withOffsetSameInstant(ZoneOffset.ofHours(3));
        assertThrows(IllegalArgumentException.class, () ->
                ReminderService.validateMinGap(sameMomentInBeirut, List.of(reminderAt(BASE))));
    }

    @Test
    void clashWithAnyOfSeveralExisting_isRejected() {
        List<Reminder> existing = List.of(reminderAt(BASE), reminderAt(BASE.plusHours(2)));
        assertThrows(IllegalArgumentException.class, () ->
                ReminderService.validateMinGap(BASE.plusHours(2).plusMinutes(5), existing));
    }

    @Test
    void farFromAllExisting_isAllowed() {
        List<Reminder> existing = List.of(reminderAt(BASE), reminderAt(BASE.plusHours(2)));
        assertDoesNotThrow(() ->
                ReminderService.validateMinGap(BASE.plusHours(1), existing));
    }
}