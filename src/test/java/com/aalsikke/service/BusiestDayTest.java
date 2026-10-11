package com.aalsikke.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** KAN-341: busiest day of the week for the weekly review card. */
class BusiestDayTest {

    private static final ZoneId BEIRUT = ZoneId.of("Asia/Beirut");
    private static final LocalDate MON = LocalDate.of(2026, 10, 5);
    private static final LocalDate SUN = LocalDate.of(2026, 10, 11);

    /** 10:00 Beirut (+03:00 in early October) on the given day of October 2026. */
    private static OffsetDateTime oct(int day) {
        return OffsetDateTime.of(2026, 10, day, 7, 0, 0, 0, ZoneOffset.UTC);
    }

    /** The given hour Beirut time (+03:00) on the given day of October 2026. */
    private static OffsetDateTime beirut(int day, int hour) {
        return OffsetDateTime.of(2026, 10, day, hour, 0, 0, 0, ZoneOffset.ofHours(3));
    }

    @Test
    void dayWithMostItemsWins() {
        BusiestDay.Result result = BusiestDay.of(
                List.of(oct(5), oct(7), oct(7), oct(7), oct(9), oct(9)), BEIRUT, MON, SUN);
        assertEquals(LocalDate.of(2026, 10, 7), result.date());
        assertEquals(3, result.count());
    }

    @Test
    void tieGoesToTheEarliestDay() {
        BusiestDay.Result result = BusiestDay.of(
                List.of(oct(9), oct(9), oct(6), oct(6)), BEIRUT, MON, SUN);
        assertEquals(LocalDate.of(2026, 10, 6), result.date());
        assertEquals(2, result.count());
    }

    @Test
    void emptyWeekGivesNull() {
        assertNull(BusiestDay.of(List.of(), BEIRUT, MON, SUN));
    }

    @Test
    void itemsOutsideTheWeekAreIgnored() {
        assertNull(BusiestDay.of(List.of(oct(4), oct(12)), BEIRUT, MON, SUN));
    }

    @Test
    void localDateIsUsedNotUtc() {
        // 22:30 UTC on Tue Oct 6 is 01:30 on Wed Oct 7 in Beirut
        OffsetDateTime lateTuesdayUtc = OffsetDateTime.of(2026, 10, 6, 22, 30, 0, 0, ZoneOffset.UTC);
        BusiestDay.Result result = BusiestDay.of(List.of(lateTuesdayUtc), BEIRUT, MON, SUN);
        assertEquals(LocalDate.of(2026, 10, 7), result.date());
    }

    @Test
    void multiDayTaskThatStartedBeforeTheWeekCountsOnEveryDayItCovers() {
        // KAN-356: like "Crowded: hackathon", Sun Oct 4 16:00 to Tue Oct 6 11:00, plus one task on Mon Oct 5
        BusiestDay.Result result = BusiestDay.ofSpans(List.of(
                new BusiestDay.Span(beirut(4, 16), beirut(6, 11)),
                new BusiestDay.Span(oct(5), null)), BEIRUT, MON, SUN);
        assertEquals(MON, result.date());
        assertEquals(2, result.count());
    }

    @Test
    void taskEndingAtMidnightDoesNotCountOnTheNextDay() {
        // Wed Oct 7 20:00 to Thu Oct 8 00:00 is a Wednesday-only task, so Wed and Thu tie at 1 and Wed wins
        BusiestDay.Result result = BusiestDay.ofSpans(List.of(
                new BusiestDay.Span(beirut(7, 20), beirut(8, 0)),
                new BusiestDay.Span(oct(8), null)), BEIRUT, MON, SUN);
        assertEquals(LocalDate.of(2026, 10, 7), result.date());
        assertEquals(1, result.count());
    }

    @Test
    void taskLongerThanTheWeekOnlyCountsInsideTheWeek() {
        BusiestDay.Result result = BusiestDay.ofSpans(List.of(
                new BusiestDay.Span(beirut(1, 9), beirut(20, 9))), BEIRUT, MON, SUN);
        assertEquals(MON, result.date());
        assertEquals(1, result.count());
    }

    @Test
    void missingTimesAreSkipped() {
        BusiestDay.Result result = BusiestDay.of(Arrays.asList(null, oct(8)), BEIRUT, MON, SUN);
        assertEquals(LocalDate.of(2026, 10, 8), result.date());
        assertEquals(1, result.count());
    }
}