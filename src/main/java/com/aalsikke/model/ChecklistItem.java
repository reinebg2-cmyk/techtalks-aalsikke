package com.aalsikke.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/** KAN-342: one step of a task's checklist. Ordered by position (0, 1, 2, ...). */
@Entity
@Table(name = "checklist_items")
public class ChecklistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_item_id", nullable = false)
    private ScheduleItem scheduleItem;

    @Column(nullable = false, length = 200)
    private String text;

    @Column(nullable = false)
    private boolean done = false;

    @Column(nullable = false)
    private int position;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    public ChecklistItem() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public ScheduleItem getScheduleItem() { return scheduleItem; }
    public void setScheduleItem(ScheduleItem scheduleItem) { this.scheduleItem = scheduleItem; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public boolean isDone() { return done; }
    public void setDone(boolean done) { this.done = done; }

    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}