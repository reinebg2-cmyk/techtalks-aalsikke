package com.aalsikke.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "reminders")
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_item_id", nullable = false)
    @JsonIgnoreProperties({"reminders", "hibernateLazyInitializer", "handler"})
    private ScheduleItem scheduleItem;

    @Column(name = "remind_at", nullable = false)
    private OffsetDateTime remindAt;

    @Column(name = "is_triggered")
    private boolean isTriggered = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    public Reminder() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public ScheduleItem getScheduleItem() { return scheduleItem; }
    public void setScheduleItem(ScheduleItem scheduleItem) { this.scheduleItem = scheduleItem; }

    public OffsetDateTime getRemindAt() { return remindAt; }
    public void setRemindAt(OffsetDateTime remindAt) { this.remindAt = remindAt; }

    public boolean isTriggered() { return isTriggered; }
    public void setTriggered(boolean triggered) { isTriggered = triggered; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}