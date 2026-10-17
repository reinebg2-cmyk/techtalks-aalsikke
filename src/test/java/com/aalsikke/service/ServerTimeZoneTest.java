package com.aalsikke.service;

import com.aalsikke.dto.ScheduleItemResponse;
import com.aalsikke.model.ItemType;
import com.aalsikke.model.RecurrenceRule;
import com.aalsikke.model.ScheduleItem;
import com.aalsikke.model.TimeType;
import com.aalsikke.model.User;
import com.aalsikke.repository.ChecklistItemRepository;
import com.aalsikke.repository.DailyReviewRepository;
import com.aalsikke.repository.CourseRepository;
import com.aalsikke.repository.OccurrenceCompletionRepository;
import com.aalsikke.repository.RecurrenceRuleRepository;
import com.aalsikke.repository.ScheduleItemRepository;
import com.aalsikke.repository.UserRepository;
import com.aalsikke.repository.WorkplaceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.argThat;

/**
 * KAN-364: the app works in Lebanon time even when the server runs in UTC (as on Railway).
 * Each test forces the JVM to UTC and puts the original zone back afterwards.
 */
class ServerTimeZoneTest {

    private static final String EMAIL = "student@test.com";

    private TimeZone originalZone;
    private ScheduleItemService service;

    @BeforeEach
    void setUp() {
        originalZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));   // like the Railway container

        UserRepository userRepository = mock(UserRepository.class);
        RecurrenceRuleRepository recurrenceRuleRepository = mock(RecurrenceRuleRepository.class);
        service = new ScheduleItemService(mock(ScheduleItemRepository.class), userRepository,
                mock(CourseRepository.class), mock(WorkplaceRepository.class), recurrenceRuleRepository,
                mock(ChecklistItemRepository.class), mock(OccurrenceCompletionRepository.class));

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        // Fri Oct 23 2026, 10:00-11:00 Beirut summer time (UTC+3) = 07:00Z-08:00Z, every Friday
        ScheduleItem base = new ScheduleItem();
        base.setId(UUID.randomUUID());
        base.setUser(user);
        base.setTitle("Weekly class");
        base.setItemType(ItemType.CLASS);
        base.setTimeType(TimeType.TIME_BLOCKED);
        base.setStartTime(OffsetDateTime.parse("2026-10-23T07:00:00Z"));
        base.setEndTime(OffsetDateTime.parse("2026-10-23T08:00:00Z"));
        base.setRecurring(true);

        RecurrenceRule rule = new RecurrenceRule();
        rule.setScheduleItem(base);
        rule.setFrequency("WEEKLY");
        rule.setIntervalValue(1);
        rule.setDaysOfWeek("FRI");
        when(recurrenceRuleRepository.findActiveWeeklyForUser(eq(user.getId()), any(), any()))
                .thenReturn(List.of(rule));
    }

    @AfterEach
    void restoreZone() {
        TimeZone.setDefault(originalZone);
    }

    @Test
    void weeklyRepeatStaysAt10BeirutAfterTheClockChange_evenOnAUtcServer() {
        // Week of Mon Oct 26 - Sun Nov 1, Beirut time. Lebanon is UTC+2 from Oct 25,
        // so 10:00 Beirut on Fri Oct 30 is 08:00Z.
        List<ScheduleItemResponse> repeats = service.getItemsInRange(EMAIL,
                        OffsetDateTime.parse("2026-10-25T22:00:00Z"), OffsetDateTime.parse("2026-11-01T22:00:00Z"))
                .stream().filter(ScheduleItemResponse::isOccurrence).toList();

        assertEquals(1, repeats.size());
        assertEquals(OffsetDateTime.parse("2026-10-30T08:00:00Z").toInstant(), repeats.get(0).getStartTime().toInstant());
    }

    @Test
    void weeklyReviewWeekStartsAtMidnightBeirut_evenOnAUtcServer() {
        ScheduleItemRepository items = mock(ScheduleItemRepository.class);
        UserRepository users = mock(UserRepository.class);
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(EMAIL);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        DailyReviewService reviews = new DailyReviewService(mock(DailyReviewRepository.class), items, users,
                mock(ScheduleItemService.class));

        reviews.getWeeklySummary(EMAIL, LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25));

        // Mon Oct 19, 00:00 Beirut (UTC+3) = Sun Oct 18, 21:00Z, not Mon 00:00Z
        OffsetDateTime mondayMidnightBeirut = OffsetDateTime.parse("2026-10-18T21:00:00Z");
        verify(items).findDueBetween(eq(user.getId()),
                argThat(from -> from.toInstant().equals(mondayMidnightBeirut.toInstant())), any());
    }
}