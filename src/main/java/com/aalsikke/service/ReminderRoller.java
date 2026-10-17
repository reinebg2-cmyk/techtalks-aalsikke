package com.aalsikke.service;

import com.aalsikke.service.RecurrenceExpander.Occurrence;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * KAN-370: moves a reminder on a weekly item to the next repeat once its own repeat is over.
 * Pure Java (no Spring, no database), like RecurrenceExpander, so it can be unit-tested directly.
 *
 * Rules:
 *  - A reminder belongs to the repeat (the original item or a weekly repeat) whose start is nearest
 *    to it; on a tie, the later one. It keeps the same offset from that start ("30 min before",
 *    "halfway through", ...), so it follows the repeat's local time across daylight-saving changes.
 *  - It only moves once that repeat is over (now >= its end), so the bell can still show it meanwhile.
 *  - It jumps straight to the first repeat that is not over yet (e.g. after the server was down).
 *  - Empty = stay where it is: the repeat isn't over yet, or the series has ended ("Until").
 */
final class ReminderRoller {

    /** Safety cap: at most this many repeats are skipped in one call (10 years of weekly repeats). */
    static final int MAX_STEPS = 520;

    private ReminderRoller() {}

    static Optional<OffsetDateTime> nextRemindAt(OffsetDateTime remindAt,
                                                 OffsetDateTime baseStart, OffsetDateTime baseEnd,
                                                 int intervalWeeks, Set<DayOfWeek> days, LocalDate untilDate,
                                                 ZoneId zone, OffsetDateTime now) {
        if (remindAt == null || baseStart == null || baseEnd == null) {
            return Optional.empty();
        }
        int interval = Math.max(1, intervalWeeks);

        Occurrence anchor = anchorOf(remindAt, baseStart, baseEnd, interval, days, untilDate, zone);
        if (anchor == null) {
            return Optional.empty();
        }
        Duration offset = Duration.between(anchor.start(), remindAt);

        Occurrence current = anchor;
        int steps = 0;
        while (!now.isBefore(current.end())) {          // this repeat is over: look at the next one
            current = nextAfter(current, baseStart, baseEnd, interval, days, untilDate, zone);
            if (current == null || ++steps > MAX_STEPS) {
                return Optional.empty();                   // the series has ended
            }
        }
        if (current == anchor) {
            return Optional.empty();                       // its own repeat isn't over yet
        }
        return Optional.of(current.start().plus(offset));
    }

    /**
     * The repeat a reminder belongs to: the one whose start is nearest to the reminder, the later one on a tie.
     * Package-private: the reminder job uses it to check whether that week was already marked done.
     */
    static Occurrence anchorOf(OffsetDateTime remindAt, OffsetDateTime baseStart, OffsetDateTime baseEnd,
                               int intervalWeeks, Set<DayOfWeek> days, LocalDate untilDate, ZoneId zone) {
        int interval = Math.max(1, intervalWeeks);
        Duration span = Duration.ofDays(7L * interval + 1);
        OffsetDateTime from = remindAt.minus(span);
        OffsetDateTime to = remindAt.plus(span);

        List<Occurrence> around = new ArrayList<>();
        if (baseEnd.isAfter(from) && baseStart.isBefore(to)) {
            around.add(new Occurrence(baseStart, baseEnd));   // the original item itself
        }
        around.addAll(RecurrenceExpander.weekly(baseStart, baseEnd, interval, days, untilDate, zone, from, to));

        return around.stream()
                .min(Comparator
                        .comparing((Occurrence o) -> Duration.between(o.start(), remindAt).abs())
                        .thenComparing(Occurrence::start, Comparator.reverseOrder()))   // tie: the later one
                .orElse(null);
    }

    /** The first repeat that starts after the given one, or null when the series has ended. */
    private static Occurrence nextAfter(Occurrence current, OffsetDateTime baseStart, OffsetDateTime baseEnd,
                                        int interval, Set<DayOfWeek> days, LocalDate untilDate, ZoneId zone) {
        OffsetDateTime to = current.start().plusDays(7L * interval + 7);
        return RecurrenceExpander.weekly(baseStart, baseEnd, interval, days, untilDate, zone, current.start(), to)
                .stream()
                .filter(o -> o.start().isAfter(current.start()))
                .findFirst()
                .orElse(null);
    }
}