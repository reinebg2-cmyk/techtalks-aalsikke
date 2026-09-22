package com.aalsikke.service;

import com.aalsikke.dto.ScheduleItemRequest;
import com.aalsikke.dto.ScheduleItemResponse;
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
import java.util.stream.Collectors;
import java.time.Duration;
import java.time.OffsetDateTime;

@Service
public class ScheduleItemService {
    private static final Duration MAX_CALENDAR_RANGE = Duration.ofDays(62);
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

    public List<ScheduleItemResponse> getItemsForCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return scheduleItemRepository.findByUserId(user.getId())
                .stream()
                .map(ScheduleItemResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Calendar range (S3-02): the caller's items overlapping [from, to), plus items whose
     * deadline falls in that range. Identity always comes from the JWT (email), never the request.
     */
    public List<ScheduleItemResponse> getItemsInRange(String email, OffsetDateTime from, OffsetDateTime to) {
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("'from' must be before 'to'.");
        }
        if (Duration.between(from, to).compareTo(MAX_CALENDAR_RANGE) > 0) {
            throw new IllegalArgumentException("The range cannot be longer than 62 days.");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return scheduleItemRepository.findInRange(user.getId(), from, to)
                .stream()
                .map(ScheduleItemResponse::from)
                .collect(Collectors.toList());
    }

    public ScheduleItemResponse createItem(ScheduleItemRequest request, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        ScheduleItem item = new ScheduleItem();
        item.setUser(user); // ALWAYS set from the authenticated caller — never trust a client-supplied user
        applyRequestToItem(item, request);
        ScheduleItem saved = scheduleItemRepository.save(item);
        return ScheduleItemResponse.from(saved, findConflictsFor(saved));
    }

    public Optional<ScheduleItemResponse> updateItem(UUID id, ScheduleItemRequest request, String email) {
        return scheduleItemRepository.findById(id)
                .filter(existing -> existing.getUser().getEmail().equals(email))
                .map(existing -> {
                    applyPartialUpdateToItem(existing, request);
                    ScheduleItem saved = scheduleItemRepository.save(existing);
                    return ScheduleItemResponse.from(saved, findConflictsFor(saved));
                });
    }

    private void applyPartialUpdateToItem(ScheduleItem item, ScheduleItemRequest request) {
        if (request.getTitle() != null) item.setTitle(request.getTitle());
        if (request.getDescription() != null) item.setDescription(request.getDescription());
        if (request.getItemType() != null) item.setItemType(request.getItemType());
        if (request.getPriority() != null) item.setPriority(request.getPriority());
        if (request.getStartTime() != null) item.setStartTime(request.getStartTime());
        if (request.getEndTime() != null) item.setEndTime(request.getEndTime());
        if (request.getDeadline() != null) item.setDeadline(request.getDeadline());
        if (request.getIsRecurring() != null) item.setRecurring(request.getIsRecurring());

        applyCourseAndWorkplace(item, request);
    }

    /**
     * S3-07: the caller's other items whose time slot overlaps this one.
     * Never blocks the save; the result is only returned as a warning.
     * Items without both start and end times (deadline-only) never conflict.
     */
    private List<ScheduleItem> findConflictsFor(ScheduleItem item) {
        if (!ConflictRules.hasTimeSlot(item)) {
            return List.of();
        }
        List<ScheduleItem> candidates = scheduleItemRepository.findOverlappingTimedItems(
                item.getUser().getId(), item.getId(), item.getStartTime(), item.getEndTime());
        return ConflictRules.findConflicts(item, candidates);
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
        item.setDeadline(request.getDeadline());
        item.setRecurring(request.getIsRecurring() != null ? request.getIsRecurring() : false);

        applyCourseAndWorkplace(item, request);
    }

    /**
     * Links a course/workplace only if it belongs to the item's owner.
     * A foreign or unknown id gives the same "not found" message, so the API
     * never reveals whether another user's course exists.
     */
    private void applyCourseAndWorkplace(ScheduleItem item, ScheduleItemRequest request) {
        UUID ownerId = item.getUser().getId();

        if (request.getCourseId() != null) {
            Course course = courseRepository.findByIdAndUserId(request.getCourseId(), ownerId)
                    .orElseThrow(() -> new IllegalArgumentException("Course not found."));
            item.setCourse(course);
        }

        if (request.getWorkplaceId() != null) {
            Workplace workplace = workplaceRepository.findByIdAndUserId(request.getWorkplaceId(), ownerId)
                    .orElseThrow(() -> new IllegalArgumentException("Workplace not found."));
            item.setWorkplace(workplace);
        }
    }
}