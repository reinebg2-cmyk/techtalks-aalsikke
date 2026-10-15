package com.aalsikke.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.util.UUID;

/**
 * POST /api/recurrence-rules (KAN-248). Weekly only for now.
 * daysOfWeek: comma-separated MON..SUN, e.g. "MON,WED". Leave it out to repeat on the item's own weekday.
 */
public record RecurrenceRuleRequest(
        @NotNull(message = "scheduleItemId is required.")
        UUID scheduleItemId,

        @NotBlank(message = "frequency is required.")
        @Pattern(regexp = "WEEKLY", message = "Only WEEKLY repeats are supported for now.")
        String frequency,

        @Min(value = 1, message = "intervalValue must be at least 1.")
        @Max(value = 52, message = "intervalValue must be at most 52.")
        Integer intervalValue,

        @Pattern(regexp = "^(MON|TUE|WED|THU|FRI|SAT|SUN)(,(MON|TUE|WED|THU|FRI|SAT|SUN))*$",
                message = "daysOfWeek must look like MON,WED,FRI.")
        String daysOfWeek,

        LocalDate untilDate
) {}