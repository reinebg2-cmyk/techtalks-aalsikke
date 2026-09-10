package com.aalsikke.service;

import com.aalsikke.model.ScheduleConflict;
import com.aalsikke.repository.ScheduleConflictRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ScheduleConflictService {
    private final ScheduleConflictRepository scheduleConflictRepository;

    public ScheduleConflictService(ScheduleConflictRepository scheduleConflictRepository) {
        this.scheduleConflictRepository = scheduleConflictRepository;
    }

    public List<ScheduleConflict> getConflictsByUser(UUID userId) {
        return scheduleConflictRepository.findByUserId(userId);
    }

    public ScheduleConflict createConflict(ScheduleConflict conflict) {
        return scheduleConflictRepository.save(conflict);
    }
}