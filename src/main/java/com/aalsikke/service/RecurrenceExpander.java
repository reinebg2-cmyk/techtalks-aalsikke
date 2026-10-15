package com.aalsikke.service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * KAN-248: expands a weekly recurrence rule into concrete occurrences.
 * Pure Java (no Spring, no database) so it can be unit-tested directly.
 *
 * Rules:
 *  - Occurrences keep the base item's local start time and duration, in the given zone
 *    (so 09:00 stays 09:00 across a daylight-saving change).
 *  - Repeats happen every {@code intervalWeeks} weeks (Monday-based weeks), on {@code days};
 *    no days means "the base item's own weekday".
 *  - Nothing before the base item's date, and nothing after {@code untilDate} (inclusive).
 *  - The base item itself is NOT returned: it is already a real item.
 *  - Only occurrences overlapping [from, to) are returned, in time order.
 */
public final class RecurrenceExpander {

    /** Safety cap: at most this many weeks are walked from the base date. */
    static final int MAX_WEEKS = 2000;

    public record Occurrence(OffsetDateTime start, OffsetDateTime end) {}

    private RecurrenceExpander() {}

    public static List<Occurrence> weekly(OffsetDateTime baseStart, OffsetDateTime baseEnd,
                                          int intervalWeeks, Collection<DayOfWeek> days,
                                          LocalDate untilDate, ZoneId zone,
                                          OffsetDateTime from, OffsetDateTime to) {
        if (intervalWeeks < 1) throw new IllegalArgumentException("intervalWeeks must be at least 1");

        Duration duration = Duration.between(baseStart, baseEnd);
        ZonedDateTime base = baseStart.atZoneSameInstant(zone);
        LocalDate baseDate = base.toLocalDate();
        LocalTime startTime = base.toLocalTime();

        Set<DayOfWeek> repeatDays = (days == null || days.isEmpty())
                ? EnumSet.of(baseDate.getDayOfWeek())
                : EnumSet.copyOf(days);   // EnumSet iterates MONDAY..SUNDAY, i.e. in time order

        LocalDate firstMonday = baseDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<Occurrence> result = new ArrayList<>();

        for (int week = 0; week < MAX_WEEKS; week++) {
            LocalDate monday = firstMonday.plusWeeks((long) week * intervalWeeks);
            if (untilDate != null && monday.isAfter(untilDate)) break;

            for (DayOfWeek day : repeatDays) {
                LocalDate date = monday.plusDays(day.getValue() - 1L);
                if (!date.isAfter(baseDate)) continue;                       // the base itself, or earlier
                if (untilDate != null && date.isAfter(untilDate)) return result;

                OffsetDateTime start = ZonedDateTime.of(date, startTime, zone).toOffsetDateTime();
                if (!start.isBefore(to)) return result;                      // later ones are later still
                OffsetDateTime end = start.plus(duration);
                if (end.isAfter(from)) {
                    result.add(new Occurrence(start, end));
                }
            }
        }
        return result;
    }

    /** "MON,WED" -> {MONDAY, WEDNESDAY}; null/blank -> empty set. */
    public static Set<DayOfWeek> parseDays(String daysOfWeek) {
        Set<DayOfWeek> result = EnumSet.noneOf(DayOfWeek.class);
        if (daysOfWeek == null || daysOfWeek.isBlank()) return result;
        Arrays.stream(daysOfWeek.split(","))
                .map(String::trim)
                .forEach(code -> result.add(switch (code) {
                    case "MON" -> DayOfWeek.MONDAY;
                    case "TUE" -> DayOfWeek.TUESDAY;
                    case "WED" -> DayOfWeek.WEDNESDAY;
                    case "THU" -> DayOfWeek.THURSDAY;
                    case "FRI" -> DayOfWeek.FRIDAY;
                    case "SAT" -> DayOfWeek.SATURDAY;
                    case "SUN" -> DayOfWeek.SUNDAY;
                    default -> throw new IllegalArgumentException("Unknown day: " + code);
                }));
        return result;
    }
}