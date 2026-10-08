package com.aalsikke.service;

import com.aalsikke.model.Reminder;
import com.aalsikke.model.ScheduleItem;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** KAN-352: the halfway reminder for any task length, and keeping it in step with the task. */
class DefaultRemindersTest {

    private static final OffsetDateTime NOW = at(2026, 10, 1, 8, 0);

    private static OffsetDateTime at(int y, int mo, int d, int h, int mi) {
        return OffsetDateTime.of(y, mo, d, h, mi, 0, 0, ZoneOffset.UTC);
    }

    private static ScheduleItem task(OffsetDateTime start, OffsetDateTime end, OffsetDateTime deadline) {
        ScheduleItem item = new ScheduleItem();
        item.setStartTime(start);
        item.setEndTime(end);
        item.setDeadline(deadline);
        return item;
    }

    private static Reminder defaultOf(ScheduleItem item) {
        return item.getReminders().stream().filter(Reminder::isDefaultReminder).findFirst().orElse(null);
    }

    // ---- halfwayPoint: any length ----

    @Test
    void hours() {
        assertEquals(at(2026, 10, 1, 11, 0),
                DefaultReminders.halfwayPoint(task(at(2026, 10, 1, 10, 0), null, at(2026, 10, 1, 12, 0)), NOW));
    }

    @Test
    void days() {
        assertEquals(at(2026, 10, 6, 10, 0),
                DefaultReminders.halfwayPoint(task(at(2026, 10, 1, 10, 0), null, at(2026, 10, 11, 10, 0)), NOW));
    }

    @Test
    void weeks() {
        assertEquals(at(2026, 10, 8, 10, 0),
                DefaultReminders.halfwayPoint(task(at(2026, 10, 1, 10, 0), null, at(2026, 10, 15, 10, 0)), NOW));
    }

    @Test
    void months() {
        // 1 Oct -> 1 Dec is 61 days, so halfway is 30.5 days in: 31 Oct 22:00
        assertEquals(at(2026, 10, 31, 22, 0),
                DefaultReminders.halfwayPoint(task(at(2026, 10, 1, 10, 0), null, at(2026, 12, 1, 10, 0)), NOW));
    }

    @Test
    void noDeadline_usesEndTime() {
        assertEquals(at(2026, 10, 2, 13, 0),
                DefaultReminders.halfwayPoint(task(at(2026, 10, 2, 9, 0), at(2026, 10, 2, 17, 0), null), NOW));
    }

    @Test
    void noStart_usesCreationTime() {
        ScheduleItem item = task(null, null, at(2026, 10, 3, 10, 0));
        item.setCreatedAt(at(2026, 10, 1, 10, 0));
        assertEquals(at(2026, 10, 2, 10, 0), DefaultReminders.halfwayPoint(item, NOW));
    }

    @Test
    void newTaskWithoutStart_usesNow() {
        assertEquals(at(2026, 10, 1, 9, 0),
                DefaultReminders.halfwayPoint(task(null, null, at(2026, 10, 1, 10, 0)), NOW));
    }

    @Test
    void startOnly_hasNoHalfway() {
        assertNull(DefaultReminders.halfwayPoint(task(at(2026, 10, 2, 9, 0), null, null), NOW));
    }

    @Test
    void roundedDownToTheMinute() {
        // 10:00 -> 10:01 is 60 s, halfway is 10:00:30, shown as 10:00
        assertEquals(at(2026, 10, 2, 10, 0),
                DefaultReminders.halfwayPoint(task(at(2026, 10, 2, 10, 0), null, at(2026, 10, 2, 10, 1)), NOW));
    }

    // ---- sync: keeping the default reminder in step ----

    @Test
    void wanted_createsTheDefault() {
        ScheduleItem item = task(at(2026, 10, 2, 10, 0), null, at(2026, 10, 4, 10, 0));
        DefaultReminders.sync(item, true, NOW);
        assertEquals(at(2026, 10, 3, 10, 0), defaultOf(item).getRemindAt());
    }

    @Test
    void noChangeAsked_doesNotCreateOne() {
        ScheduleItem item = task(at(2026, 10, 2, 10, 0), null, at(2026, 10, 4, 10, 0));
        DefaultReminders.sync(item, null, NOW);
        assertTrue(item.getReminders().isEmpty());
    }

    @Test
    void notWanted_removesOnlyTheDefault() {
        ScheduleItem item = task(at(2026, 10, 2, 10, 0), null, at(2026, 10, 4, 10, 0));
        DefaultReminders.sync(item, true, NOW);
        Reminder custom = new Reminder();
        custom.setRemindAt(at(2026, 10, 2, 12, 0));
        item.getReminders().add(custom);

        DefaultReminders.sync(item, false, NOW);

        assertNull(defaultOf(item));
        assertEquals(1, item.getReminders().size());   // the custom one stays
    }

    @Test
    void taskMoved_defaultFollowsAndFiresAgain() {
        ScheduleItem item = task(at(2026, 10, 2, 10, 0), null, at(2026, 10, 4, 10, 0));
        DefaultReminders.sync(item, true, NOW);
        Reminder def = defaultOf(item);
        def.setTriggered(true);
        def.setEmailedAt(at(2026, 10, 3, 10, 0));

        item.setDeadline(at(2026, 10, 6, 10, 0));   // due 2 days later
        DefaultReminders.sync(item, null, NOW);

        assertEquals(at(2026, 10, 4, 10, 0), def.getRemindAt());
        assertFalse(def.isTriggered());
        assertNull(def.getEmailedAt());
    }

    @Test
    void halfwayAlreadyPassed_leavesTheDefaultAlone() {
        ScheduleItem item = task(at(2026, 10, 2, 10, 0), null, at(2026, 10, 4, 10, 0));
        DefaultReminders.sync(item, true, NOW);
        Reminder def = defaultOf(item);

        OffsetDateTime later = at(2026, 10, 3, 18, 0);   // after the halfway point
        item.setStartTime(at(2026, 10, 3, 10, 0));        // new halfway would be 3 Oct 22:00... still future
        item.setDeadline(at(2026, 10, 3, 20, 0));         // ...but this makes it 3 Oct 15:00, already past
        DefaultReminders.sync(item, null, later);

        assertEquals(at(2026, 10, 3, 10, 0), def.getRemindAt());   // unchanged
    }
}