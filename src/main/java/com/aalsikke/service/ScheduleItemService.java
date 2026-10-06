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
import com.aalsikke.model.RecurrenceRule;
import com.aalsikke.repository.RecurrenceRuleRepository;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.time.Duration;
import java.time.OffsetDateTime;
import com.aalsikke.model.TimeType;

@Service
public class ScheduleItemService {
    private static final Duration MAX_CALENDAR_RANGE = Duration.ofDays(62);
    private final ScheduleItemRepository scheduleItemRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final WorkplaceRepository workplaceRepository;
    private final RecurrenceRuleRepository recurrenceRuleRepository;

    public ScheduleItemService(ScheduleItemRepository scheduleItemRepository,
                               UserRepository userRepository,
                               CourseRepository courseRepository,
                               WorkplaceRepository workplaceRepository,
                               RecurrenceRuleRepository recurrenceRuleRepository) {
        this.scheduleItemRepository = scheduleItemRepository;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.workplaceRepository = workplaceRepository;
        this.recurrenceRuleRepository = recurrenceRuleRepository;
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

        List<ScheduleItemResponse> result = new ArrayList<>(
                scheduleItemRepository.findInRange(user.getId(), from, to)
                        .stream()
                        .map(ScheduleItemResponse::from)
                        .toList());

        // KAN-248: add the weekly repeats of recurring items that fall in the range
        ZoneId zone = ZoneId.systemDefault();   // same convention as the dashboard
        for (RecurrenceRule rule : recurrenceRuleRepository.findActiveWeeklyForUser(
                user.getId(), to, from.atZoneSameInstant(zone).toLocalDate())) {
            ScheduleItem base = rule.getScheduleItem();
            int interval = rule.getIntervalValue() != null ? rule.getIntervalValue() : 1;
            for (RecurrenceExpander.Occurrence occurrence : RecurrenceExpander.weekly(
                    base.getStartTime(), base.getEndTime(), interval,
                    RecurrenceExpander.parseDays(rule.getDaysOfWeek()), rule.getUntilDate(),
                    zone, from, to)) {
                result.add(ScheduleItemResponse.occurrenceOf(base, occurrence.start(), occurrence.end()));
            }
        }

        result.sort(Comparator.comparing(
                (ScheduleItemResponse r) -> r.getStartTime() != null ? r.getStartTime() : r.getDeadline()));
        return result;
    }

    public ScheduleItemResponse createItem(ScheduleItemRequest request, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        ScheduleItem item = new ScheduleItem();
        item.setUser(user); // ALWAYS set from the authenticated caller — never trust a client-supplied user
        applyRequestToItem(item, request);
        item.setTimeType(request.getTimeType() != null ? request.getTimeType() : inferTimeType(item));
        validateTimes(item);
        ScheduleItem saved = scheduleItemRepository.save(item);
        return ScheduleItemResponse.from(saved, findConflictsFor(saved));
    }

    public Optional<ScheduleItemResponse> updateItem(UUID id, ScheduleItemRequest request, String email) {
        return scheduleItemRepository.findById(id)
                .filter(existing -> existing.getUser().getEmail().equals(email))
                .map(existing -> {
                    applyPartialUpdateToItem(existing, request);
                    validateTimes(existing);
                    ScheduleItem saved = scheduleItemRepository.save(existing);
                    return ScheduleItemResponse.from(saved, findConflictsFor(saved));
                });
    }

