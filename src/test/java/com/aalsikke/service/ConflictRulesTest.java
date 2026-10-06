package com.aalsikke.service;

import com.aalsikke.model.ScheduleItem;
import com.aalsikke.model.TimeType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;

/**
 * Tests for ConflictRules. Items are Mockito mocks that answer only the getters ConflictRules
 * calls (getId, getUser, getTimeType, getStartTime, getEndTime), so no setters, constructors
 * or builders on ScheduleItem/User are needed. Return types (LocalDateTime, Instant, Long,
 * UUID, ...) are read from the real getter signatures.
 */
class ConflictRulesTest {

    private final Object alice = user(1L);
    private final Object bob = user(2L);

    // ---------- TIME_BLOCKED vs TIME_BLOCKED ----------

    @Test
    void blocksThatOverlapConflict() {
        ScheduleItem a = block(1L, alice, at(9), at(11));
        ScheduleItem b = block(2L, alice, at(10), at(12));
        assertEquals(Optional.of(ConflictRules.Kind.OVERLAP), ConflictRules.conflictKind(a, b));
        assertEquals(Optional.of(ConflictRules.Kind.OVERLAP), ConflictRules.conflictKind(b, a));
    }

    @Test
    void oneBlockContainingAnotherConflicts() {
        assertTrue(ConflictRules.overlaps(block(1L, alice, at(9), at(12)), block(2L, alice, at(10), at(11))));
    }

    @Test
    void backToBackBlocksDoNotConflict() {
        assertFalse(ConflictRules.overlaps(block(1L, alice, at(9), at(10)), block(2L, alice, at(10), at(11))));
        assertFalse(ConflictRules.overlaps(block(2L, alice, at(10), at(11)), block(1L, alice, at(9), at(10))));
    }

    @Test
    void separatedBlocksDoNotConflict() {
        assertFalse(ConflictRules.overlaps(block(1L, alice, at(9), at(10)), block(2L, alice, at(11), at(12))));
    }

    // ---------- START_ONLY vs TIME_BLOCKED ----------

    @Test
    void markerInsideBlockConflicts() {
        ScheduleItem block = block(1L, alice, at(9), at(11));
        ScheduleItem marker = marker(2L, alice, at(10));
        assertEquals(Optional.of(ConflictRules.Kind.START_IN_BLOCK), ConflictRules.conflictKind(marker, block));
        assertEquals(Optional.of(ConflictRules.Kind.START_IN_BLOCK), ConflictRules.conflictKind(block, marker));
    }

    @Test
    void markerAtBlockStartConflicts() {
        assertTrue(ConflictRules.overlaps(marker(2L, alice, at(9)), block(1L, alice, at(9), at(11))));
    }

    @Test
    void markerAtBlockEndDoesNotConflict() {
        assertFalse(ConflictRules.overlaps(marker(2L, alice, at(11)), block(1L, alice, at(9), at(11))));
    }

    @Test
    void markerBeforeOrAfterBlockDoesNotConflict() {
        ScheduleItem block = block(1L, alice, at(10), at(11));
        assertFalse(ConflictRules.overlaps(marker(2L, alice, at(9)), block));
        assertFalse(ConflictRules.overlaps(marker(3L, alice, at(12)), block));
    }

    // ---------- START_ONLY vs START_ONLY ----------

    @Test
    void markersAtSameInstantConflict() {
        ScheduleItem a = marker(1L, alice, at(10));
        ScheduleItem b = marker(2L, alice, at(10));
        assertEquals(Optional.of(ConflictRules.Kind.SAME_START), ConflictRules.conflictKind(a, b));
    }

    @Test
    void markersAtDifferentInstantsDoNotConflict() {
        assertFalse(ConflictRules.overlaps(marker(1L, alice, at(10)), marker(2L, alice, at(11))));
    }

    // ---------- DEADLINE_ONLY ----------

    @Test
    void deadlineOnlyNeverConflicts() {
        ScheduleItem deadline = deadline(1L, alice);
        assertFalse(ConflictRules.overlaps(deadline, block(2L, alice, at(9), at(11))));
        assertFalse(ConflictRules.overlaps(deadline, marker(3L, alice, at(10))));
        assertFalse(ConflictRules.overlaps(deadline, deadline(4L, alice)));
    }

    // ---------- guards ----------

    @Test
    void differentUsersNeverConflict() {
        assertFalse(ConflictRules.overlaps(block(1L, alice, at(9), at(11)), block(2L, bob, at(10), at(12))));
        assertFalse(ConflictRules.overlaps(marker(1L, alice, at(10)), marker(2L, bob, at(10))));
    }

