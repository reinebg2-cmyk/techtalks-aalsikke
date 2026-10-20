package com.aalsikke.service;

import com.aalsikke.mail.EmailSender;
import com.aalsikke.model.Reminder;
import com.aalsikke.model.ScheduleItem;
import com.aalsikke.model.User;
import com.aalsikke.model.RecurrenceRule;
import com.aalsikke.config.AppTime;
import com.aalsikke.service.RecurrenceExpander.Occurrence;
import com.aalsikke.repository.ReminderRepository;
import com.aalsikke.repository.RecurrenceRuleRepository;
import com.aalsikke.repository.OccurrenceCompletionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.aalsikke.util.EmailLang;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

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
    private final RecurrenceRuleRepository recurrenceRuleRepository;            // KAN-370
    private final OccurrenceCompletionRepository occurrenceCompletionRepository;

    public ReminderEmailJob(ReminderRepository reminderRepository, EmailSender emailSender,
                            RecurrenceRuleRepository recurrenceRuleRepository,
                            OccurrenceCompletionRepository occurrenceCompletionRepository) {
        this.reminderRepository = reminderRepository;
        this.emailSender = emailSender;
        this.recurrenceRuleRepository = recurrenceRuleRepository;
        this.occurrenceCompletionRepository = occurrenceCompletionRepository;
    }

    @Scheduled(fixedDelayString = "${app.reminders.email-poll-ms:60000}",
            initialDelayString = "${app.reminders.email-initial-delay-ms:15000}")
    public void run() {
        OffsetDateTime now = OffsetDateTime.now();
        rollWeeklyReminders(now);       // KAN-370: first move finished weeks forward...
        sendDueReminderEmails(now);     // ...then email what is due now
    }

    /** KAN-370: moves reminders on weekly items whose repeat is over to the next repeat. Returns how many moved. */
    int rollWeeklyReminders(OffsetDateTime now) {
        int rolled = 0;
        for (Reminder reminder : reminderRepository.findPastOnRecurringItems(now)) {
            try {
                ScheduleItem item = reminder.getScheduleItem();
                RecurrenceRule rule = weeklyRuleOf(item);
                if (rule == null) continue;
                Optional<OffsetDateTime> next = ReminderRoller.nextRemindAt(reminder.getRemindAt(),
                        item.getStartTime(), item.getEndTime(), intervalOf(rule),
                        RecurrenceExpander.parseDays(rule.getDaysOfWeek()), rule.getUntilDate(), AppTime.ZONE, now);
                if (next.isPresent()) {
                    reminder.setRemindAt(next.get());
                    reminder.setTriggered(false);   // show in the bell again
                    reminder.setEmailedAt(null);    // ...and email again
                    reminderRepository.save(reminder);
                    rolled++;
                }
            } catch (RuntimeException e) {
                log.error("[REMINDER] Could not move reminder {} to the next week: {}", reminder.getId(), e.getMessage());
            }
        }
        if (rolled > 0) {
            log.info("[REMINDER] Moved {} weekly reminder(s) to their next week", rolled);
        }
        return rolled;
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
            User user = item != null ? item.getUser() : reminder.getUser();
            try {
                if (item == null) {
                    // KAN-381: a reminder on its own: its own text, no due time
                    emailSender.sendReminderEmail(user.getEmail(), user.getFullName(), reminder.getTitle(), null);
                    reminderRepository.markEmailed(reminder.getId(), OffsetDateTime.now());
                    sent++;
                    continue;
                }
                OffsetDateTime dueAt = item.getDeadline() != null ? item.getDeadline() : item.getEndTime();
                if (item.isRecurring()) {
                    // KAN-370: the reminder belongs to one week of a repeating item
                    Occurrence week = weekOf(reminder, item);
                    if (week != null) {
                        if (isWeekDone(item, week)) {
                            // That week is already ticked off: no email; it moves on once the week is over
                            reminderRepository.markEmailed(reminder.getId(), OffsetDateTime.now());
                            continue;
                        }
                        if (!week.start().isEqual(item.getStartTime())) {
                            dueAt = week.end();   // a later week: show that week's time, not the first date's
                        }
                    }
                }
                emailSender.sendReminderEmail(user.getEmail(), user.getFullName(), item.getTitle(), dueAt,
                        EmailLang.normalize(user.getPreferredLanguage()));
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

    private RecurrenceRule weeklyRuleOf(ScheduleItem item) {
        return recurrenceRuleRepository.findByScheduleItemId(item.getId())
                .filter(rule -> "WEEKLY".equals(rule.getFrequency()))
                .orElse(null);
    }

    private static int intervalOf(RecurrenceRule rule) {
        return rule.getIntervalValue() != null ? rule.getIntervalValue() : 1;
    }

    /** KAN-370: the week (original item or a repeat) this reminder belongs to, or null if it can't tell. */
    private Occurrence weekOf(Reminder reminder, ScheduleItem item) {
        RecurrenceRule rule = weeklyRuleOf(item);
        if (rule == null || item.getStartTime() == null || item.getEndTime() == null) return null;
        return ReminderRoller.anchorOf(reminder.getRemindAt(), item.getStartTime(), item.getEndTime(),
                intervalOf(rule), RecurrenceExpander.parseDays(rule.getDaysOfWeek()), rule.getUntilDate(), AppTime.ZONE);
    }

    /** KAN-363/370: the first date uses the item's own status; later weeks use occurrence_completions. */
    private boolean isWeekDone(ScheduleItem item, Occurrence week) {
        if (week.start().isEqual(item.getStartTime())) {
            return "COMPLETED".equals(item.getStatus());
        }
        LocalDate date = week.start().atZoneSameInstant(AppTime.ZONE).toLocalDate();
        return occurrenceCompletionRepository.existsByScheduleItemIdAndOccurrenceDate(item.getId(), date);
    }
}