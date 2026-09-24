package com.aalsikke.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** PATCH /api/schedule-items/{id}/status body, as sent by the dashboard's done checkbox. */
public record StatusUpdateRequest(
        @NotNull(message = "Status is required.")
        @Pattern(regexp = "PENDING|COMPLETED", message = "Must be one of: PENDING, COMPLETED")
        String status
) {}