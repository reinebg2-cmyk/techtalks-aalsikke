package com.aalsikke.dto;

import com.aalsikke.model.RecurrenceRule;

import java.time.LocalDate;
import java.util.UUID;

/** A recurrence rule without the nested schedule item / user entities. */
public record RecurrenceRuleResponse(
        UUID id,
        UUID scheduleItemId,
        String scheduleItemTitle,
        String frequency,
        Integer intervalValue,
        String daysOfWeek,
        LocalDate untilDate
) {
    public static RecurrenceRuleResponse from(RecurrenceRule rule) {
        return new RecurrenceRuleResponse(
                rule.getId(),
                rule.getScheduleItem().getId(),
                rule.getScheduleItem().getTitle(),
                rule.getFrequency(),
                rule.getIntervalValue(),
                rule.getDaysOfWeek(),
                rule.getUntilDate());
    }
}