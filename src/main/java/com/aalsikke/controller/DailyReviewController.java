package com.aalsikke.controller;

import com.aalsikke.model.DailyReview;
import com.aalsikke.service.DailyReviewService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/daily-reviews")
public class DailyReviewController {
    private final DailyReviewService dailyReviewService;

    public DailyReviewController(DailyReviewService dailyReviewService) {
        this.dailyReviewService = dailyReviewService;
    }

    @GetMapping("/user/{userId}")
    public List<DailyReview> getWeeklyReviews(
            @PathVariable UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return dailyReviewService.getWeeklyReviews(userId, start, end);
    }

    @PostMapping
    public ResponseEntity<DailyReview> createReview(@RequestBody DailyReview review) {
        return ResponseEntity.ok(dailyReviewService.createReview(review));
    }
}