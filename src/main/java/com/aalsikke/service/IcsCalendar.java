package com.aalsikke.service;

import com.aalsikke.model.RecurrenceRule;
import com.aalsikke.model.ScheduleItem;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * KAN-339: turns a user's schedule items into an iCalendar (.ics) file (RFC 5545).
 * Pure Java (no Spring, no database) so it can be unit-tested directly, like RecurrenceExpander.
 *
 * Rules:
 *  - TIME_BLOCKED: event from start to end.
 *  - START_ONLY: event from start to the deadline if there is a later one, otherwise 15 minutes.
 *  - DEADLINE_ONLY: 15-minute event ending at the deadline.
 *  - Weekly recurring items get an RRULE, so the calendar app creates the repeats itself.
 *  - All times are written in Asia/Beirut with a VTIMEZONE block, so a weekly 09:00 class
 *    stays at 09:00 across the daylight-saving change.
 */
public final class IcsCalendar {

    public static final ZoneId ZONE = com.aalsikke.config.AppTime.ZONE;   // the VTIMEZONE block below describes this zone
    static final Duration SHORT_EVENT = Duration.ofMinutes(15);

    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final String CRLF = "\r\n";

    /** Lebanon: summer time from the last Sunday of March to the last Sunday of October, at 00:00. */
    private static final String VTIMEZONE = String.join(CRLF,
            "BEGIN:VTIMEZONE",
            "TZID:Asia/Beirut",
            "BEGIN:DAYLIGHT",
            "TZOFFSETFROM:+0200",
            "TZOFFSETTO:+0300",
            "TZNAME:EEST",
            "DTSTART:19700329T000000",
            "RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU",
            "END:DAYLIGHT",
            "BEGIN:STANDARD",
            "TZOFFSETFROM:+0300",
            "TZOFFSETTO:+0200",
            "TZNAME:EET",
            "DTSTART:19701025T000000",
            "RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU",
            "END:STANDARD",
            "END:VTIMEZONE");

    private IcsCalendar() {}

    /**
     * @param items        the user's items (course and workplace should already be loaded)
     * @param rulesByItem  recurrence rules keyed by schedule item id; items without a rule are one-off
     * @param now          used for DTSTAMP
     */
    public static String build(Collection<ScheduleItem> items, Map<UUID, RecurrenceRule> rulesByItem,
                               OffsetDateTime now) {
        StringBuilder out = new StringBuilder();
        line(out, "BEGIN:VCALENDAR");
        line(out, "VERSION:2.0");
        line(out, "PRODID:-//Aal Sikke//Planner//EN");
        line(out, "CALSCALE:GREGORIAN");
        line(out, "METHOD:PUBLISH");
        line(out, "X-WR-CALNAME:Aal Sikke");
        line(out, "X-WR-TIMEZONE:Asia/Beirut");
        out.append(VTIMEZONE).append(CRLF);

        String stamp = UTC.format(now.withOffsetSameInstant(ZoneOffset.UTC));
        for (ScheduleItem item : items) {
            OffsetDateTime[] slot = slotOf(item);
            if (slot == null) continue;   // nothing to place on a calendar

            line(out, "BEGIN:VEVENT");
            line(out, "UID:" + item.getId() + "@aalsikke");
            line(out, "DTSTAMP:" + stamp);
            line(out, "DTSTART;TZID=Asia/Beirut:" + local(slot[0]));
            line(out, "DTEND;TZID=Asia/Beirut:" + local(slot[1]));
            String rrule = rruleOf(rulesByItem.get(item.getId()), item);
            if (rrule != null) line(out, rrule);
            line(out, "SUMMARY:" + escape(item.getTitle()));
            if (hasText(item.getDescription())) line(out, "DESCRIPTION:" + escape(item.getDescription()));
            String location = locationOf(item);
            if (hasText(location)) line(out, "LOCATION:" + escape(location));
            if (item.getItemType() != null) line(out, "CATEGORIES:" + item.getItemType().name());
            line(out, "END:VEVENT");
        }
        line(out, "END:VCALENDAR");
        return out.toString();
    }

    /** [start, end] of the calendar event, or null if the item has no usable time. */
    static OffsetDateTime[] slotOf(ScheduleItem item) {
        OffsetDateTime start = item.getStartTime();
        OffsetDateTime end = item.getEndTime();
        OffsetDateTime deadline = item.getDeadline();

        if (start != null && end != null && end.isAfter(start)) {
            return new OffsetDateTime[]{start, end};
        }
        if (start != null) {
            OffsetDateTime until = (deadline != null && deadline.isAfter(start)) ? deadline : start.plus(SHORT_EVENT);
            return new OffsetDateTime[]{start, until};
        }
        if (deadline != null) {
            return new OffsetDateTime[]{deadline.minus(SHORT_EVENT), deadline};
        }
        return null;
    }

    /** Weekly rule -> RRULE line; null for non-weekly rules or items without a start and end. */
    static String rruleOf(RecurrenceRule rule, ScheduleItem item) {
        if (rule == null || !"WEEKLY".equals(rule.getFrequency())) return null;
        if (item.getStartTime() == null || item.getEndTime() == null) return null;   // same as the calendar page

        StringBuilder r = new StringBuilder("RRULE:FREQ=WEEKLY;WKST=MO");
        int interval = rule.getIntervalValue() != null ? rule.getIntervalValue() : 1;
        if (interval > 1) r.append(";INTERVAL=").append(interval);

        Set<DayOfWeek> days = RecurrenceExpander.parseDays(rule.getDaysOfWeek());
        if (!days.isEmpty()) {
            r.append(";BYDAY=").append(days.stream()
                    .map(d -> d.name().substring(0, 2))      // MONDAY -> MO
                    .collect(Collectors.joining(",")));
        }
        if (rule.getUntilDate() != null) {
            // until_date is inclusive (Beirut); with a TZID start, UNTIL must be written in UTC
            OffsetDateTime lastMoment = rule.getUntilDate().atTime(LocalTime.of(23, 59, 59))
                    .atZone(ZONE).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
            r.append(";UNTIL=").append(UTC.format(lastMoment));
        }
        return r.toString();
    }

    private static String locationOf(ScheduleItem item) {
        if (item.getCourse() != null && hasText(item.getCourse().getLocation())) return item.getCourse().getLocation();
        if (item.getWorkplace() != null && hasText(item.getWorkplace().getLocation())) return item.getWorkplace().getLocation();
        return null;
    }

    private static String local(OffsetDateTime time) {
        return LOCAL.format(time.atZoneSameInstant(ZONE));
    }

    /** RFC 5545 TEXT escaping: backslash, semicolon, comma and newlines. */
    static String escape(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
    }

    /** Writes one content line, folded at 75 octets without splitting a UTF-8 character (Arabic titles). */
    private static void line(StringBuilder out, String content) {
        int bytes = 0;
        for (int i = 0; i < content.length(); ) {
            int cp = content.codePointAt(i);
            int size = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + size > 75) {
                out.append(CRLF).append(' ');
                bytes = 1;          // the leading space counts
            }
            out.appendCodePoint(cp);
            bytes += size;
            i += Character.charCount(cp);
        }
        out.append(CRLF);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}