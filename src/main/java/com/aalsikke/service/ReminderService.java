package com.aalsikke.service;

import com.aalsikke.dto.CreateReminderRequest;
import com.aalsikke.dto.ReminderResponseDTO;
import com.aalsikke.model.Reminder;
import com.aalsikke.repository.ReminderRepository;
import com.aalsikke.repository.ScheduleItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ReminderService {
    private final ReminderRepository reminderRepository;
    private final ScheduleItemRepository scheduleItemRepository;

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
                    Reminder reminder = new Reminder();
                    reminder.setScheduleItem(item);
                    reminder.setRemindAt(request.remindAt());
                    return toResponse(reminderRepository.save(reminder));
                });
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