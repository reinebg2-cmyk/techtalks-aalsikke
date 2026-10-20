package com.aalsikke.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.time.OffsetDateTime;

/** KAN-381: title is optional. It renames a reminder without a task; null keeps the old title. */
public record UpdateReminderRequest(
        OffsetDateTime remindAt,
        String title
) {
    @JsonCreator
    public UpdateReminderRequest {}

    /** Snooze and older callers: only move the time. */
    public UpdateReminderRequest(OffsetDateTime remindAt) {
        this(remindAt, null);
    }
}