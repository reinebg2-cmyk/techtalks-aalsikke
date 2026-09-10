package com.aalsikke.service;

import com.aalsikke.model.Workplace;
import com.aalsikke.repository.WorkplaceRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class WorkplaceService {
    private final WorkplaceRepository workplaceRepository;

    public WorkplaceService(WorkplaceRepository workplaceRepository) {
        this.workplaceRepository = workplaceRepository;
    }

    public List<Workplace> getWorkplacesByUser(UUID userId) {
        return workplaceRepository.findByUserId(userId);
    }

    public Workplace createWorkplace(Workplace workplace) {
        return workplaceRepository.save(workplace);
    }
}