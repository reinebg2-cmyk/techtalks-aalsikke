package com.aalsikke.dto;

public class NotificationPreferencesResponse {

    private boolean deadlineReminders;
    private boolean overdueAlerts;
    private boolean scheduleConflicts;
    private boolean weeklyReviewReady;

    public NotificationPreferencesResponse(boolean deadlineReminders, boolean overdueAlerts,
                                           boolean scheduleConflicts, boolean weeklyReviewReady) {
        this.deadlineReminders = deadlineReminders;
        this.overdueAlerts = overdueAlerts;
        this.scheduleConflicts = scheduleConflicts;
        this.weeklyReviewReady = weeklyReviewReady;
    }

    public boolean isDeadlineReminders() { return deadlineReminders; }
    public boolean isOverdueAlerts() { return overdueAlerts; }
    public boolean isScheduleConflicts() { return scheduleConflicts; }
    public boolean isWeeklyReviewReady() { return weeklyReviewReady; }
}
