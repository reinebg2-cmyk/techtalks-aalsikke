package com.aalsikke.repository;

import com.aalsikke.model.DailyReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface DailyReviewRepository extends JpaRepository<DailyReview, UUID> {
    List<DailyReview> findByUserIdAndReviewDateBetween(UUID userId, LocalDate start, LocalDate end);
}