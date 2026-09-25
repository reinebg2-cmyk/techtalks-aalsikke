package com.aalsikke.model;


import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "deadline_reminders", nullable = false)
    private boolean deadlineReminders = true;

    @Column(name = "overdue_alerts", nullable = false)
    private boolean overdueAlerts = true;

    @Column(name = "schedule_conflicts", nullable = false)
    private boolean scheduleConflicts = true;

    @Column(name = "weekly_review_ready", nullable = false)
    private boolean weeklyReviewReady = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public NotificationPreference() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public boolean isDeadlineReminders() { return deadlineReminders; }
    public void setDeadlineReminders(boolean deadlineReminders) { this.deadlineReminders = deadlineReminders; }

    public boolean isOverdueAlerts() { return overdueAlerts; }
    public void setOverdueAlerts(boolean overdueAlerts) { this.overdueAlerts = overdueAlerts; }

    public boolean isScheduleConflicts() { return scheduleConflicts; }
    public void setScheduleConflicts(boolean scheduleConflicts) { this.scheduleConflicts = scheduleConflicts; }

    public boolean isWeeklyReviewReady() { return weeklyReviewReady; }
    public void setWeeklyReviewReady(boolean weeklyReviewReady) { this.weeklyReviewReady = weeklyReviewReady; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
