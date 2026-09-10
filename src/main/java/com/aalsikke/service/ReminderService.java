package com.aalsikke.service;

import com.aalsikke.model.Reminder;
import com.aalsikke.repository.ReminderRepository;
import org.springframework.stereotype.Service;

@Service
public class ReminderService {
    private final ReminderRepository reminderRepository;

    public ReminderService(ReminderRepository reminderRepository) {
        this.reminderRepository = reminderRepository;
    }

    public Reminder createReminder(Reminder reminder) {
        return reminderRepository.save(reminder);
    }
}