package com.aalsikke.service;

import com.aalsikke.model.ItemType;
import com.aalsikke.model.RecurrenceRule;
import com.aalsikke.model.ScheduleItem;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** KAN-339: the .ics file imports with correct Beirut times, weekly repeats and safe titles. */
class IcsCalendarTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 10, 9, 12, 0, 0, 0, ZoneOffset.UTC);

    private static OffsetDateTime utc(int y, int mo, int d, int h, int mi) {
        return OffsetDateTime.of(y, mo, d, h, mi, 0, 0, ZoneOffset.UTC);
    }

    private static ScheduleItem item(String title, OffsetDateTime start, OffsetDateTime end, OffsetDateTime deadline) {
        ScheduleItem item = new ScheduleItem();
        item.setId(UUID.randomUUID());
        item.setTitle(title);
        item.setItemType(ItemType.CLASS);
        item.setStartTime(start);
        item.setEndTime(end);
        item.setDeadline(deadline);
        return item;
    }

    private static String ics(ScheduleItem item) {
        return IcsCalendar.build(List.of(item), Map.of(), NOW);
    }

    @Test
    void timeBlockedUsesBeirutSummerTime() {
        // 06:00 UTC in October (before the 25th) is 09:00 in Beirut (+03:00)
        String out = ics(item("Lecture", utc(2026, 10, 12, 6, 0), utc(2026, 10, 12, 7, 30), null));
        assertTrue(out.contains("DTSTART;TZID=Asia/Beirut:20261012T090000\r\n"));
        assertTrue(out.contains("DTEND;TZID=Asia/Beirut:20261012T103000\r\n"));
    }

    @Test
    void timeBlockedUsesBeirutWinterTime() {
        // 07:00 UTC in November is 09:00 in Beirut (+02:00)
        String out = ics(item("Lecture", utc(2026, 11, 2, 7, 0), utc(2026, 11, 2, 8, 0), null));
        assertTrue(out.contains("DTSTART;TZID=Asia/Beirut:20261102T090000\r\n"));
    }

    @Test
    void deadlineOnlyIsFifteenMinutesEndingAtTheDeadline() {
        String out = ics(item("Essay", null, null, utc(2026, 10, 15, 20, 59)));
        assertTrue(out.contains("DTSTART;TZID=Asia/Beirut:20261015T234400\r\n"));
        assertTrue(out.contains("DTEND;TZID=Asia/Beirut:20261015T235900\r\n"));
    }

    @Test
    void startOnlyRunsToTheDeadlineIfThereIsOne() {
        String out = ics(item("Project", utc(2026, 10, 12, 6, 0), null, utc(2026, 10, 12, 9, 0)));
        assertTrue(out.contains("DTEND;TZID=Asia/Beirut:20261012T120000\r\n"));
    }

    @Test
    void startOnlyWithoutDeadlineIsFifteenMinutes() {
        String out = ics(item("Call", utc(2026, 10, 12, 6, 0), null, null));
        assertTrue(out.contains("DTEND;TZID=Asia/Beirut:20261012T091500\r\n"));
    }

    @Test
    void itemWithNoTimesIsSkipped() {
        assertFalse(ics(item("Someday", null, null, null)).contains("BEGIN:VEVENT"));
    }

    @Test
    void weeklyRuleBecomesRrule() {
        ScheduleItem lecture = item("Java", utc(2026, 10, 5, 6, 0), utc(2026, 10, 5, 7, 15), null);
        RecurrenceRule rule = new RecurrenceRule();
        rule.setFrequency("WEEKLY");
        rule.setIntervalValue(2);
        rule.setDaysOfWeek("MON,WED");
        rule.setUntilDate(LocalDate.of(2026, 12, 20));

        String out = IcsCalendar.build(List.of(lecture), Map.of(lecture.getId(), rule), NOW);
        // 2026-12-20 23:59:59 in Beirut (+02:00) is 21:59:59 UTC
        assertTrue(out.contains("RRULE:FREQ=WEEKLY;WKST=MO;INTERVAL=2;BYDAY=MO,WE;UNTIL=20261220T215959Z\r\n"));
    }

    @Test
    void weeklyRuleWithoutDaysOrEndRepeatsOnTheSameWeekday() {
        ScheduleItem lecture = item("Java", utc(2026, 10, 5, 6, 0), utc(2026, 10, 5, 7, 15), null);
        RecurrenceRule rule = new RecurrenceRule();
        rule.setFrequency("WEEKLY");

        String out = IcsCalendar.build(List.of(lecture), Map.of(lecture.getId(), rule), NOW);
        assertTrue(out.contains("RRULE:FREQ=WEEKLY;WKST=MO\r\n"));
    }

    @Test
    void commasSemicolonsAndNewlinesAreEscaped() {
        ScheduleItem essay = item("Read ch. 1, 2; then summarise", utc(2026, 10, 12, 6, 0), utc(2026, 10, 12, 7, 0), null);
        essay.setDescription("line one\nline two \\ done");
        String out = ics(essay);
        assertTrue(out.contains("SUMMARY:Read ch. 1\\, 2\\; then summarise\r\n"));
        assertTrue(out.contains("DESCRIPTION:line one\\nline two \\\\ done\r\n"));
    }

    @Test
    void longArabicLinesAreFoldedWithoutBreakingCharacters() {
        String title = "مراجعة ".repeat(20);
        String out = ics(item(title, utc(2026, 10, 12, 6, 0), utc(2026, 10, 12, 7, 0), null));
        for (String line : out.split("\r\n")) {
            assertTrue(line.getBytes(StandardCharsets.UTF_8).length <= 75, "line too long: " + line);
        }
        // unfolding gives the original title back
        assertTrue(out.replace("\r\n ", "").contains("SUMMARY:" + title + "\r\n"));
    }

    @Test
    void wrapsEventsInACalendarWithTheBeirutTimezone() {
        String out = ics(item("Lecture", utc(2026, 10, 12, 6, 0), utc(2026, 10, 12, 7, 0), null));
        assertTrue(out.startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n"));
        assertTrue(out.contains("BEGIN:VTIMEZONE\r\nTZID:Asia/Beirut\r\n"));
        assertTrue(out.endsWith("END:VCALENDAR\r\n"));
    }
}