package com.aalsikke.repository;

import com.aalsikke.model.ScheduleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ScheduleItemRepository extends JpaRepository<ScheduleItem, UUID> {
    List<ScheduleItem> findByUserId(UUID userId);
}