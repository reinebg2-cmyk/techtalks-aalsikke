package com.aalsikke.service;

import com.aalsikke.model.ItemType;
import com.aalsikke.model.ScheduleItem;
import com.aalsikke.model.User;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** S3-07 conflict rules. Pure unit tests: no Spring context, no database. */
class ConflictRulesTest {

    private final User alice = user();
    private final User bob = user();

    // ---- The six cases required by S3-07 ----

    @Test
    void partialOverlap_isConflict() {
        ScheduleItem a = item(alice, "2026-09-23T10:00:00Z", "2026-09-23T11:00:00Z");
        ScheduleItem b = item(alice, "2026-09-23T10:30:00Z", "2026-09-23T11:30:00Z");

        assertTrue(ConflictRules.overlaps(a, b));
        assertTrue(ConflictRules.overlaps(b, a), "the rule must be symmetric");
    }

    @Test
    void fullyInside_isConflict() {
        ScheduleItem outer = item(alice, "2026-09-23T10:00:00Z", "2026-09-23T12:00:00Z");
        ScheduleItem inner = item(alice, "2026-09-23T10:30:00Z", "2026-09-23T11:00:00Z");

        assertTrue(ConflictRules.overlaps(outer, inner));
        assertTrue(ConflictRules.overlaps(inner, outer));
    }

    @Test
    void backToBack_isNotConflict() {
        ScheduleItem first = item(alice, "2026-09-23T10:00:00Z", "2026-09-23T11:00:00Z");
        ScheduleItem second = item(alice, "2026-09-23T11:00:00Z", "2026-09-23T12:00:00Z");

        assertFalse(ConflictRules.overlaps(first, second));
        assertFalse(ConflictRules.overlaps(second, first));
    }

    @Test
    void differentUsers_isNotConflict() {
        ScheduleItem aliceItem = item(alice, "2026-09-23T10:00:00Z", "2026-09-23T11:00:00Z");
        ScheduleItem bobItem = item(bob, "2026-09-23T10:00:00Z", "2026-09-23T11:00:00Z");

        assertFalse(ConflictRules.overlaps(aliceItem, bobItem));
    }

    @Test
    void deadlineOnlyItem_neverConflicts() {
        ScheduleItem timed = item(alice, "2026-09-23T10:00:00Z", "2026-09-23T11:00:00Z");
        ScheduleItem deadlineOnly = item(alice, null, null);
        deadlineOnly.setDeadline(OffsetDateTime.parse("2026-09-23T10:30:00Z"));

        assertFalse(ConflictRules.hasTimeSlot(deadlineOnly));
        assertFalse(ConflictRules.overlaps(timed, deadlineOnly));
        assertFalse(ConflictRules.overlaps(deadlineOnly, timed));
    }

    @Test
    void itemEditedSoItNoLongerOverlaps_isNoLongerConflict() {
        ScheduleItem a = item(alice, "2026-09-23T10:00:00Z", "2026-09-23T11:00:00Z");
        ScheduleItem b = item(alice, "2026-09-23T10:30:00Z", "2026-09-23T11:30:00Z");
        assertTrue(ConflictRules.overlaps(a, b));

        // Same object, new times - as after a partial PUT
        b.setStartTime(OffsetDateTime.parse("2026-09-23T13:00:00Z"));
        b.setEndTime(OffsetDateTime.parse("2026-09-23T14:00:00Z"));

        assertFalse(ConflictRules.overlaps(a, b));
    }

    // ---- Extra edge cases ----

    @Test
    void itemNeverConflictsWithItself() {
        ScheduleItem a = item(alice, "2026-09-23T10:00:00Z", "2026-09-23T11:00:00Z");

        assertFalse(ConflictRules.overlaps(a, a));
    }

    @Test
    void differentOffsets_areComparedAsTheSameInstant() {
        // 13:30+03:00 is 10:30Z, which is inside 10:00Z-11:00Z
        ScheduleItem utc = item(alice, "2026-09-23T10:00:00Z", "2026-09-23T11:00:00Z");
        ScheduleItem beirut = item(alice, "2026-09-23T13:30:00+03:00", "2026-09-23T14:30:00+03:00");

        assertTrue(ConflictRules.overlaps(utc, beirut));
    }

    @Test
    void findConflicts_returnsOnlyClashingItems_earliestFirst() {
        ScheduleItem target = item(alice, "2026-09-23T10:00:00Z", "2026-09-23T12:00:00Z");
        ScheduleItem late = item(alice, "2026-09-23T11:00:00Z", "2026-09-23T13:00:00Z");
        ScheduleItem early = item(alice, "2026-09-23T09:00:00Z", "2026-09-23T10:30:00Z");
        ScheduleItem backToBack = item(alice, "2026-09-23T12:00:00Z", "2026-09-23T13:00:00Z");
        ScheduleItem otherUser = item(bob, "2026-09-23T10:00:00Z", "2026-09-23T12:00:00Z");

        List<ScheduleItem> result = ConflictRules.findConflicts(
                target, List.of(late, backToBack, otherUser, early, target));

        assertEquals(List.of(early, late), result);
    }

    // ---- Helpers ----

    private static User user() {
        User u = new User();
        u.setId(UUID.randomUUID());
        return u;
    }

    private static ScheduleItem item(User owner, String start, String end) {
        ScheduleItem i = new ScheduleItem();
        i.setId(UUID.randomUUID());
        i.setUser(owner);
        i.setTitle("test");
        i.setItemType(ItemType.STUDY);
        i.setStartTime(start == null ? null : OffsetDateTime.parse(start));
        i.setEndTime(end == null ? null : OffsetDateTime.parse(end));
        return i;
    }
}