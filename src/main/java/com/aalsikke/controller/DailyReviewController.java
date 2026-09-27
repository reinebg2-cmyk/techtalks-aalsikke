package com.aalsikke.controller;

import com.aalsikke.dto.DailyReviewEntry;
import com.aalsikke.dto.WeeklyReviewResponse;
import com.aalsikke.model.DailyReview;
import com.aalsikke.service.DailyReviewService;
import com.aalsikke.dto.DailyReviewRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/daily-reviews")
public class DailyReviewController {
    private final DailyReviewService dailyReviewService;

    public DailyReviewController(DailyReviewService dailyReviewService) {
        this.dailyReviewService = dailyReviewService;
    }

    /** The caller's daily reviews between two dates (inclusive), sorted by date. */
    @GetMapping("/me")
    public List<DailyReviewEntry> getMyReviews(
            Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return dailyReviewService.getWeeklyReviewsForCurrentUser(authentication.getName(), start, end);
    }

    /**
     * KAN-248 / ADR-002: weekly review aggregated from the daily rows, plus completed / missed /
     * upcoming task counts for the same dates.
     * Example: GET /api/daily-reviews/weekly?start=2026-09-21&end=2026-09-27
     */
    @GetMapping("/weekly")
    public WeeklyReviewResponse getMyWeeklySummary(
            Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return dailyReviewService.getWeeklySummary(authentication.getName(), start, end);
    }

    @PostMapping
    public ResponseEntity<DailyReviewEntry> createReview(@Valid @RequestBody DailyReviewRequest request,
                                                         Authentication authentication) {
        return ResponseEntity.ok(dailyReviewService.createReview(request, authentication.getName()));
    }
}