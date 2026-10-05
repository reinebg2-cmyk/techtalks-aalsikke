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
import com.aalsikke.model.TimeType;

@Getter
@Setter
public class ScheduleItemRequest {

    @NotBlank(message = "Title is required.")
    private String title;

    private String description;

    @NotNull(message = "Item type is required.")
    private ItemType itemType;

    private Priority priority;


    private OffsetDateTime startTime;


    private OffsetDateTime endTime;

    @Future(message = "Deadline must be in the future.")
    private OffsetDateTime deadline;

    private TimeType timeType;

    public TimeType getTimeType() { return timeType; }
    public void setTimeType(TimeType timeType) { this.timeType = timeType; }

    private Boolean isRecurring;
    private Boolean reminderEnabled;

    /**
     * KAN-270: on update, true removes the time window (startTime/endTime) so the
     * task becomes deadline-only. Needed because null means "keep" in a partial update.
     */
    private Boolean clearTimeSlot;

    private UUID courseId;
    private UUID workplaceId;
}