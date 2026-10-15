package com.aalsikke.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * KAN-341: the busiest day of a date range, for the weekly review card.
 * Pure Java (no Spring, no database) so it can be unit-tested directly, like RecurrenceExpander.
 *
 * Rules:
 *  - Each item counts once on every local date it covers, in the given zone (KAN-356):
 *    a time block from its start date to its end date, a single time (deadline-only task) on its own date.
 *    An item that ends exactly at midnight does not count on the next day.
 *  - Only dates from start to end (both inclusive) count.
 *  - The day with the most wins; on a tie, the earliest day.
 *  - Nothing in the range gives null (the card then shows nothing extra).
 */
public final class BusiestDay {

    public record Result(LocalDate date, int count) {}

    /** KAN-356: one item from its first moment to its last. last == null means a single moment. */
    public record Span(OffsetDateTime first, OffsetDateTime last) {}

    private BusiestDay() {}

    /** Single moments only: each time counts on its own local date. */
    public static Result of(Collection<OffsetDateTime> times, ZoneId zone, LocalDate start, LocalDate end) {
        List<Span> spans = new ArrayList<>();
        for (OffsetDateTime time : times) {
            spans.add(new Span(time, null));
        }
        return ofSpans(spans, zone, start, end);
    }

    /** KAN-356: each item counts on every day it covers, so a multi-day task that started before the week still counts. */
    public static Result ofSpans(Collection<Span> spans, ZoneId zone, LocalDate start, LocalDate end) {
        Map<LocalDate, Integer> perDay = new TreeMap<>();   // sorted, so the earliest day comes first
        for (Span span : spans) {
            if (span == null || span.first() == null) continue;
            LocalDate firstDay = span.first().atZoneSameInstant(zone).toLocalDate();
            LocalDate from = firstDay.isBefore(start) ? start : firstDay;
            LocalDate lastDay = lastDay(span, zone, firstDay);
            LocalDate to = lastDay.isAfter(end) ? end : lastDay;
            for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {   // nothing when it is outside the range
                perDay.merge(day, 1, Integer::sum);
            }
        }

        Result best = null;
        for (Map.Entry<LocalDate, Integer> day : perDay.entrySet()) {
            if (best == null || day.getValue() > best.count()) {   // strictly more, so ties keep the earlier day
                best = new Result(day.getKey(), day.getValue());
            }
        }
        return best;
    }

    /** The local date of the span's last moment; an end exactly at midnight belongs to the day before. */
    private static LocalDate lastDay(Span span, ZoneId zone, LocalDate firstDay) {
        if (span.last() == null || !span.last().isAfter(span.first())) return firstDay;
        ZonedDateTime last = span.last().atZoneSameInstant(zone);
        LocalDate day = last.toLocalDate();
        if (last.toLocalTime().equals(LocalTime.MIDNIGHT) && day.isAfter(firstDay)) {
            day = day.minusDays(1);
        }
        return day;
    }
}