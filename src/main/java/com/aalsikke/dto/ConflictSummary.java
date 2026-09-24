package com.aalsikke.dto;

import com.aalsikke.model.ScheduleItem;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One clashing item, as shown in a conflict warning (S3-07). */
public record ConflictSummary(UUID id, String title, OffsetDateTime startTime, OffsetDateTime endTime) {

    public static ConflictSummary from(ScheduleItem item) {
        return new ConflictSummary(item.getId(), item.getTitle(), item.getStartTime(), item.getEndTime());
    }
}