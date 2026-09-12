package com.aalsikke.service;

import com.aalsikke.model.Reminder;
import com.aalsikke.model.ScheduleItem;
import com.aalsikke.repository.ReminderRepository;
import com.aalsikke.repository.ScheduleItemRepository;
import org.springframework.stereotype.Service;

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
}