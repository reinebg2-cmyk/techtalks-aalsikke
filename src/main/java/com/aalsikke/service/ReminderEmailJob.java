package com.aalsikke.service;

import com.aalsikke.mail.EmailSender;
import com.aalsikke.model.Reminder;
import com.aalsikke.model.ScheduleItem;
import com.aalsikke.model.User;
import com.aalsikke.repository.ReminderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * KAN-336: emails reminders whose time has come, so they reach users even when the app is closed.
 * Runs every minute. Each reminder is emailed once (reminders.emailed_at). A failed send is logged,
 * does not stop the other reminders, and is retried on the next run.
 */
@Component
public class ReminderEmailJob {

    private static final Logger log = LoggerFactory.getLogger(ReminderEmailJob.class);

    private final ReminderRepository reminderRepository;
    private final EmailSender emailSender;

    public ReminderEmailJob(ReminderRepository reminderRepository, EmailSender emailSender) {
        this.reminderRepository = reminderRepository;
        this.emailSender = emailSender;
    }

    @Scheduled(fixedDelayString = "${app.reminders.email-poll-ms:60000}",
            initialDelayString = "${app.reminders.email-initial-delay-ms:15000}")
    public void run() {
        sendDueReminderEmails(OffsetDateTime.now());
    }

    /** Returns how many emails went out. Package-private so tests can pass a fixed "now". */
    int sendDueReminderEmails(OffsetDateTime now) {
        List<Reminder> due = reminderRepository.findDueForEmail(now);
        if (due.isEmpty()) {
            return 0;
        }

        int sent = 0;
        for (Reminder reminder : due) {
            ScheduleItem item = reminder.getScheduleItem();
            User user = item.getUser();
            try {
                OffsetDateTime dueAt = item.getDeadline() != null ? item.getDeadline() : item.getEndTime();
                emailSender.sendReminderEmail(user.getEmail(), user.getFullName(), item.getTitle(), dueAt);
                reminderRepository.markEmailed(reminder.getId(), OffsetDateTime.now());
                sent++;
            } catch (RuntimeException e) {
                log.error("[REMINDER] Email for reminder {} to {} failed, will retry next run: {}",
                        reminder.getId(), user.getEmail(), e.getMessage());
            }
        }
        log.info("[REMINDER] Sent {} of {} due reminder emails", sent, due.size());
        return sent;
    }
}