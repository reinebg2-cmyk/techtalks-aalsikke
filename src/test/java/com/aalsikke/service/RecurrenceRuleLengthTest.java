package com.aalsikke.service;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** KAN-373 / KAN-387: a weekly repeating task can last at most 8 hours (one work shift). Pure, no Spring. */
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
    void nineToFiveWorkShift_canRepeat() {
        assertTrue(RecurrenceRuleService.shortEnoughToRepeat(
                at("2026-10-19T09:00:00+03:00"), at("2026-10-19T17:00:00+03:00")));
    }

    @Test
    void eightHourNightShiftAcrossMidnight_canRepeat() {
        assertTrue(RecurrenceRuleService.shortEnoughToRepeat(
                at("2026-10-19T22:00:00+03:00"), at("2026-10-20T06:00:00+03:00")));
    }

    @Test
    void oneMinuteOver8Hours_cannotRepeat() {
        assertFalse(RecurrenceRuleService.shortEnoughToRepeat(
                at("2026-10-19T09:00:00+03:00"), at("2026-10-19T17:01:00+03:00")));
    }

    @Test
    void twentyThreeHourTask_cannotRepeat() {
        // KAN-387: the tester's case, a 23-hour task was accepted as a weekly repeat
        assertFalse(RecurrenceRuleService.shortEnoughToRepeat(
                at("2026-10-19T10:00:00+03:00"), at("2026-10-20T09:00:00+03:00")));
    }

    @Test
    void exactly24Hours_cannotRepeat() {
        assertFalse(RecurrenceRuleService.shortEnoughToRepeat(
                at("2026-10-19T10:00:00+03:00"), at("2026-10-20T10:00:00+03:00")));
    }

    @Test
    void fourWeekTask_cannotRepeat() {
        // The "ash test" bug: Mon 12 Oct 14:08 -> Mon 9 Nov 14:09, weekly copies piled on top of each other
        assertFalse(RecurrenceRuleService.shortEnoughToRepeat(
                at("2026-10-12T14:08:00+03:00"), at("2026-11-09T14:09:00+02:00")));
    }
}