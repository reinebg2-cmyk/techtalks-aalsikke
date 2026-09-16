package com.aalsikke.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class ScheduleItemRequest {

    @NotBlank(message = "Title is required.")
    private String title;

    private String description;

    @NotBlank(message = "Item type is required.")
    private String itemType;

    private String priority;

    @NotNull(message = "Start time is required.")
    private OffsetDateTime startTime;

    @NotNull(message = "End time is required.")
    private OffsetDateTime endTime;

    private Boolean isRecurring;

    private UUID courseId;
    private UUID workplaceId;
}