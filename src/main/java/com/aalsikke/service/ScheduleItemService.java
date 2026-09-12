package com.aalsikke.service;

import com.aalsikke.model.ScheduleItem;
import com.aalsikke.model.User;
import com.aalsikke.repository.ScheduleItemRepository;
import com.aalsikke.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ScheduleItemService {
    private final ScheduleItemRepository scheduleItemRepository;
    private final UserRepository userRepository;

    public ScheduleItemService(ScheduleItemRepository scheduleItemRepository, UserRepository userRepository) {
        this.scheduleItemRepository = scheduleItemRepository;
        this.userRepository = userRepository;
    }

    public List<ScheduleItem> getItemsForCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return scheduleItemRepository.findByUserId(user.getId());
    }

    public ScheduleItem createItem(ScheduleItem item, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        item.setUser(user); // ALWAYS set from the authenticated caller — never trust a client-supplied user
        return scheduleItemRepository.save(item);
    }

    public Optional<ScheduleItem> updateItem(UUID id, ScheduleItem updatedItem, String email) {
        return scheduleItemRepository.findById(id)
                .filter(existing -> existing.getUser().getEmail().equals(email)) // ownership check
                .map(existing -> {
                    existing.setTitle(updatedItem.getTitle());
                    existing.setDescription(updatedItem.getDescription());
                    existing.setItemType(updatedItem.getItemType());
                    existing.setPriority(updatedItem.getPriority());
                    existing.setStartTime(updatedItem.getStartTime());
                    existing.setEndTime(updatedItem.getEndTime());
                    return scheduleItemRepository.save(existing);
                });
    }

    public boolean deleteItem(UUID id, String email) {
        return scheduleItemRepository.findById(id)
                .filter(existing -> existing.getUser().getEmail().equals(email)) // ownership check
                .map(existing -> {
                    scheduleItemRepository.deleteById(id);
                    return true;
                })
                .orElse(false);
    }
}