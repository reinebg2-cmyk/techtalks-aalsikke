package com.aalsikke.service;

import com.aalsikke.dto.ScheduleItemResponse;
import com.aalsikke.dto.WeeklyReviewResponse;
import com.aalsikke.model.ItemType;
import com.aalsikke.model.ScheduleItem;
import com.aalsikke.model.TimeType;
import com.aalsikke.model.User;
import com.aalsikke.repository.DailyReviewRepository;
import com.aalsikke.repository.ScheduleItemRepository;
import com.aalsikke.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** KAN-363: the weekly review counts weekly repeats, each with its own done state. */
class DailyReviewServiceTest {

    private static final String EMAIL = "student@test.com";

    private ScheduleItemService scheduleItemService;
    private DailyReviewService service;
    private ScheduleItem base;

    @BeforeEach
    void setUp() {
        UserRepository userRepository = mock(UserRepository.class);
        scheduleItemService = mock(ScheduleItemService.class);
        service = new DailyReviewService(mock(DailyReviewRepository.class), mock(ScheduleItemRepository.class),
                userRepository, scheduleItemService);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        // A weekly class that started earlier; only its repeats fall in the tested weeks
        base = new ScheduleItem();
        base.setId(UUID.randomUUID());
        base.setUser(user);
        base.setTitle("Weekly class");
        base.setItemType(ItemType.CLASS);
        base.setTimeType(TimeType.TIME_BLOCKED);
        base.setStartTime(OffsetDateTime.parse("2026-10-02T19:00:00Z"));
        base.setEndTime(OffsetDateTime.parse("2026-10-02T20:00:00Z"));
        base.setRecurring(true);
    }

    @Test
    void aRepeatMarkedDone_countsAsCompleted() {
        inRange(repeat("2026-10-23", true));

        WeeklyReviewResponse week = service.getWeeklySummary(EMAIL, LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25));

        assertEquals(1, week.completedTasks());
        assertEquals(0, week.missedTasks());
        assertEquals(0, week.upcomingTasks());
    }

    @Test
    void aPastRepeatNotDone_countsAsMissed() {
        inRange(repeat("2026-10-09", false));

        WeeklyReviewResponse week = service.getWeeklySummary(EMAIL, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11));

        assertEquals(0, week.completedTasks());
        assertEquals(1, week.missedTasks());
        assertEquals(0, week.upcomingTasks());
    }

    @Test
    void aFutureRepeatNotDone_countsAsUpcoming() {
        inRange(repeat("2030-01-11", false));   // far in the future, so this stays "upcoming" whenever the test runs

        WeeklyReviewResponse week = service.getWeeklySummary(EMAIL, LocalDate.of(2030, 1, 7), LocalDate.of(2030, 1, 13));

        assertEquals(0, week.completedTasks());
        assertEquals(0, week.missedTasks());
        assertEquals(1, week.upcomingTasks());
    }

    // ---- Helpers ----

    private ScheduleItemResponse repeat(String date, boolean done) {
        OffsetDateTime start = OffsetDateTime.parse(date + "T19:00:00Z");
        return ScheduleItemResponse.occurrenceOf(base, start, start.plusHours(1), LocalDate.parse(date), done);
    }

    private void inRange(ScheduleItemResponse... items) {
        when(scheduleItemService.getItemsInRange(eq(EMAIL), any(), any())).thenReturn(List.of(items));
    }
}