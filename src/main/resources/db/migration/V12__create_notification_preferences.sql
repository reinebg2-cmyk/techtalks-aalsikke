-- V12 (KAN-244): one row per user for the four notification toggles the
-- backend can actually detect today (DashboardService / ScheduleConflictService
-- / DailyReviewService). Own table, matching how courses/workplaces/recurrence
-- rules are already isolated rather than bloating the users table.

CREATE TABLE notification_preferences (
                                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                          user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
                                          deadline_reminders BOOLEAN NOT NULL DEFAULT TRUE,
                                          overdue_alerts BOOLEAN NOT NULL DEFAULT TRUE,
                                          schedule_conflicts BOOLEAN NOT NULL DEFAULT TRUE,
                                          weekly_review_ready BOOLEAN NOT NULL DEFAULT TRUE,
                                          created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                          updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
