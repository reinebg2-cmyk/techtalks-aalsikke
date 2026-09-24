package com.aalsikke.dto;

import com.aalsikke.model.DailyReview;

import java.time.LocalDate;
import java.util.UUID;

/** One day's review, without the nested user entity. */
public record DailyReviewEntry(UUID id, LocalDate reviewDate, Integer productivityScore, String notes) {

    public static DailyReviewEntry from(DailyReview review) {
        return new DailyReviewEntry(review.getId(), review.getReviewDate(),
                review.getProductivityScore(), review.getNotes());
    }
}