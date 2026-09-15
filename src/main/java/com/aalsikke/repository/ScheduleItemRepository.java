package com.aalsikke.repository;

import com.aalsikke.model.ScheduleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ScheduleItemRepository extends JpaRepository<ScheduleItem, UUID> {

    List<ScheduleItem> findByUserIdAndStatusAndEndTimeBeforeOrderByEndTimeAsc(
            UUID userId, String status, OffsetDateTime now);

    List<ScheduleItem> findByUserIdAndEndTimeBetweenOrderByEndTimeAsc(
            UUID userId, OffsetDateTime start, OffsetDateTime end);

    List<ScheduleItem> findByUserId(UUID userId);

    long countByUserId(UUID userId);

    long countByUserIdAndStatus(UUID userId, String status);

    long countByUserIdAndStatusAndEndTimeBefore(UUID userId, String status, OffsetDateTime now);


}