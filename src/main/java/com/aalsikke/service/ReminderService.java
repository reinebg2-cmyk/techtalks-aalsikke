package com.aalsikke.service;

import com.aalsikke.dto.CreateReminderRequest;
import com.aalsikke.dto.ReminderResponseDTO;
import com.aalsikke.model.Reminder;
import com.aalsikke.repository.ReminderRepository;
import com.aalsikke.repository.ScheduleItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.aalsikke.model.ScheduleItem;
import com.aalsikke.dto.UpdateReminderRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Duration;

@Service
public class ReminderService {
    private final ReminderRepository reminderRepository;
    private final ScheduleItemRepository scheduleItemRepository;
    private static final Duration MIN_REMINDER_GAP = Duration.ofMinutes(10);

    public ReminderService(ReminderRepository reminderRepository, ScheduleItemRepository scheduleItemRepository) {
        this.reminderRepository = reminderRepository;
        this.scheduleItemRepository = scheduleItemRepository;
    }

    /**
     * Builds a brand-new Reminder from the DTO only: the client can no longer
     * send id / triggered / createdAt. Empty = item missing or not owned by caller.
     */
    @Transactional
    public Optional<ReminderResponseDTO> createReminder(CreateReminderRequest request, String email) {
        return scheduleItemRepository.findById(request.scheduleItemId())
                .filter(item -> item.getUser().getEmail().equals(email))
                .map(item -> {
                    validateWithinTimeline(item, request.remindAt());
                    validateMinGap(request.remindAt(), reminderRepository.findByScheduleItemId(item.getId()));
                    Reminder reminder = new Reminder();
                    reminder.setScheduleItem(item);
                    reminder.setRemindAt(request.remindAt());
                    return toResponse(reminderRepository.save(reminder));
                });
    }
    /** Change when a reminder fires. Empty = reminder missing or not owned by the caller. */
    @Transactional
    public Optional<ReminderResponseDTO> updateReminder(UUID id, UpdateReminderRequest request, String email) {
        return reminderRepository.findByIdAndOwner(id, email)
                .map(reminder -> {
                    ScheduleItem item = reminder.getScheduleItem();
                    validateWithinTimeline(item, request.remindAt());
                    // The reminder being edited must not clash with itself
                    List<Reminder> others = reminderRepository.findByScheduleItemId(item.getId()).stream()
                            .filter(other -> !other.getId().equals(reminder.getId()))
                            .toList();
                    validateMinGap(request.remindAt(), others);
                    reminder.setRemindAt(request.remindAt());
                    reminder.setTriggered(false);   // a new time means it should fire again
                    reminder.setEmailedAt(null);    // KAN-336: ...and be emailed again at the new time
                    return toResponse(reminderRepository.save(reminder));
                });
    }
    /** KAN-346: postpone a reminder. Same rules as editing it (task window, 10-minute gap). */
    @Transactional
    public Optional<ReminderResponseDTO> snoozeReminder(UUID id, int minutes, String email) {
        return updateReminder(id, new UpdateReminderRequest(OffsetDateTime.now().plus(Duration.ofMinutes(minutes))), email);
    }

    @Transactional
    public boolean deleteReminder(UUID id, String email) {
        return reminderRepository.findByIdAndOwner(id, email)
                .map(reminder -> {
                    reminderRepository.delete(reminder);
                    return true;
                })
                .orElse(false);
    }

    /** KAN-236: the bell/panel calls this on app open. */
    @Transactional(readOnly = true)
    public List<ReminderResponseDTO> getDueRemindersForCurrentUser(String email) {
        return reminderRepository.findDueForUser(email, OffsetDateTime.now())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public boolean markTriggered(UUID id, String email) {
        return reminderRepository.findByIdAndOwner(id, email)
                .map(reminder -> {
                    reminder.setTriggered(true);
                    reminderRepository.save(reminder);
                    return true;
                })
                .orElse(false);
    }
    /**
     * KAN-299: a reminder must fall inside the task's window.
     * Window start = startTime, or createdAt for deadline-only tasks (no startTime).
     * Window end = deadline, or endTime when there is no deadline.
     * If the task has neither (start-only, no deadline) there is no upper limit.
     */
    private static void validateWithinTimeline(ScheduleItem item, OffsetDateTime remindAt) {
        if (remindAt == null) {
            throw new IllegalArgumentException("remindAt is required.");
        }
        OffsetDateTime from = item.getStartTime() != null ? item.getStartTime() : item.getCreatedAt();
        OffsetDateTime to = item.getDeadline() != null ? item.getDeadline() : item.getEndTime();

        if (from != null && remindAt.isBefore(from)) {
            throw new IllegalArgumentException("A reminder cannot be set before the task's start time.");
        }
        // KAN-370: a repeating task's reminders move to later weeks, so only the first date's start limits them
        if (to != null && remindAt.isAfter(to) && !item.isRecurring()) {
            throw new IllegalArgumentException("A reminder cannot be set after the task's deadline.");
        }
    }
    /** KAN-298: at least 10 minutes between any two reminders on the same task. */
    static void validateMinGap(OffsetDateTime remindAt, List<Reminder> existing) {
        for (Reminder other : existing) {
            Duration gap = Duration.between(other.getRemindAt(), remindAt).abs();
            if (gap.compareTo(MIN_REMINDER_GAP) < 0) {
                throw new IllegalArgumentException("Reminders on the same task must be at least 10 minutes apart.");
            }
        }
    }

    private ReminderResponseDTO toResponse(Reminder reminder) {
        return new ReminderResponseDTO(
                reminder.getId(),
                reminder.getScheduleItem().getId(),
                reminder.getScheduleItem().getTitle(),
                reminder.getRemindAt(),
                reminder.isTriggered()
        );
    }
}