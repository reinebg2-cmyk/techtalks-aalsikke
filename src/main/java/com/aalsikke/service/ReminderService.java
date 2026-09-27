package com.aalsikke.service;

import com.aalsikke.model.Reminder;
import com.aalsikke.model.ScheduleItem;
import com.aalsikke.repository.ReminderRepository;
import com.aalsikke.repository.ScheduleItemRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ReminderService {
    private final ReminderRepository reminderRepository;
    private final ScheduleItemRepository scheduleItemRepository;

    public ReminderService(ReminderRepository reminderRepository, ScheduleItemRepository scheduleItemRepository) {
        this.reminderRepository = reminderRepository;
        this.scheduleItemRepository = scheduleItemRepository;
    }

    public Reminder createReminder(Reminder reminder, String email) {
        ScheduleItem item = scheduleItemRepository.findById(reminder.getScheduleItem().getId())
                .filter(existing -> existing.getUser().getEmail().equals(email))
                .orElseThrow(() -> new RuntimeException("Schedule item not found or not owned by you"));

        reminder.setScheduleItem(item); // re-attach the verified, fully-loaded entity
        return reminderRepository.save(reminder);
    }

    /** KAN-236: the bell/panel calls this on app open. */
    public List<Reminder> getDueRemindersForCurrentUser(String email) {
        return reminderRepository.findDueForUser(email, OffsetDateTime.now());
    }

    public boolean markTriggered(UUID id, String email) {
        return reminderRepository.findByIdAndOwner(id, email)
                .map(reminder -> {
                    reminder.setTriggered(true);
                    reminderRepository.save(reminder);
                    return true;
                })
                .orElse(false);
    }
}

