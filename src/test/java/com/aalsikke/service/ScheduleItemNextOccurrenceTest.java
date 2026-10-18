package com.aalsikke.service;

import com.aalsikke.dto.ScheduleItemResponse;
import com.aalsikke.dto.ScheduleItemResponse.NextOccurrence;
import com.aalsikke.model.ItemType;
import com.aalsikke.model.RecurrenceRule;
import com.aalsikke.model.ScheduleItem;
import com.aalsikke.model.TimeType;
import com.aalsikke.model.User;
import com.aalsikke.repository.ChecklistItemRepository;
import com.aalsikke.repository.CourseRepository;
import com.aalsikke.repository.OccurrenceCompletionRepository;
import com.aalsikke.repository.RecurrenceRuleRepository;
import com.aalsikke.repository.ScheduleItemRepository;
import com.aalsikke.repository.UserRepository;
import com.aalsikke.repository.WorkplaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** KAN-371: /me gives a repeating task its next repeat, so the dashboard doesn't call it overdue. */
class ScheduleItemNextOccurrenceTest {

    private static final String EMAIL = "student@test.com";

    private OccurrenceCompletionRepository completions;
    private ScheduleItemService service;
    private ScheduleItem base;
    private RecurrenceRule rule;

    @BeforeEach
    void setUp() {
        ScheduleItemRepository items = mock(ScheduleItemRepository.class);
        UserRepository users = mock(UserRepository.class);
        RecurrenceRuleRepository rules = mock(RecurrenceRuleRepository.class);
        completions = mock(OccurrenceCompletionRepository.class);
        service = new ScheduleItemService(items, users, mock(CourseRepository.class), mock(WorkplaceRepository.class),
                rules, mock(ChecklistItemRepository.class), completions);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(EMAIL);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        // Weekly class: Fri Oct 16 2026, 10:00-11:00 Beirut (07:00Z-08:00Z), every Friday
        base = new ScheduleItem();
        base.setId(UUID.randomUUID());
        base.setUser(user);
        base.setTitle("Java class");
        base.setItemType(ItemType.CLASS);
        base.setTimeType(TimeType.TIME_BLOCKED);
        base.setStartTime(OffsetDateTime.parse("2026-10-16T07:00:00Z"));
        base.setEndTime(OffsetDateTime.parse("2026-10-16T08:00:00Z"));
        base.setDeadline(OffsetDateTime.parse("2026-10-16T08:00:00Z"));
        base.setRecurring(true);
        when(items.findByUserId(user.getId())).thenReturn(List.of(base));

        rule = new RecurrenceRule();
        rule.setScheduleItem(base);
        rule.setFrequency("WEEKLY");
        rule.setIntervalValue(1);
        rule.setDaysOfWeek("FRI");
        when(rules.findAllForUser(user.getId())).thenReturn(List.of(rule));
    }

    private NextOccurrence nextAt(String now) {
        List<ScheduleItemResponse> result = service.getItemsForCurrentUser(EMAIL, OffsetDateTime.parse(now));
        return result.get(0).getNextOccurrence();
    }

    @Test
    void afterTheFirstDate_nextIsNextWeek_notOverdue() {
        NextOccurrence next = nextAt("2026-10-18T09:00:00Z");   // Sunday after the first class

        assertNotNull(next);
        assertEquals(LocalDate.of(2026, 10, 23), next.date());
        assertEquals(OffsetDateTime.parse("2026-10-23T07:00:00Z").toInstant(), next.start().toInstant());
        assertEquals(OffsetDateTime.parse("2026-10-23T08:00:00Z").toInstant(), next.end().toInstant());
        assertEquals("PENDING", next.status());
        assertFalse(next.original());
    }

    @Test
    void beforeTheFirstClassIsOver_nextIsTheOriginal() {
        NextOccurrence next = nextAt("2026-10-16T07:30:00Z");   // during the first class

        assertTrue(next.original());
        assertEquals(LocalDate.of(2026, 10, 16), next.date());
    }

    @Test
    void thatWeekMarkedDone_isShownDone() {
        when(completions.existsByScheduleItemIdAndOccurrenceDate(base.getId(), LocalDate.of(2026, 10, 23)))
                .thenReturn(true);

        assertEquals("COMPLETED", nextAt("2026-10-18T09:00:00Z").status());
    }

    @Test
    void seriesEnded_noNext() {
        rule.setUntilDate(LocalDate.of(2026, 10, 16));   // only the first date

        assertNull(nextAt("2026-10-18T09:00:00Z"));
    }
}