package com.aalsikke.service;

import com.aalsikke.model.Reminder;
import com.aalsikke.model.ScheduleItem;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * KAN-352: a task's default "halfway" reminder, midway between when the task starts and when it is due.
 * Works the same for any length (hours, days, weeks, months): start + (end - start) / 2.
 */
final class DefaultReminders {

    private DefaultReminders() {}

    /**
     * start = start time, else the task's creation time (or now, for a task being created);
     * end = deadline, else end time. Null when there is no end or it isn't after the start.
     * Rounded down to the minute, like the time inputs in the UI.
     */
    static OffsetDateTime halfwayPoint(ScheduleItem item, OffsetDateTime now) {
        OffsetDateTime start = item.getStartTime() != null ? item.getStartTime()
                : item.getCreatedAt() != null ? item.getCreatedAt() : now;
        OffsetDateTime end = item.getDeadline() != null ? item.getDeadline() : item.getEndTime();
        if (end == null || !end.isAfter(start)) {
            return null;
        }
        return start.plus(Duration.between(start, end).dividedBy(2)).truncatedTo(ChronoUnit.MINUTES);
    }

    /**
     * Brings the task's default reminder in line with its times.
     * wanted: TRUE = the user wants it, FALSE = remove it, null = no change asked (only move an existing one).
     * A new or moved default must be in the future; if the halfway point has passed, an existing one is left as is.
     * Changes item.getReminders() (cascade + orphanRemoval), so saving the item saves the change.
     */
    static void sync(ScheduleItem item, Boolean wanted, OffsetDateTime now) {
        List<Reminder> reminders = item.getReminders();
        Reminder existing = reminders.stream().filter(Reminder::isDefaultReminder).findFirst().orElse(null);

        if (Boolean.FALSE.equals(wanted)) {
            if (existing != null) {
                reminders.remove(existing);
            }
            return;
        }

        OffsetDateTime target = halfwayPoint(item, now);
        if (target == null || !target.isAfter(now)) {
            return;
        }

        if (existing == null) {
            if (Boolean.TRUE.equals(wanted)) {
                Reminder reminder = new Reminder();
                reminder.setScheduleItem(item);
                reminder.setRemindAt(target);
                reminder.setDefaultReminder(true);
                reminders.add(reminder);
            }
        } else if (!existing.getRemindAt().isEqual(target)) {
            existing.setRemindAt(target);
            existing.setTriggered(false);   // a new time means it should fire again
            existing.setEmailedAt(null);    // ...and be emailed again (KAN-336)
        }
    }
}