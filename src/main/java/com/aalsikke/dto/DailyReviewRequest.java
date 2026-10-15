package com.aalsikke.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * POST /api/daily-reviews body. Deliberately has no id or user field: a review is always
 * created new for the caller, so a client can never overwrite someone else's review.
 */
public record DailyReviewRequest(
        @NotNull(message = "reviewDate is required.")
        LocalDate reviewDate,

        @Min(value = 1, message = "productivityScore must be between 1 and 5.")
        @Max(value = 5, message = "productivityScore must be between 1 and 5.")
        Integer productivityScore,

        @Size(max = 2000, message = "notes can be at most 2000 characters.")
        String notes
) {}