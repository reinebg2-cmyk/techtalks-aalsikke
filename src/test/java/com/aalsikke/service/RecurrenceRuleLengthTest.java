package com.aalsikke.service;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** KAN-373: a weekly repeat must last at most 24 hours, or its copies overlap. Pure, no Spring. */
class RecurrenceRuleLengthTest {

    private static OffsetDateTime at(String iso) {
        return OffsetDateTime.parse(iso);
    }

    @Test
    void oneHourClass_canRepeat() {
        assertTrue(RecurrenceRuleService.shortEnoughToRepeat(
                at("2026-10-19T10:00:00+03:00"), at("2026-10-19T11:00:00+03:00")));
    }

    @Test
    void nightShiftAcrossMidnight_canRepeat() {
        assertTrue(RecurrenceRuleService.shortEnoughToRepeat(
                at("2026-10-19T22:00:00+03:00"), at("2026-10-20T06:00:00+03:00")));
    }

    @Test
    void exactly24Hours_canRepeat() {
        assertTrue(RecurrenceRuleService.shortEnoughToRepeat(
                at("2026-10-19T10:00:00+03:00"), at("2026-10-20T10:00:00+03:00")));
    }

    @Test
    void oneMinuteOver24Hours_cannotRepeat() {
        assertFalse(RecurrenceRuleService.shortEnoughToRepeat(
                at("2026-10-19T10:00:00+03:00"), at("2026-10-20T10:01:00+03:00")));
    }

    @Test
    void fourWeekTask_cannotRepeat() {
        // The "ash test" bug: Mon 12 Oct 14:08 -> Mon 9 Nov 14:09, weekly copies piled on top of each other
        assertFalse(RecurrenceRuleService.shortEnoughToRepeat(
                at("2026-10-12T14:08:00+03:00"), at("2026-11-09T14:09:00+02:00")));
    }
}