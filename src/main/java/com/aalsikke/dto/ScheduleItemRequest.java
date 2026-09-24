package com.aalsikke.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.aalsikke.model.Priority;
import com.aalsikke.model.ItemType;
import jakarta.validation.constraints.Future;

@Getter
@Setter
public class ScheduleItemRequest {

    @NotBlank(message = "Title is required.")
    private String title;

    private String description;

    @NotNull(message = "Item type is required.")
    private ItemType itemType;

    private Priority priority;

    @NotNull(message = "Start time is required.")
    private OffsetDateTime startTime;

    @NotNull(message = "End time is required.")
    private OffsetDateTime endTime;

    @Future(message = "Deadline must be in the future.")
    private OffsetDateTime deadline;

    private Boolean isRecurring;

    private UUID courseId;
    private UUID workplaceId;
}