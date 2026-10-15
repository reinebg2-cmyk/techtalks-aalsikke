package com.aalsikke.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * KAN-343: free time slots of a given length on one day, for "Suggest a time".
 * Pure Java (no Spring, no database) so it can be unit-tested directly, like ConflictRules.
 *
 * Busy time follows ConflictRules, so a suggestion never creates a conflict:
 *  - a time block is busy from start to end (back-to-back is fine), plus the optional buffer after it;
 *  - a start-only marker is busy at its start: a slot may not contain it (start <= marker < end);
 *  - deadline-only tasks are not passed in at all (a deadline never reserves time).
 *
 * Slots start on 15-minute marks (local time), inside the day's window, and not before notBefore.
 * After a free slot the next candidate starts where that slot ends, so three suggestions are spread out.
 */
public final class FreeSlots {

    static final int STEP_MINUTES = 15;

    /** A busy period. For a start-only marker, end is null. */
    public record Busy(OffsetDateTime start, OffsetDateTime end) {
        public static Busy block(OffsetDateTime start, OffsetDateTime end) { return new Busy(start, end); }
        public static Busy marker(OffsetDateTime start) { return new Busy(start, null); }
        boolean isMarker() { return end == null; }
    }

    /** A suggestion: the slot itself, and until when the time stays free (capped at the window end). */
    public record Slot(OffsetDateTime start, OffsetDateTime end, OffsetDateTime freeUntil) {}

    private FreeSlots() {}

    public static List<Slot> find(Collection<Busy> busy, LocalDate date, LocalTime windowStart, LocalTime windowEnd,
                                  Duration length, Duration buffer, OffsetDateTime notBefore, ZoneId zone, int limit) {
        OffsetDateTime dayStart = date.atTime(windowStart).atZone(zone).toOffsetDateTime();
        OffsetDateTime dayEnd = date.atTime(windowEnd).atZone(zone).toOffsetDateTime();

        OffsetDateTime candidate = roundUp(later(dayStart, notBefore), zone);
        List<Slot> slots = new ArrayList<>();

        while (slots.size() < limit && !candidate.plus(length).isAfter(dayEnd)) {
            OffsetDateTime end = candidate.plus(length);
            if (isFree(busy, candidate, end, buffer)) {
                slots.add(new Slot(candidate, end,
                        freeUntil(busy, end, dayEnd).atZoneSameInstant(zone).toOffsetDateTime()));
                candidate = roundUp(end, zone);                       // next suggestion after this one
            } else {
                candidate = candidate.plusMinutes(STEP_MINUTES);
            }
        }
        return slots;
    }

    private static boolean isFree(Collection<Busy> busy, OffsetDateTime start, OffsetDateTime end, Duration buffer) {
        for (Busy b : busy) {
            if (b.isMarker()) {
                boolean inside = !b.start().isBefore(start) && b.start().isBefore(end);
                if (inside) return false;
            } else {
                OffsetDateTime busyUntil = b.end().plus(buffer);
                boolean overlaps = start.isBefore(busyUntil) && b.start().isBefore(end);
                if (overlaps) return false;
            }
        }
        return true;
    }

    /** When the next busy thing starts after the slot, or the window end if nothing does. */
    private static OffsetDateTime freeUntil(Collection<Busy> busy, OffsetDateTime slotEnd, OffsetDateTime dayEnd) {
        OffsetDateTime until = dayEnd;
        for (Busy b : busy) {
            if (!b.start().isBefore(slotEnd) && b.start().isBefore(until)) {
                until = b.start();
            }
        }
        return until;
    }

    /** Up to the next 15-minute mark in local time (10:07 -> 10:15, 10:15 stays). */
    static OffsetDateTime roundUp(OffsetDateTime time, ZoneId zone) {
        ZonedDateTime local = time.atZoneSameInstant(zone);
        ZonedDateTime floor = local.truncatedTo(ChronoUnit.MINUTES)
                .withMinute(local.getMinute() - local.getMinute() % STEP_MINUTES);
        ZonedDateTime rounded = floor.isBefore(local) ? floor.plusMinutes(STEP_MINUTES) : floor;
        return rounded.toOffsetDateTime();
    }

    private static OffsetDateTime later(OffsetDateTime a, OffsetDateTime b) {
        return b != null && b.isAfter(a) ? b : a;
    }
}