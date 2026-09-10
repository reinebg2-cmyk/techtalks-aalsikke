package com.aalsikke.service;

import com.aalsikke.model.DailyReview;
import com.aalsikke.repository.DailyReviewRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class DailyReviewService {
    private final DailyReviewRepository dailyReviewRepository;

    public DailyReviewService(DailyReviewRepository dailyReviewRepository) {
        this.dailyReviewRepository = dailyReviewRepository;
    }

    public List<DailyReview> getWeeklyReviews(UUID userId, LocalDate start, LocalDate end) {
        return dailyReviewRepository.findByUserIdAndReviewDateBetween(userId, start, end);
    }

    public DailyReview createReview(DailyReview review) {
        return dailyReviewRepository.save(review);
    }
}