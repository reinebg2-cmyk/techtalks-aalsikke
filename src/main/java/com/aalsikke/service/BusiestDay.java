package com.aalsikke.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * KAN-341: the busiest day of a date range, for the weekly review card.
 * Pure Java (no Spring, no database) so it can be unit-tested directly, like RecurrenceExpander.
 *
 * Rules:
 *  - Each time is one task or time block, placed on its local date in the given zone.
 *  - Only dates from start to end (both inclusive) count.
 *  - The day with the most wins; on a tie, the earliest day.
 *  - Nothing in the range gives null (the card then shows nothing extra).
 */
public final class BusiestDay {

    public record Result(LocalDate date, int count) {}

    private BusiestDay() {}

    public static Result of(Collection<OffsetDateTime> times, ZoneId zone, LocalDate start, LocalDate end) {
        Map<LocalDate, Integer> perDay = new TreeMap<>();   // sorted, so the earliest day comes first
        for (OffsetDateTime time : times) {
            if (time == null) continue;
            LocalDate date = time.atZoneSameInstant(zone).toLocalDate();
            if (date.isBefore(start) || date.isAfter(end)) continue;
            perDay.merge(date, 1, Integer::sum);
        }

        Result best = null;
        for (Map.Entry<LocalDate, Integer> day : perDay.entrySet()) {
            if (best == null || day.getValue() > best.count()) {   // strictly more, so ties keep the earlier day
                best = new Result(day.getKey(), day.getValue());
            }
        }
        return best;
    }
}