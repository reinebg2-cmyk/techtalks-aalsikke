package com.aalsikke.service;

import com.aalsikke.model.DailyReview;
import com.aalsikke.model.User;
import com.aalsikke.repository.DailyReviewRepository;
import com.aalsikke.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class DailyReviewService {
    private final DailyReviewRepository dailyReviewRepository;
    private final UserRepository userRepository;

    public DailyReviewService(DailyReviewRepository dailyReviewRepository, UserRepository userRepository) {
        this.dailyReviewRepository = dailyReviewRepository;
        this.userRepository = userRepository;
    }

    public List<DailyReview> getWeeklyReviewsForCurrentUser(String email, LocalDate start, LocalDate end) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return dailyReviewRepository.findByUserIdAndReviewDateBetween(user.getId(), start, end);
    }

    public DailyReview createReview(DailyReview review, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        review.setUser(user);
        return dailyReviewRepository.save(review);
    }
}