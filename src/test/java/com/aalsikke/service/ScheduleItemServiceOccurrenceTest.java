package com.aalsikke.service;

import com.aalsikke.dto.ScheduleItemResponse;
import com.aalsikke.model.ItemType;
import com.aalsikke.model.OccurrenceCompletion;
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
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** KAN-363: marking one weekly repeat done must not mark the whole series done. */
class ScheduleItemServiceOccurrenceTest {

    private static final String EMAIL = "student@test.com";
    private static final LocalDate OCT_23 = LocalDate.of(2026, 10, 23);

    private ScheduleItemRepository scheduleItemRepository;
    private OccurrenceCompletionRepository completionRepository;
    private ScheduleItemService service;
    private ScheduleItem base;

    @BeforeEach
    void setUp() {
        scheduleItemRepository = mock(ScheduleItemRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        RecurrenceRuleRepository recurrenceRuleRepository = mock(RecurrenceRuleRepository.class);
        completionRepository = mock(OccurrenceCompletionRepository.class);
        service = new ScheduleItemService(scheduleItemRepository, userRepository,
                mock(CourseRepository.class), mock(WorkplaceRepository.class),
                recurrenceRuleRepository, mock(ChecklistItemRepository.class), completionRepository);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        // Base item: Fri Oct 16 2026, 19:00Z-20:00Z, repeats every Friday with no end date.
        // 19:00Z is the same date in Beirut and in UTC, so the test passes in either server zone.
        base = new ScheduleItem();
        base.setId(UUID.randomUUID());
        base.setUser(user);
        base.setTitle("Recur test");
        base.setItemType(ItemType.ASSIGNMENT);
        base.setTimeType(TimeType.TIME_BLOCKED);
        base.setStartTime(OffsetDateTime.parse("2026-10-16T19:00:00Z"));
        base.setEndTime(OffsetDateTime.parse("2026-10-16T20:00:00Z"));
        base.setRecurring(true);

        RecurrenceRule rule = new RecurrenceRule();
        rule.setScheduleItem(base);
        rule.setFrequency("WEEKLY");
        rule.setIntervalValue(1);
        rule.setDaysOfWeek("FRI");
        when(recurrenceRuleRepository.findActiveWeeklyForUser(eq(user.getId()), any(), any()))
                .thenReturn(List.of(rule));
        when(recurrenceRuleRepository.findByScheduleItemId(base.getId())).thenReturn(Optional.of(rule));
        when(scheduleItemRepository.findById(base.getId())).thenReturn(Optional.of(base));
    }

    @Test
    void completingTheBaseItemDoesNotCompleteItsRepeats() {
        base.setStatus("COMPLETED");

        List<ScheduleItemResponse> repeats = repeatsBetween("2026-10-16T00:00:00Z", "2026-11-06T00:00:00Z");

        assertEquals(2, repeats.size(), "expected the Oct 23 and Oct 30 repeats");
        for (ScheduleItemResponse repeat : repeats) {
            assertEquals("PENDING", repeat.getStatus(), "repeat starting " + repeat.getStartTime());
        }
    }

    @Test
    void onlyTheCompletedRepeatShowsAsDone() {
        List<Object[]> rows = List.<Object[]>of(new Object[]{base.getId(), OCT_23});
        when(completionRepository.findCompletedDates(anyCollection(), any(), any())).thenReturn(rows);

        List<ScheduleItemResponse> repeats = repeatsBetween("2026-10-16T00:00:00Z", "2026-11-06T00:00:00Z");

        assertEquals(OCT_23, repeats.get(0).getOccurrenceDate());
        assertEquals("COMPLETED", repeats.get(0).getStatus());
        assertEquals(LocalDate.of(2026, 10, 30), repeats.get(1).getOccurrenceDate());
        assertEquals("PENDING", repeats.get(1).getStatus());
    }

    @Test
    void markingOneRepeatDone_savesThatDateOnly_andLeavesTheItemAlone() {
        ScheduleItemResponse result = service.updateStatus(base.getId(), "COMPLETED", OCT_23, EMAIL).orElseThrow();

        ArgumentCaptor<OccurrenceCompletion> saved = ArgumentCaptor.forClass(OccurrenceCompletion.class);
        verify(completionRepository).save(saved.capture());
        assertEquals(OCT_23, saved.getValue().getOccurrenceDate());
        verify(scheduleItemRepository, never()).save(any());
        assertEquals("PENDING", base.getStatus());
        assertEquals("COMPLETED", result.getStatus());
        assertEquals(OCT_23, result.getOccurrenceDate());
    }

    @Test
    void markingARepeatDoneTwice_savesOnce() {
        when(completionRepository.existsByScheduleItemIdAndOccurrenceDate(base.getId(), OCT_23)).thenReturn(true);

        service.updateStatus(base.getId(), "COMPLETED", OCT_23, EMAIL);

        verify(completionRepository, never()).save(any());
    }

    @Test
    void markingARepeatNotDone_removesThatDate() {
        ScheduleItemResponse result = service.updateStatus(base.getId(), "PENDING", OCT_23, EMAIL).orElseThrow();

        verify(completionRepository).deleteByScheduleItemIdAndOccurrenceDate(base.getId(), OCT_23);
        assertEquals("PENDING", result.getStatus());
    }

    @Test
    void aDateThatIsNotARepeat_isRejected() {
        // Thursday: not a repeat day. Oct 16: the base item itself, which is marked without a date.
        assertThrows(IllegalArgumentException.class,
                () -> service.updateStatus(base.getId(), "COMPLETED", LocalDate.of(2026, 10, 22), EMAIL));
        assertThrows(IllegalArgumentException.class,
                () -> service.updateStatus(base.getId(), "COMPLETED", LocalDate.of(2026, 10, 16), EMAIL));
        verify(completionRepository, never()).save(any());
    }

    private List<ScheduleItemResponse> repeatsBetween(String from, String to) {
        return service.getItemsInRange(EMAIL, OffsetDateTime.parse(from), OffsetDateTime.parse(to))
                .stream()
                .filter(ScheduleItemResponse::isOccurrence)
                .toList();
    }
}