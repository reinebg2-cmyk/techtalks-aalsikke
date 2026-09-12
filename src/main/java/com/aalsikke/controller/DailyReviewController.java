package com.aalsikke.controller;

import com.aalsikke.model.DailyReview;
import com.aalsikke.service.DailyReviewService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/daily-reviews")
public class DailyReviewController {
    private final DailyReviewService dailyReviewService;

    public DailyReviewController(DailyReviewService dailyReviewService) {
        this.dailyReviewService = dailyReviewService;
    }

    @GetMapping("/me")
    public List<DailyReview> getMyWeeklyReviews(
            Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return dailyReviewService.getWeeklyReviewsForCurrentUser(authentication.getName(), start, end);
    }

    @PostMapping
    public ResponseEntity<DailyReview> createReview(@RequestBody DailyReview review, Authentication authentication) {
        return ResponseEntity.ok(dailyReviewService.createReview(review, authentication.getName()));
    }
}