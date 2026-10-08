package com.aalsikke.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotificationPreferencesRequest {

    @NotNull(message = "deadlineReminders is required.")
    private Boolean deadlineReminders;

    @NotNull(message = "overdueAlerts is required.")
    private Boolean overdueAlerts;

    @NotNull(message = "scheduleConflicts is required.")
    private Boolean scheduleConflicts;

    @NotNull(message = "weeklyReviewReady is required.")
    private Boolean weeklyReviewReady;

    /** KAN-345: optional, so older clients that don't send it keep working; null = keep current value. */
    private Boolean emailEnabled;
}