    @Test
    void itemNeverConflictsWithItself() {
        ScheduleItem a = block(1L, alice, at(9), at(11));
        assertFalse(ConflictRules.overlaps(a, a));
        // same id, different instance (e.g. the stored copy while editing)
        assertFalse(ConflictRules.overlaps(a, block(1L, alice, at(10), at(12))));
    }

    @Test
    void unsavedItemsAreComparedByTimes() {
        assertTrue(ConflictRules.overlaps(block(null, alice, at(9), at(11)), block(null, alice, at(10), at(12))));
    }

    @Test
    void missingTimeTypeOrUserNeverConflicts() {
        ScheduleItem noType = item(1L, alice, null, at(9), at(11));
        assertFalse(ConflictRules.overlaps(noType, block(2L, alice, at(10), at(12))));

        ScheduleItem noUser = block(3L, null, at(9), at(11));
        assertFalse(ConflictRules.overlaps(noUser, block(4L, alice, at(10), at(12))));
    }

    // ---------- findConflicts ----------

    @Test
    void findConflictsReturnsOnlyConflictingItemsEarliestFirst() {
        ScheduleItem candidate = block(10L, alice, at(9), at(12));
        ScheduleItem late = marker(1L, alice, at(11));
        ScheduleItem early = block(2L, alice, at(9), at(10));
        ScheduleItem outside = block(3L, alice, at(12), at(13));
        ScheduleItem other = block(4L, bob, at(9), at(12));
        ScheduleItem dl = deadline(5L, alice);

        List<ScheduleItem> result =
                ConflictRules.findConflicts(candidate, List.of(late, early, outside, other, dl));

        assertEquals(List.of(early, late), result);
    }

    @Test
    void findConflictsWithNoMatchesIsEmpty() {
        assertTrue(ConflictRules.findConflicts(marker(1L, alice, at(9)),
                List.of(block(2L, alice, at(10), at(11)))).isEmpty());
    }

    // ---------- helpers ----------

    private static final Instant BASE = Instant.parse("2026-10-07T00:00:00Z");

    private static Instant at(int hour) {
        return BASE.plus(hour, ChronoUnit.HOURS);
    }

    private static Object user(long id) {
        Class<?> userType;
        try {
            userType = ScheduleItem.class.getMethod("getUser").getReturnType();
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
        return mock(userType, inv -> "getId".equals(inv.getMethod().getName())
                ? convertId(inv.getMethod().getReturnType(), id)
                : RETURNS_DEFAULTS.answer(inv));
    }

    private static ScheduleItem item(Long id, Object user, TimeType type, Instant start, Instant end) {
        return mock(ScheduleItem.class, inv -> {
            Class<?> returnType = inv.getMethod().getReturnType();
            switch (inv.getMethod().getName()) {
                case "getId":        return convertId(returnType, id);
                case "getUser":      return user;
                case "getTimeType":  return type;
                case "getStartTime": return convertTime(returnType, start);
                case "getEndTime":   return convertTime(returnType, end);
                default:             return RETURNS_DEFAULTS.answer(inv);
            }
        });
    }

    private static ScheduleItem block(Long id, Object user, Instant start, Instant end) {
        return item(id, user, TimeType.TIME_BLOCKED, start, end);
    }

    private static ScheduleItem marker(Long id, Object user, Instant start) {
        return item(id, user, TimeType.START_ONLY, start, null);
    }

    private static ScheduleItem deadline(Long id, Object user) {
        return item(id, user, TimeType.DEADLINE_ONLY, null, null);
    }

    private static Object convertId(Class<?> type, Long id) {
        if (id == null) return null;
        if (type == Long.class || type == long.class) return id;
        if (type == Integer.class || type == int.class) return id.intValue();
        if (type == UUID.class) return new UUID(0L, id);
        if (type == String.class) return String.valueOf(id);
        throw new IllegalStateException("Unsupported id type: " + type);
    }

    private static Object convertTime(Class<?> type, Instant value) {
        if (value == null) return null;
        if (type == Instant.class) return value;
        if (type == LocalDateTime.class) return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
        if (type == OffsetDateTime.class) return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
        if (type == ZonedDateTime.class) return ZonedDateTime.ofInstant(value, ZoneOffset.UTC);
        throw new IllegalStateException("Unsupported time type: " + type);
    }
}