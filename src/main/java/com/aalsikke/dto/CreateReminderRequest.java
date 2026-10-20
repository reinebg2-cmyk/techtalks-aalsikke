package com.aalsikke.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * scheduleItemId set = a reminder on that task.
 * KAN-381: scheduleItemId null = a reminder on its own, and then title is required.
 */
public record CreateReminderRequest(
        UUID scheduleItemId,
        OffsetDateTime remindAt,
        String title
) {
    /** Tells Jackson to always use this full constructor (the short one below is only for Java code). */
    @JsonCreator
    public CreateReminderRequest {}

    /** Task reminders, as before. */
    public CreateReminderRequest(UUID scheduleItemId, OffsetDateTime remindAt) {
        this(scheduleItemId, remindAt, null);
    }
}