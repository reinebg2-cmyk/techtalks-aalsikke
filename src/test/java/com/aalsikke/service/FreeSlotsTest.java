package com.aalsikke.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** KAN-343: free slots follow the conflict rules, the window, 15-minute marks and "now". */
class FreeSlotsTest {

    private static final ZoneId BEIRUT = ZoneId.of("Asia/Beirut");
    private static final LocalDate SAT = LocalDate.of(2026, 10, 10);   // Beirut is +03:00 on this day
    private static final LocalTime FROM = LocalTime.of(8, 0);
    private static final LocalTime TO = LocalTime.of(22, 0);
    private static final Duration HOUR = Duration.ofMinutes(60);

    private static OffsetDateTime at(int hour, int minute) {
        return OffsetDateTime.of(2026, 10, 10, hour, minute, 0, 0, ZoneOffset.ofHours(3));
    }

    private static FreeSlots.Busy block(int h1, int m1, int h2, int m2) {
        return FreeSlots.Busy.block(at(h1, m1), at(h2, m2));
    }

    private static List<FreeSlots.Slot> find(List<FreeSlots.Busy> busy) {
        return FreeSlots.find(busy, SAT, FROM, TO, HOUR, Duration.ZERO, null, BEIRUT, 3);
    }

    @Test
    void emptyDayGivesSpreadOutSlotsFromWindowStart() {
        List<FreeSlots.Slot> slots = find(List.of());
        assertEquals(3, slots.size());
        assertEquals(at(8, 0), slots.get(0).start());
        assertEquals(at(9, 0), slots.get(1).start());
        assertEquals(at(10, 0), slots.get(2).start());
        assertEquals(at(22, 0), slots.get(0).freeUntil());
    }

    @Test
    void slotCanStartExactlyWhenABlockEnds() {
        List<FreeSlots.Slot> slots = find(List.of(block(8, 30, 10, 0)));
        assertEquals(at(10, 0), slots.get(0).start());
        assertEquals(at(11, 0), slots.get(0).end());
    }

    @Test
    void slotsNeverOverlapBlocks() {
        List<FreeSlots.Busy> busy = List.of(block(8, 0, 9, 30), block(10, 0, 12, 15), block(13, 0, 13, 45));
        List<FreeSlots.Slot> slots = FreeSlots.find(busy, SAT, FROM, TO, HOUR, Duration.ZERO, null, BEIRUT, 10);
        assertFalse(slots.isEmpty());
        for (FreeSlots.Slot slot : slots) {
            for (FreeSlots.Busy b : busy) {
                boolean overlaps = slot.start().isBefore(b.end()) && b.start().isBefore(slot.end());
                assertFalse(overlaps, "slot " + slot + " overlaps " + b);
            }
        }
        assertEquals(at(13, 45), slots.get(0).start());   // 12:15-13:00 is only 45 min, so the first full hour is after 13:45
    }

    @Test
    void slotMayNotContainAStartOnlyMarker() {
        // ConflictRules: a marker conflicts with a block when start <= marker < end
        List<FreeSlots.Slot> slots = find(List.of(FreeSlots.Busy.marker(at(8, 30))));
        assertEquals(at(8, 45), slots.get(0).start());
    }

    @Test
    void freeUntilShowsWhenTheNextBlockStarts() {
        List<FreeSlots.Slot> slots = find(List.of(block(12, 0, 13, 0)));
        assertEquals(at(12, 0), slots.get(0).freeUntil());
    }

    @Test
    void fullDayGivesNoSlots() {
        assertTrue(find(List.of(block(7, 0, 23, 0))).isEmpty());
    }

    @Test
    void gapShorterThanTheLengthIsSkipped() {
        assertTrue(find(List.of(block(8, 0, 10, 0), block(10, 45, 22, 0))).isEmpty());
    }

    @Test
    void todayStartsAfterNowOnA15MinuteMark() {
        List<FreeSlots.Slot> slots =
                FreeSlots.find(List.of(), SAT, FROM, TO, HOUR, Duration.ZERO, at(10, 7), BEIRUT, 3);
        assertEquals(at(10, 15), slots.get(0).start());
    }

    @Test
    void bufferKeepsAGapAfterBlocks() {
        List<FreeSlots.Slot> slots = FreeSlots.find(List.of(block(8, 0, 9, 0)), SAT, FROM, TO,
                HOUR, Duration.ofMinutes(10), null, BEIRUT, 3);
        assertEquals(at(9, 15), slots.get(0).start());   // 09:10 rounded to the next mark
    }

    @Test
    void customWindowIsRespected() {
        List<FreeSlots.Slot> slots = FreeSlots.find(List.of(), SAT, LocalTime.of(18, 0), LocalTime.of(20, 0),
                Duration.ofMinutes(90), Duration.ZERO, null, BEIRUT, 3);
        assertEquals(1, slots.size());
        assertEquals(at(18, 0), slots.get(0).start());
        assertEquals(at(19, 30), slots.get(0).end());
        assertEquals(at(20, 0), slots.get(0).freeUntil());
    }

    @Test
    void blockCrossingTheWindowStartPushesTheFirstSlot() {
        assertEquals(at(8, 45), find(List.of(block(6, 0, 8, 45))).get(0).start());
    }

    @Test
    void roundUpKeepsExactMarks() {
        assertEquals(at(10, 15), FreeSlots.roundUp(at(10, 15), BEIRUT));
        assertEquals(at(10, 30), FreeSlots.roundUp(at(10, 16), BEIRUT));
    }
}