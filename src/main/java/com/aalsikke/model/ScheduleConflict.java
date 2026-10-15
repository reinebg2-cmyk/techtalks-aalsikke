package com.aalsikke.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "schedule_conflicts")
public class ScheduleConflict {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_one_id", nullable = false)
    private ScheduleItem itemOne;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_two_id", nullable = false)
    private ScheduleItem itemTwo;

    @Column(name = "conflict_type", length = 50)
    private String conflictType;

    @Column(name = "is_resolved")
    private boolean isResolved = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    public ScheduleConflict() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public ScheduleItem getItemOne() { return itemOne; }
    public void setItemOne(ScheduleItem itemOne) { this.itemOne = itemOne; }

    public ScheduleItem getItemTwo() { return itemTwo; }
    public void setItemTwo(ScheduleItem itemTwo) { this.itemTwo = itemTwo; }

    public String getConflictType() { return conflictType; }
    public void setConflictType(String conflictType) { this.conflictType = conflictType; }

    public boolean isResolved() { return isResolved; }
    public void setResolved(boolean resolved) { isResolved = resolved; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}