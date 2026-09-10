package com.aalsikke.service;

import com.aalsikke.model.ScheduleItem;
import com.aalsikke.repository.ScheduleItemRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ScheduleItemService {
    private final ScheduleItemRepository scheduleItemRepository;

    public ScheduleItemService(ScheduleItemRepository scheduleItemRepository) {
        this.scheduleItemRepository = scheduleItemRepository;
    }

    public List<ScheduleItem> getItemsByUser(UUID userId) {
        return scheduleItemRepository.findByUserId(userId);
    }

    public ScheduleItem createItem(ScheduleItem item) {
        return scheduleItemRepository.save(item);
    }
}