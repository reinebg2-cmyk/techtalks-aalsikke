package com.aalsikke.service;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** KAN-370: where a reminder on a weekly item goes once its repeat is over. Pure, no Spring. */
class ReminderRollerTest {

    private static final ZoneId BEIRUT = ZoneId.of("Asia/Beirut");
    private static final Set<DayOfWeek> FRIDAY = Set.of(DayOfWeek.FRIDAY);

    // Weekly class: Fri Oct 16 2026, 10:00-11:00 Beirut summer time (UTC+3) = 07:00Z-08:00Z
    private static final OffsetDateTime BASE_START = at("2026-10-16T07:00:00Z");
    private static final OffsetDateTime BASE_END = at("2026-10-16T08:00:00Z");

    private static Optional<OffsetDateTime> roll(OffsetDateTime remindAt, LocalDate until, OffsetDateTime now) {
        return ReminderRoller.nextRemindAt(remindAt, BASE_START, BASE_END, 1, FRIDAY, until, BEIRUT, now);
    }

    @Test
    void thirtyMinutesBefore_movesToNextWeek_sameLocalTime() {
        // 09:30 Fri Oct 16; the class is over at 12:00 -> 09:30 Fri Oct 23 (still summer time)
        assertEquals(Optional.of(at("2026-10-23T06:30:00Z").toInstant()),
                roll(at("2026-10-16T06:30:00Z"), null, at("2026-10-16T09:00:00Z")).map(OffsetDateTime::toInstant));
    }

    @Test
    void acrossTheClockChange_staysThirtyMinutesBeforeTheClass() {
        // 09:30 Fri Oct 23 (UTC+3) -> 09:30 Fri Oct 30 (UTC+2) = 07:30Z
        assertEquals(Optional.of(at("2026-10-30T07:30:00Z").toInstant()),
                roll(at("2026-10-23T06:30:00Z"), null, at("2026-10-23T09:00:00Z")).map(OffsetDateTime::toInstant));
    }

    @Test
    void whileTheClassIsNotOver_staysWhereItIs() {
        // 10:30 on Fri Oct 16: the class runs until 11:00, so the reminder still belongs to this week
        assertTrue(roll(at("2026-10-16T06:30:00Z"), null, at("2026-10-16T07:30:00Z")).isEmpty());
    }

    @Test
    void halfwayReminderInsideTheClass_movesWithIt() {
        // 10:30 = halfway through 10:00-11:00 -> 10:30 next Friday
        assertEquals(Optional.of(at("2026-10-23T07:30:00Z").toInstant()),
                roll(at("2026-10-16T07:30:00Z"), null, at("2026-10-16T09:00:00Z")).map(OffsetDateTime::toInstant));
    }

    @Test
    void afterUntil_doesNotMove() {
        assertTrue(roll(at("2026-10-16T06:30:00Z"), LocalDate.of(2026, 10, 16), at("2026-10-16T09:00:00Z")).isEmpty());
    }

    @Test
    void jobWasDownForWeeks_jumpsToTheNextClassThatIsNotOver() {
        // Now: Fri Nov 20, 12:00 Beirut (10:00Z), that day's class is over -> 09:30 Fri Nov 27 (UTC+2) = 07:30Z
        assertEquals(Optional.of(at("2026-11-27T07:30:00Z").toInstant()),
                roll(at("2026-10-16T06:30:00Z"), null, at("2026-11-20T10:00:00Z")).map(OffsetDateTime::toInstant));
    }

    @Test
    void mondayAndWednesday_movesFromMondayToWednesday() {
        // Mon Oct 12, 09:00-10:00 Beirut, repeating Mon+Wed; reminder 08:30 Mon -> 08:30 Wed Oct 14
        Optional<OffsetDateTime> next = ReminderRoller.nextRemindAt(at("2026-10-12T05:30:00Z"),
                at("2026-10-12T06:00:00Z"), at("2026-10-12T07:00:00Z"), 1,
                Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), null, BEIRUT, at("2026-10-12T07:30:00Z"));
        assertEquals(Optional.of(at("2026-10-14T05:30:00Z").toInstant()), next.map(OffsetDateTime::toInstant));
    }

    private static OffsetDateTime at(String iso) {
        return OffsetDateTime.parse(iso);
    }
}