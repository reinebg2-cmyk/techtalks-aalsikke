package com.aalsikke.service;

import com.aalsikke.model.ScheduleItem;
import com.aalsikke.model.TimeType;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Conflict rules, kept free of Spring and the database so they can be unit-tested directly.
 *
 * Two items conflict only when they are different items, belong to the same user,
 * and neither is DEADLINE_ONLY (a deadline alone never reserves time). Then, by type:
 *  - TIME_BLOCKED vs TIME_BLOCKED: slots overlap (start1 < end2 AND start2 < end1).
 *    Back-to-back items (one ends exactly when the other starts) do NOT conflict.
 *  - START_ONLY vs TIME_BLOCKED: the marker's start falls inside [start, end).
 *  - START_ONLY vs START_ONLY: exactly the same start instant.
 */
public final class ConflictRules {

    public enum Kind { OVERLAP, START_IN_BLOCK, SAME_START }

    private ConflictRules() {}
    /** True for items with both a start and an end time (TIME_BLOCKED). */
    public static boolean hasTimeSlot(ScheduleItem item) {
        return item.getStartTime() != null && item.getEndTime() != null;
    }

    public static Optional<Kind> conflictKind(ScheduleItem a, ScheduleItem b) {
        if (a == b) return Optional.empty();
        if (a.getId() != null && a.getId().equals(b.getId())) return Optional.empty();
        if (a.getUser() == null || b.getUser() == null
                || !Objects.equals(a.getUser().getId(), b.getUser().getId())) return Optional.empty();

        TimeType ta = a.getTimeType();
        TimeType tb = b.getTimeType();
        if (ta == null || tb == null) return Optional.empty();
        if (ta == TimeType.DEADLINE_ONLY || tb == TimeType.DEADLINE_ONLY) return Optional.empty();

        if (ta == TimeType.TIME_BLOCKED && tb == TimeType.TIME_BLOCKED) {
            return a.getStartTime().isBefore(b.getEndTime()) && b.getStartTime().isBefore(a.getEndTime())
                    ? Optional.of(Kind.OVERLAP) : Optional.empty();
        }

        if (ta == TimeType.START_ONLY && tb == TimeType.START_ONLY) {
            return sameInstant(a, b) ? Optional.of(Kind.SAME_START) : Optional.empty();
        }

        // one marker (START_ONLY), one block (TIME_BLOCKED)
        ScheduleItem marker = ta == TimeType.START_ONLY ? a : b;
        ScheduleItem block  = ta == TimeType.START_ONLY ? b : a;
        boolean inside = !marker.getStartTime().isBefore(block.getStartTime())   // marker >= block start
                && marker.getStartTime().isBefore(block.getEndTime());           // marker <  block end
        return inside ? Optional.of(Kind.START_IN_BLOCK) : Optional.empty();
    }

    public static boolean overlaps(ScheduleItem a, ScheduleItem b) {
        return conflictKind(a, b).isPresent();
    }

    /** The items in {@code others} that conflict with {@code item}, earliest first. */
    public static List<ScheduleItem> findConflicts(ScheduleItem item, Collection<ScheduleItem> others) {
        return others.stream()
                .filter(other -> overlaps(item, other))
                .sorted(Comparator.comparing(ScheduleItem::getStartTime))
                .toList();
    }

    private static boolean sameInstant(ScheduleItem a, ScheduleItem b) {
        return !a.getStartTime().isBefore(b.getStartTime()) && !b.getStartTime().isBefore(a.getStartTime());
    }
}