    private void applyPartialUpdateToItem(ScheduleItem item, ScheduleItemRequest request) {
        if (request.getTitle() != null) item.setTitle(request.getTitle());
        if (request.getDescription() != null) item.setDescription(request.getDescription());
        if (request.getItemType() != null) item.setItemType(request.getItemType());
        if (request.getPriority() != null) item.setPriority(request.getPriority());
        if (Boolean.TRUE.equals(request.getClearTimeSlot())) {
            // KAN-270: turn a time-window task into a deadline-only task
            item.setStartTime(null);
            item.setEndTime(null);
        } else {
            if (request.getStartTime() != null) item.setStartTime(request.getStartTime());
            if (request.getEndTime() != null) item.setEndTime(request.getEndTime());
        }
        if (request.getTimeType() == TimeType.START_ONLY) {
            item.setEndTime(null);   // a start-only item has no end time
        }
        if (request.getDeadline() != null) item.setDeadline(request.getDeadline());
        if (request.getIsRecurring() != null) item.setRecurring(request.getIsRecurring());
        if (request.getReminderEnabled() != null) item.setReminderEnabled(request.getReminderEnabled());
        boolean timesTouched = Boolean.TRUE.equals(request.getClearTimeSlot())
                || request.getStartTime() != null || request.getEndTime() != null;
        if (request.getTimeType() != null) {
            item.setTimeType(request.getTimeType());
        } else if (timesTouched || item.getTimeType() == null) {
            item.setTimeType(inferTimeType(item));
        }
        applyCourseAndWorkplace(item, request);
    }

    /**
     * Conflicts for the saved item, using ConflictRules (TIME_BLOCKED, START_ONLY and
     * DEADLINE_ONLY). Never blocks the save; the result is only returned as a warning.
     * DEADLINE_ONLY items never conflict.
     */
    private List<ScheduleItem> findConflictsFor(ScheduleItem item) {
        if (item.getTimeType() == TimeType.DEADLINE_ONLY || item.getStartTime() == null) {
            return List.of();
        }
        OffsetDateTime windowEnd = item.getEndTime() != null ? item.getEndTime() : item.getStartTime();
        List<ScheduleItem> candidates = scheduleItemRepository.findPossibleConflicts(
                item.getUser().getId(), item.getId(), item.getStartTime(), windowEnd);
        return ConflictRules.findConflicts(item, candidates);
    }
    /** Mark one of the caller's items PENDING or COMPLETED (dashboard checkbox, weekly review). */
    public Optional<ScheduleItemResponse> updateStatus(UUID id, String status, String email) {
        return scheduleItemRepository.findById(id)
                .filter(existing -> existing.getUser().getEmail().equals(email))
                .map(existing -> {
                    existing.setStatus(status);
                    ScheduleItem saved = scheduleItemRepository.save(existing);
                    return ScheduleItemResponse.from(saved, findConflictsFor(saved));
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
        item.setDeadline(request.getDeadline());
        item.setRecurring(Boolean.TRUE.equals(request.getIsRecurring()));
        item.setReminderEnabled(Boolean.TRUE.equals(request.getReminderEnabled()));

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

    /**
     * V11 rules, checked on the FINAL state of the item (after a partial update is merged),
     * so the user gets a clear 400 instead of a database constraint error:
     *  - start and end are set together or not at all,
     *  - end is after start,
     *  - the item has a time slot or a deadline.
     */
    private static TimeType inferTimeType(ScheduleItem item) {
        boolean hasStart = item.getStartTime() != null;
        boolean hasEnd = item.getEndTime() != null;
        if (hasStart && hasEnd) return TimeType.TIME_BLOCKED;
        if (hasStart) return TimeType.START_ONLY;
        return TimeType.DEADLINE_ONLY;
    }

    /** Per-type rules (KAN-317), checked on the final state of the item. */
    private static void validateTimes(ScheduleItem item) {
        OffsetDateTime start = item.getStartTime();
        OffsetDateTime end = item.getEndTime();
        switch (item.getTimeType()) {
            case DEADLINE_ONLY -> {
                if (start != null || end != null)
                    throw new IllegalArgumentException("A DEADLINE_ONLY item cannot have startTime or endTime.");
                if (item.getDeadline() == null)
                    throw new IllegalArgumentException("A DEADLINE_ONLY item needs a deadline.");
            }
            case TIME_BLOCKED -> {
                if (start == null || end == null)
                    throw new IllegalArgumentException("A TIME_BLOCKED item needs both startTime and endTime.");
                if (!end.isAfter(start))
                    throw new IllegalArgumentException("endTime must be after startTime.");
            }
            case START_ONLY -> {
                if (start == null)
                    throw new IllegalArgumentException("A START_ONLY item needs a startTime.");
                if (end != null)
                    throw new IllegalArgumentException("A START_ONLY item cannot have an endTime.");
            }
        }
    }
}
