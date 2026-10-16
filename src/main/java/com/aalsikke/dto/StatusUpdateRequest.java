package com.aalsikke.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** PATCH /api/schedule-items/{id}/status body, as sent by the dashboard's done checkbox. */
public record StatusUpdateRequest(
        @NotNull(message = "Status is required.")
        @Pattern(regexp = "PENDING|COMPLETED", message = "Must be one of: PENDING, COMPLETED")
        String status,
        // KAN-363: set when one weekly repeat is marked, e.g. "2026-10-23"; absent for the item itself
        java.time.LocalDate occurrenceDate
) {}