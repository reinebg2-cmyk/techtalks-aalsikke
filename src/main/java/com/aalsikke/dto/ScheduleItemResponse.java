package com.aalsikke.dto;

import com.aalsikke.model.ItemType;
import com.aalsikke.model.Priority;
import com.aalsikke.model.ScheduleItem;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;
import com.aalsikke.model.TimeType;

public class ScheduleItemResponse {

    private UUID id;
    private String title;
    private String description;
    private ItemType itemType;
    private Priority priority;
    private String status;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private OffsetDateTime deadline;
    private TimeType timeType;
    /** KAN-270: when the task was added. Reminder start for deadline-only tasks. */
    private OffsetDateTime createdAt;
    private boolean isRecurring;
    private Summary course;
    private Summary workplace;
    private List<ConflictSummary> conflicts = List.of();
    private boolean occurrence;

    public static ScheduleItemResponse from(ScheduleItem item) {
        ScheduleItemResponse dto = new ScheduleItemResponse();
        dto.id = item.getId();
        dto.title = item.getTitle();
        dto.description = item.getDescription();
        dto.itemType = item.getItemType();
        dto.priority = item.getPriority();
        dto.status = item.getStatus();
        dto.startTime = item.getStartTime();
        dto.endTime = item.getEndTime();
        dto.deadline = item.getDeadline();
        dto.timeType = item.getTimeType();
        dto.createdAt = item.getCreatedAt();
        dto.isRecurring = item.isRecurring();
        if (item.getCourse() != null) {
            dto.course = new Summary(item.getCourse().getId(), item.getCourse().getCourseName());
        }
        if (item.getWorkplace() != null) {
            dto.workplace = new Summary(item.getWorkplace().getId(), item.getWorkplace().getCompanyName());
        }
        return dto;
    }

    /** Same as from(item), plus the items it clashes with (S3-07). */
    public static ScheduleItemResponse from(ScheduleItem item, List<ScheduleItem> clashingItems) {
        ScheduleItemResponse dto = from(item);
        dto.conflicts = clashingItems.stream().map(ConflictSummary::from).toList();
        return dto;
    }


    /**
     * KAN-248: a generated weekly repeat of a recurring item. Same id and details as the item,
     * its own start/end, no deadline (the deadline marker belongs to the real item only), and
     * "occurrence": true so the calendar can show it as a read-only repeat.
     */
    public static ScheduleItemResponse occurrenceOf(ScheduleItem item, OffsetDateTime start, OffsetDateTime end) {
        ScheduleItemResponse dto = from(item);
        dto.startTime = start.withOffsetSameInstant(java.time.ZoneOffset.UTC);
        dto.endTime = end.withOffsetSameInstant(java.time.ZoneOffset.UTC);
        dto.deadline = null;
        dto.occurrence = true;
        return dto;
    }

    public static class Summary {
        private final UUID id;
        private final String name;

        public Summary(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        public UUID getId() { return id; }
        public String getName() { return name; }
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public ItemType getItemType() { return itemType; }
    public Priority getPriority() { return priority; }
    public String getStatus() { return status; }
    public OffsetDateTime getStartTime() { return startTime; }
    public OffsetDateTime getEndTime() { return endTime; }
    public OffsetDateTime getDeadline() { return deadline; }
    public TimeType getTimeType() { return timeType; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public boolean isRecurring() { return isRecurring; }
    public Summary getCourse() { return course; }
    public Summary getWorkplace() { return workplace; }
    public List<ConflictSummary> getConflicts() { return conflicts; }
    public boolean isOccurrence() { return occurrence; }

}