package com.aalsikke.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/daily-reviews/weekly (KAN-248, ADR-002): the daily reviews of a date range
 * aggregated on demand, plus the caller's task results for the same dates.
 *
 * Task counts use the item's due time (endTime, or deadline for deadline-only items):
 *  - completed: status COMPLETED
 *  - missed:    still PENDING and already due
 *  - upcoming:  still PENDING and due later in the range
 */
public record WeeklyReviewResponse(
        LocalDate start,
        LocalDate end,
        int daysReviewed,
        Double averageProductivityScore,   // null when no day in the range was reviewed
        List<DailyReviewEntry> reviews,    // sorted by date
        long completedTasks,
        long missedTasks,
        long upcomingTasks
) {}