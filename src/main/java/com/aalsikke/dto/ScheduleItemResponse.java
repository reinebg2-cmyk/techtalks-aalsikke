package com.aalsikke.dto;

import com.aalsikke.model.ItemType;
import com.aalsikke.model.Priority;
import com.aalsikke.model.ScheduleItem;

import java.time.OffsetDateTime;
import java.util.UUID;

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
    private boolean isRecurring;
    private Summary course;
    private Summary workplace;

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
        dto.isRecurring = item.isRecurring();
        if (item.getCourse() != null) {
            dto.course = new Summary(item.getCourse().getId(), item.getCourse().getCourseName());
        }
        if (item.getWorkplace() != null) {
            dto.workplace = new Summary(item.getWorkplace().getId(), item.getWorkplace().getCompanyName());
        }
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
    public boolean isRecurring() { return isRecurring; }
    public Summary getCourse() { return course; }
    public Summary getWorkplace() { return workplace; }
}