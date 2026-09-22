package com.aalsikke.service;

import com.aalsikke.model.ScheduleItem;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Conflict rules (S3-07), kept free of Spring and the database so they can be unit-tested directly.
 *
 * Two items conflict when ALL of these hold:
 *  - they are different items,
 *  - they belong to the same user,
 *  - both have a start AND end time (a deadline alone is never a commitment - mentor's rule),
 *  - their time slots overlap: start1 < end2 AND start2 < end1.
 *    Back-to-back items (one ends exactly when the other starts) do NOT conflict.
 */
public final class ConflictRules {

    private ConflictRules() {}

    public static boolean hasTimeSlot(ScheduleItem item) {
        return item.getStartTime() != null && item.getEndTime() != null;
    }

    public static boolean overlaps(ScheduleItem a, ScheduleItem b) {
        if (a == b) return false;
        if (a.getId() != null && a.getId().equals(b.getId())) return false;
        if (!hasTimeSlot(a) || !hasTimeSlot(b)) return false;
        if (a.getUser() == null || b.getUser() == null
                || !Objects.equals(a.getUser().getId(), b.getUser().getId())) return false;

        return a.getStartTime().isBefore(b.getEndTime())
                && b.getStartTime().isBefore(a.getEndTime());
    }

    /** The items in {@code others} that conflict with {@code item}, earliest first. */
    public static List<ScheduleItem> findConflicts(ScheduleItem item, Collection<ScheduleItem> others) {
        return others.stream()
                .filter(other -> overlaps(item, other))
                .sorted(Comparator.comparing(ScheduleItem::getStartTime))
                .toList();
    }
}