package com.aalsikke.service;

import com.aalsikke.dto.ScheduleItemRequest;
import com.aalsikke.model.RecurrenceRule;
import com.aalsikke.model.ScheduleItem;
import com.aalsikke.model.TimeType;
import com.aalsikke.model.User;
import com.aalsikke.repository.RecurrenceRuleRepository;
import com.aalsikke.repository.ScheduleItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** KAN-387: rescheduling a weekly task can't stretch it past 8 hours (the limit was only checked when the repeat was created). */
@ExtendWith(MockitoExtension.class)
class WeeklyTaskEditLengthTest {

    private static final String EMAIL = "lara@example.com";

    @Mock ScheduleItemRepository items;
    @Mock RecurrenceRuleRepository rules;
    @InjectMocks ScheduleItemService service;

    private ScheduleItem shift;

    @BeforeEach
    void setUp() {
        User lara = new User();
        lara.setEmail(EMAIL);

        shift = new ScheduleItem();                       // Coffee shift, Friday 17:00-22:00 (5 h)
        shift.setUser(lara);
        shift.setTitle("Coffee shift");
        shift.setTimeType(TimeType.TIME_BLOCKED);
        shift.setStartTime(OffsetDateTime.parse("2026-10-23T17:00:00+03:00"));
        shift.setEndTime(OffsetDateTime.parse("2026-10-23T22:00:00+03:00"));

        when(items.findById(any())).thenReturn(Optional.of(shift));
    }

    private static ScheduleItemRequest moveTo(String start, String end) {
        ScheduleItemRequest request = new ScheduleItemRequest();
        request.setStartTime(OffsetDateTime.parse(start));
        request.setEndTime(OffsetDateTime.parse(end));
        request.setTimeType(TimeType.TIME_BLOCKED);
        return request;
    }

    @Test
    void weeklyTask_rescheduledTo8HoursAndMore_isRefused() {
        lenient().when(rules.findByScheduleItemId(any())).thenReturn(Optional.of(new RecurrenceRule()));

        // Friday 17:00 -> Saturday 01:00 = 8 h is fine; 17:00 -> 01:30 = 8 h 30 is not
        assertThrows(IllegalArgumentException.class, () -> service.updateItem(UUID.randomUUID(),
                moveTo("2026-10-23T17:00:00+03:00", "2026-10-24T01:30:00+03:00"), EMAIL));
        verify(items, never()).save(any());
    }

    @Test
    void weeklyTask_rescheduledOvernightWithin8Hours_isSaved() {
        lenient().when(rules.findByScheduleItemId(any())).thenReturn(Optional.of(new RecurrenceRule()));
        when(items.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // The tester's case: 21:15 -> 02:00 next day (4 h 45)
        assertTrue(service.updateItem(UUID.randomUUID(),
                moveTo("2026-10-23T21:15:00+03:00", "2026-10-24T02:00:00+03:00"), EMAIL).isPresent());
    }

    @Test
    void oneOffTask_canStillBeLongerThan8Hours() {
        lenient().when(rules.findByScheduleItemId(any())).thenReturn(Optional.empty());
        when(items.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Not repeating: a 10-hour one-off day is allowed
        assertTrue(service.updateItem(UUID.randomUUID(),
                moveTo("2026-10-23T08:00:00+03:00", "2026-10-23T18:00:00+03:00"), EMAIL).isPresent());
    }
}