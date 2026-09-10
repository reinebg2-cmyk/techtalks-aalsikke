package com.aalsikke.service;

import com.aalsikke.model.ScheduleItem;
import com.aalsikke.repository.ScheduleItemRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
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

    public Optional<ScheduleItem> updateItem(UUID id, ScheduleItem updatedItem) {
        return scheduleItemRepository.findById(id).map(existing -> {
            existing.setTitle(updatedItem.getTitle());
            existing.setDescription(updatedItem.getDescription());
            existing.setItemType(updatedItem.getItemType());
            existing.setPriority(updatedItem.getPriority());
            existing.setStartTime(updatedItem.getStartTime());
            existing.setEndTime(updatedItem.getEndTime());
            return scheduleItemRepository.save(existing);
        });
    }

    public boolean deleteItem(UUID id) {
        if (scheduleItemRepository.existsById(id)) {
            scheduleItemRepository.deleteById(id);
            return true;
        }
        return false;
    }
}