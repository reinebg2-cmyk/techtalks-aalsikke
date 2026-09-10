package com.aalsikke.repository;

import com.aalsikke.model.ScheduleConflict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ScheduleConflictRepository extends JpaRepository<ScheduleConflict, UUID> {
    List<ScheduleConflict> findByUserId(UUID userId);
}