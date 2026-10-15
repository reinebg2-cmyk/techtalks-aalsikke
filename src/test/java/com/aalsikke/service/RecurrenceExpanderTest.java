package com.aalsikke.service;

import com.aalsikke.service.RecurrenceExpander.Occurrence;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** KAN-248 weekly recurrence expansion. Pure unit tests: no Spring context, no database. */
class RecurrenceExpanderTest {

    private static final ZoneId BEIRUT = ZoneId.of("Asia/Beirut");

    // Base item: Monday Sep 21 2026, 09:00-10:30 Beirut time (06:00Z-07:30Z)
    private static final OffsetDateTime BASE_START = OffsetDateTime.parse("2026-09-21T06:00:00Z");
    private static final OffsetDateTime BASE_END = OffsetDateTime.parse("2026-09-21T07:30:00Z");

    private static final Set<DayOfWeek> MON_WED = RecurrenceExpander.parseDays("MON,WED");

    @Test
    void mondayAndWednesday_everyWeek_excludesTheBaseItself() {
        List<Occurrence> result = RecurrenceExpander.weekly(BASE_START, BASE_END, 1, MON_WED, null, BEIRUT,
                at("2026-09-20T21:00:00Z"), at("2026-10-04T21:00:00Z"));   // Mon Sep 21 - Mon Oct 5 local

        assertEquals(List.of("2026-09-23T09:00", "2026-09-28T09:00", "2026-09-30T09:00"), localStarts(result));
    }

    @Test
    void keepsTheBaseDuration() {
        List<Occurrence> result = RecurrenceExpander.weekly(BASE_START, BASE_END, 1, MON_WED, null, BEIRUT,
                at("2026-09-22T00:00:00Z"), at("2026-09-24T00:00:00Z"));

        Occurrence wednesday = result.get(0);
        assertEquals(90, java.time.Duration.between(wednesday.start(), wednesday.end()).toMinutes());
    }

    @Test
    void everySecondWeek() {
        List<Occurrence> result = RecurrenceExpander.weekly(BASE_START, BASE_END, 2, Set.of(DayOfWeek.MONDAY),
                null, BEIRUT, at("2026-09-20T21:00:00Z"), at("2026-10-31T21:00:00Z"));

        assertEquals(List.of("2026-10-05T09:00", "2026-10-19T09:00"), localStarts(result));
    }

    @Test
    void stopsAfterUntilDate_inclusive() {
        List<Occurrence> result = RecurrenceExpander.weekly(BASE_START, BASE_END, 1, MON_WED,
                LocalDate.of(2026, 9, 30), BEIRUT, at("2026-09-20T21:00:00Z"), at("2026-10-31T21:00:00Z"));

        assertEquals(List.of("2026-09-23T09:00", "2026-09-28T09:00", "2026-09-30T09:00"), localStarts(result));
    }

    @Test
    void noDays_repeatsOnTheBaseWeekday() {
        List<Occurrence> result = RecurrenceExpander.weekly(BASE_START, BASE_END, 1, Set.of(), null, BEIRUT,
                at("2026-09-20T21:00:00Z"), at("2026-10-12T21:00:00Z"));

        assertEquals(List.of("2026-09-28T09:00", "2026-10-05T09:00", "2026-10-12T09:00"), localStarts(result));
    }

    @Test
    void rangeBeforeTheBase_isEmpty() {
        List<Occurrence> result = RecurrenceExpander.weekly(BASE_START, BASE_END, 1, MON_WED, null, BEIRUT,
                at("2026-09-01T00:00:00Z"), at("2026-09-20T00:00:00Z"));

        assertTrue(result.isEmpty());
    }

    @Test
    void occurrenceAlreadyRunningWhenTheRangeStarts_isIncluded() {
        // Range starts Wed 09:30 local, in the middle of the 09:00-10:30 repeat
        List<Occurrence> result = RecurrenceExpander.weekly(BASE_START, BASE_END, 1, MON_WED, null, BEIRUT,
                at("2026-09-23T06:30:00Z"), at("2026-09-23T21:00:00Z"));

        assertEquals(List.of("2026-09-23T09:00"), localStarts(result));
    }

    @Test
    void keepsLocalTimeAcrossDaylightSavingChange() {
        // Lebanon leaves summer time on Oct 25 2026: 09:00 local is 06:00Z before, 07:00Z after
        List<Occurrence> result = RecurrenceExpander.weekly(BASE_START, BASE_END, 1, Set.of(DayOfWeek.MONDAY),
                null, BEIRUT, at("2026-10-25T00:00:00Z"), at("2026-11-03T00:00:00Z"));

        assertEquals(List.of("2026-10-26T09:00", "2026-11-02T09:00"), localStarts(result));
        assertEquals(at("2026-10-26T07:00:00Z").toInstant(), result.get(0).start().toInstant());
    }

    @Test
    void daysBeforeTheBaseInTheFirstWeek_areSkipped() {
        // Base on Wednesday Sep 23; the Monday of that week is before the base and must not appear
        OffsetDateTime wedStart = at("2026-09-23T06:00:00Z");
        OffsetDateTime wedEnd = at("2026-09-23T07:00:00Z");
        List<Occurrence> result = RecurrenceExpander.weekly(wedStart, wedEnd, 1, MON_WED, null, BEIRUT,
                at("2026-09-20T21:00:00Z"), at("2026-10-04T21:00:00Z"));

        assertEquals(List.of("2026-09-28T09:00", "2026-09-30T09:00"), localStarts(result));
    }

    @Test
    void farFutureRange_stillWorks() {
        List<Occurrence> result = RecurrenceExpander.weekly(BASE_START, BASE_END, 1, MON_WED, null, BEIRUT,
                at("2027-06-06T21:00:00Z"), at("2027-06-13T21:00:00Z"));

        assertEquals(List.of("2027-06-07T09:00", "2027-06-09T09:00"), localStarts(result));
    }

    @Test
    void parseDays_rejectsUnknownCodes() {
        assertEquals(Set.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), RecurrenceExpander.parseDays("MON,FRI"));
        assertTrue(RecurrenceExpander.parseDays(null).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> RecurrenceExpander.parseDays("MON,XYZ"));
    }

    // ---- Helpers ----

    private static OffsetDateTime at(String iso) {
        return OffsetDateTime.parse(iso);
    }

    private static List<String> localStarts(List<Occurrence> occurrences) {
        return occurrences.stream()
                .map(o -> o.start().atZoneSameInstant(BEIRUT).toLocalDateTime())
                .map(LocalDateTime::toString)
                .toList();
    }
}