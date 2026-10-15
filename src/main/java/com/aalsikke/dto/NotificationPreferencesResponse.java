package com.aalsikke.dto;

public class NotificationPreferencesResponse {

    private boolean deadlineReminders;
    private boolean overdueAlerts;
    private boolean scheduleConflicts;
    private boolean weeklyReviewReady;
    private boolean emailEnabled;

    public NotificationPreferencesResponse(boolean deadlineReminders, boolean overdueAlerts,
                                           boolean scheduleConflicts, boolean weeklyReviewReady,
                                           boolean emailEnabled) {
        this.deadlineReminders = deadlineReminders;
        this.overdueAlerts = overdueAlerts;
        this.scheduleConflicts = scheduleConflicts;
        this.weeklyReviewReady = weeklyReviewReady;
        this.emailEnabled = emailEnabled;
    }

    public boolean isDeadlineReminders() { return deadlineReminders; }
    public boolean isOverdueAlerts() { return overdueAlerts; }
    public boolean isScheduleConflicts() { return scheduleConflicts; }
    public boolean isWeeklyReviewReady() { return weeklyReviewReady; }
    public boolean isEmailEnabled() { return emailEnabled; }
}
