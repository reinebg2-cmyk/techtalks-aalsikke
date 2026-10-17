package com.aalsikke.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** KAN-363: one completed weekly repeat of a recurring item (the item + the repeat's local date). */
@Entity
@Table(name = "occurrence_completions")
public class OccurrenceCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_item_id", nullable = false)
    private ScheduleItem scheduleItem;

    @Column(name = "occurrence_date", nullable = false)
    private LocalDate occurrenceDate;

    @CreationTimestamp
    @Column(name = "completed_at", updatable = false)
    private OffsetDateTime completedAt;

    public OccurrenceCompletion() {}

    public OccurrenceCompletion(ScheduleItem scheduleItem, LocalDate occurrenceDate) {
        this.scheduleItem = scheduleItem;
        this.occurrenceDate = occurrenceDate;
    }

    public UUID getId() { return id; }
    public ScheduleItem getScheduleItem() { return scheduleItem; }
    public LocalDate getOccurrenceDate() { return occurrenceDate; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
}