package com.aalsikke.service;

import com.aalsikke.dto.ScheduleItemRequest;
import com.aalsikke.model.Course;
import com.aalsikke.model.ScheduleItem;
import com.aalsikke.model.User;
import com.aalsikke.model.Workplace;
import com.aalsikke.repository.CourseRepository;
import com.aalsikke.repository.ScheduleItemRepository;
import com.aalsikke.repository.UserRepository;
import com.aalsikke.repository.WorkplaceRepository;
import org.springframework.stereotype.Service;
import com.aalsikke.model.Priority;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ScheduleItemService {
    private final ScheduleItemRepository scheduleItemRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final WorkplaceRepository workplaceRepository;

    public ScheduleItemService(ScheduleItemRepository scheduleItemRepository,
                               UserRepository userRepository,
                               CourseRepository courseRepository,
                               WorkplaceRepository workplaceRepository) {
        this.scheduleItemRepository = scheduleItemRepository;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.workplaceRepository = workplaceRepository;
    }

    public List<ScheduleItem> getItemsForCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return scheduleItemRepository.findByUserId(user.getId());
    }

    public ScheduleItem createItem(ScheduleItemRequest request, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        ScheduleItem item = new ScheduleItem();
        item.setUser(user); // ALWAYS set from the authenticated caller — never trust a client-supplied user
        applyRequestToItem(item, request);
        return scheduleItemRepository.save(item);
    }

    public Optional<ScheduleItem> updateItem(UUID id, ScheduleItemRequest request, String email) {
        return scheduleItemRepository.findById(id)
                .filter(existing -> existing.getUser().getEmail().equals(email)) // ownership check
                .map(existing -> {
                    applyRequestToItem(existing, request);
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

    private void applyRequestToItem(ScheduleItem item, ScheduleItemRequest request) {
        item.setTitle(request.getTitle());
        item.setDescription(request.getDescription());
        item.setItemType(request.getItemType());
        item.setPriority(request.getPriority() != null ? request.getPriority() : Priority.MEDIUM);
        item.setStartTime(request.getStartTime());
        item.setEndTime(request.getEndTime());
        item.setRecurring(request.getIsRecurring() != null ? request.getIsRecurring() : false);

        if (request.getCourseId() != null) {
            Course course = courseRepository.findById(request.getCourseId())
                    .orElseThrow(() -> new IllegalArgumentException("Course not found."));
            item.setCourse(course);
        }

        if (request.getWorkplaceId() != null) {
            Workplace workplace = workplaceRepository.findById(request.getWorkplaceId())
                    .orElseThrow(() -> new IllegalArgumentException("Workplace not found."));
            item.setWorkplace(workplace);
        }
    }
}