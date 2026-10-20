package com.aalsikke.service;

import com.aalsikke.dto.CreateReminderRequest;
import com.aalsikke.dto.ReminderResponseDTO;
import com.aalsikke.model.Reminder;
import com.aalsikke.repository.ReminderRepository;
import com.aalsikke.repository.ScheduleItemRepository;
import com.aalsikke.repository.UserRepository;
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
    private final UserRepository userRepository;   // KAN-381: owner of reminders without a task
    private static final Duration MIN_REMINDER_GAP = Duration.ofMinutes(10);
    private static final int MAX_TITLE_LENGTH = 200;

    public ReminderService(ReminderRepository reminderRepository, ScheduleItemRepository scheduleItemRepository,
                           UserRepository userRepository) {
        this.reminderRepository = reminderRepository;
        this.scheduleItemRepository = scheduleItemRepository;
        this.userRepository = userRepository;
    }

    /**
     * Builds a brand-new Reminder from the DTO only: the client can no longer
     * send id / triggered / createdAt. Empty = item missing or not owned by caller.
     */
    @Transactional
    public Optional<ReminderResponseDTO> createReminder(CreateReminderRequest request, String email) {
        if (request.scheduleItemId() == null) {
            return createStandalone(request, email);   // KAN-381
        }
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
    /** KAN-381: a reminder on its own ("22/12 Coco's birthday, buy a gift"). Empty = unknown user. */
    private Optional<ReminderResponseDTO> createStandalone(CreateReminderRequest request, String email) {
        if (request.remindAt() == null) {
            throw new IllegalArgumentException("remindAt is required.");
        }
        String title = cleanTitle(request.title());
        return userRepository.findByEmail(email)
                .map(user -> {
                    Reminder reminder = new Reminder();
                    reminder.setUser(user);
                    reminder.setTitle(title);
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
                    if (item == null) {
                        // KAN-381: no task, so no task window and no 10-minute gap; the title can change
                        if (request.remindAt() == null) {
                            throw new IllegalArgumentException("remindAt is required.");
                        }
                        if (request.title() != null) {
                            reminder.setTitle(cleanTitle(request.title()));
                        }
                    } else {
                        validateWithinTimeline(item, request.remindAt());
                        // The reminder being edited must not clash with itself
                        List<Reminder> others = reminderRepository.findByScheduleItemId(item.getId()).stream()
                                .filter(other -> !other.getId().equals(reminder.getId()))
                                .toList();
                        validateMinGap(request.remindAt(), others);
                    }
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

    /** KAN-381: every reminder the bell list shows, with or without a task. */
    @Transactional(readOnly = true)
    public List<ReminderResponseDTO> getAllRemindersForCurrentUser(String email) {
        return reminderRepository.findAllForUser(email, OffsetDateTime.now())
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
     * KAN-299 / KAN-379: a reminder can be any time up to the task's end.
     * Before the start is allowed (the evening before, the same morning): that's what reminders are for.
     * End = deadline, or endTime when there is no deadline. Start-only tasks have no upper limit.
     */
    static void validateWithinTimeline(ScheduleItem item, OffsetDateTime remindAt) {
        if (remindAt == null) {
            throw new IllegalArgumentException("remindAt is required.");
        }
        OffsetDateTime to = item.getDeadline() != null ? item.getDeadline() : item.getEndTime();
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

    /** KAN-381: trimmed, 1–200 characters. */
    static String cleanTitle(String title) {
        String clean = title == null ? "" : title.trim();
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("A reminder needs a title.");
        }
        if (clean.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("A reminder title can be at most 200 characters.");
        }
        return clean;
    }

    private ReminderResponseDTO toResponse(Reminder reminder) {
        ScheduleItem item = reminder.getScheduleItem();   // KAN-381: null for a reminder on its own
        return new ReminderResponseDTO(
                reminder.getId(),
                item != null ? item.getId() : null,
                item != null ? item.getTitle() : reminder.getTitle(),
                reminder.getRemindAt(),
                reminder.isTriggered()
        );
    }
}