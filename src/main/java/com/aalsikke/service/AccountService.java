package com.aalsikke.service;

import com.aalsikke.exception.InvalidCurrentPasswordException;
import com.aalsikke.mail.EmailSender;
import com.aalsikke.model.ChecklistItem;
import com.aalsikke.model.Course;
import com.aalsikke.model.DailyReview;
import com.aalsikke.model.Notification;
import com.aalsikke.model.RecurrenceRule;
import com.aalsikke.model.Reminder;
import com.aalsikke.model.ScheduleItem;
import com.aalsikke.model.User;
import com.aalsikke.model.Workplace;
import com.aalsikke.repository.ChecklistItemRepository;
import com.aalsikke.repository.CourseRepository;
import com.aalsikke.repository.DailyReviewRepository;
import com.aalsikke.repository.NotificationPreferenceRepository;
import com.aalsikke.repository.NotificationRepository;
import com.aalsikke.repository.RecurrenceRuleRepository;
import com.aalsikke.repository.ScheduleItemRepository;
import com.aalsikke.repository.UserRepository;
import com.aalsikke.repository.WorkplaceRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * KAN-338: users control their own data.
 * - exportData: everything the user has stored, as one JSON document (never the password hash or tokens)
 * - deleteAccount: removes the user; the database deletes everything they own (ON DELETE CASCADE)
 * Both only ever act on the signed-in user's own account.
 */
@Service
public class AccountService {

    /** The word the user must type to confirm deleting their account. */
    public static final String CONFIRMATION_WORD = "DELETE";

    private final UserRepository userRepository;
    private final ScheduleItemRepository scheduleItemRepository;
    private final RecurrenceRuleRepository recurrenceRuleRepository;
    private final CourseRepository courseRepository;
    private final WorkplaceRepository workplaceRepository;
    private final DailyReviewRepository dailyReviewRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;

    public AccountService(UserRepository userRepository,
                          ScheduleItemRepository scheduleItemRepository,
                          RecurrenceRuleRepository recurrenceRuleRepository,
                          CourseRepository courseRepository,
                          WorkplaceRepository workplaceRepository,
                          DailyReviewRepository dailyReviewRepository,
                          NotificationRepository notificationRepository,
                          NotificationPreferenceRepository notificationPreferenceRepository,
                          ChecklistItemRepository checklistItemRepository,
                          PasswordEncoder passwordEncoder,
                          EmailSender emailSender) {
        this.userRepository = userRepository;
        this.scheduleItemRepository = scheduleItemRepository;
        this.recurrenceRuleRepository = recurrenceRuleRepository;
        this.courseRepository = courseRepository;
        this.workplaceRepository = workplaceRepository;
        this.dailyReviewRepository = dailyReviewRepository;
        this.notificationRepository = notificationRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.checklistItemRepository = checklistItemRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
    }

    // ---------------- export ----------------

    @Transactional(readOnly = true)
    public Map<String, Object> exportData(String email) {
        User user = findUser(email);
        UUID userId = user.getId();

        Map<UUID, RecurrenceRule> repeatByTask = recurrenceRuleRepository.findAllForUser(userId).stream()
                .collect(Collectors.toMap(rule -> rule.getScheduleItem().getId(), Function.identity(), (a, b) -> a));
        Map<UUID, List<ChecklistItem>> stepsByTask = checklistItemRepository.findAllForUser(userId).stream()
                .collect(Collectors.groupingBy(step -> step.getScheduleItem().getId()));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("format", "aal-sikke-export-v1");
        data.put("exportedAt", OffsetDateTime.now());
        data.put("profile", profileOf(user));
        data.put("notificationPreferences", notificationPreferenceRepository.findByUserId(userId)
                .map(p -> fields(
                        "deadlineReminders", p.isDeadlineReminders(),
                        "overdueAlerts", p.isOverdueAlerts(),
                        "scheduleConflicts", p.isScheduleConflicts(),
                        "weeklyReviewReady", p.isWeeklyReviewReady(),
                        "emailEnabled", p.isEmailEnabled()))
                .orElse(null));
        data.put("courses", courseRepository.findByUserId(userId).stream().map(AccountService::courseOf).toList());
        data.put("workplaces", workplaceRepository.findByUserId(userId).stream().map(AccountService::workplaceOf).toList());
        data.put("tasks", scheduleItemRepository.findByUserId(userId).stream()
                .map(item -> taskOf(item, repeatByTask.get(item.getId()), stepsByTask.getOrDefault(item.getId(), List.of())))
                .toList());
        data.put("dailyReviews", dailyReviewRepository.findByUserIdOrderByReviewDateAsc(userId).stream()
                .map(AccountService::reviewOf).toList());
        data.put("notifications", notificationRepository.findByUserId(userId).stream()
                .map(AccountService::notificationOf).toList());
        return data;
    }

    /** "aal-sikke-data-2026-10-10.json" */
    public static String exportFileName(LocalDate date) {
        return "aal-sikke-data-" + date + ".json";
    }

    /** The profile as exported: no password hash, no internal status fields. */
    static Map<String, Object> profileOf(User user) {
        return fields(
                "fullName", user.getFullName(),
                "username", user.getUsername(),
                "email", user.getEmail(),
                "phoneNumber", user.getPhoneNumber(),
                "dateOfBirth", user.getDateOfBirth(),
                "profileMode", user.getProfileMode(),
                "colorPalette", user.getColorPalette(),
                "emailVerified", user.isEmailVerified(),
                "phoneVerified", user.isPhoneVerified(),
                "twoFactorEnabled", user.isTwoFactorEnabled(),
                "createdAt", user.getCreatedAt());
    }

    private static Map<String, Object> taskOf(ScheduleItem item, RecurrenceRule repeat, List<ChecklistItem> steps) {
        List<Map<String, Object>> reminders = item.getReminders().stream()
                .map(AccountService::reminderOf).toList();
        return fields(
                "title", item.getTitle(),
                "description", item.getDescription(),
                "category", item.getItemType(),
                "priority", item.getPriority(),
                "status", item.getStatus(),
                "startTime", item.getStartTime(),
                "endTime", item.getEndTime(),
                "deadline", item.getDeadline(),
                "timeType", item.getTimeType(),
                "course", item.getCourse() != null ? item.getCourse().getCourseName() : null,
                "workplace", item.getWorkplace() != null ? item.getWorkplace().getCompanyName() : null,
                "remindersOn", item.isReminderEnabled(),
                "reminders", reminders,
                "checklist", checklistOf(steps),
                "weeklyRepeat", repeat == null ? null : fields(
                        "daysOfWeek", repeat.getDaysOfWeek(),
                        "everyWeeks", repeat.getIntervalValue(),
                        "until", repeat.getUntilDate()),
                "createdAt", item.getCreatedAt());
    }

    private static Map<String, Object> reminderOf(Reminder reminder) {
        return fields(
                "remindAt", reminder.getRemindAt(),
                "halfwayReminder", reminder.isDefaultReminder(),
                "shown", reminder.isTriggered(),
                "emailedAt", reminder.getEmailedAt());
    }

    /** KAN-355: a task's checklist steps in display order, as {text, done}. No steps gives an empty list, never null. */
    static List<Map<String, Object>> checklistOf(List<ChecklistItem> steps) {
        return steps.stream()
                .sorted(Comparator.comparingInt(ChecklistItem::getPosition))
                .map(step -> fields("text", step.getText(), "done", step.isDone()))
                .toList();
    }

    private static Map<String, Object> courseOf(Course course) {
        return fields(
                "code", course.getCourseCode(),
                "name", course.getCourseName(),
                "instructor", course.getInstructorName(),
                "location", course.getLocation());
    }

    private static Map<String, Object> workplaceOf(Workplace workplace) {
        return fields(
                "company", workplace.getCompanyName(),
                "jobTitle", workplace.getJobTitle(),
                "location", workplace.getLocation());
    }

    private static Map<String, Object> reviewOf(DailyReview review) {
        return fields(
                "date", review.getReviewDate(),
                "productivityScore", review.getProductivityScore(),
                "notes", review.getNotes());
    }

    private static Map<String, Object> notificationOf(Notification notification) {
        return fields(
                "title", notification.getTitle(),
                "message", notification.getMessage(),
                "read", notification.isRead(),
                "createdAt", notification.getCreatedAt());
    }

    /** An ordered map from name/value pairs (values may be null, unlike Map.of). */
    private static Map<String, Object> fields(Object... namesAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            map.put((String) namesAndValues[i], namesAndValues[i + 1]);
        }
        return map;
    }

    // ---------------- delete ----------------

    /**
     * Deletes the account after checking the password and the confirmation word.
     * Nothing is deleted when either check fails.
     */
    @Transactional
    public void deleteAccount(String email, String currentPassword, String confirmation) {
        User user = findUser(email);

        if (currentPassword == null || currentPassword.isBlank()
                || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException("Current password is incorrect.");
        }
        if (!isConfirmed(confirmation)) {
            throw new IllegalArgumentException("Type " + CONFIRMATION_WORD + " to confirm that you want to delete your account.");
        }

        String to = user.getEmail();
        String name = user.getFullName();

        userRepository.delete(user);
        userRepository.flush();   // a database problem shows up here, before the email is sent

        emailSender.sendSecurityAlert(to, name,
                "Your Aal Sikke account was deleted",
                "Your Aal Sikke account and all of its data were permanently deleted. "
                        + "If you didn't do this, reply to this email right away.");
    }

    /** The user typed the confirmation word exactly (spaces around it are ignored; it must be in capitals). */
    static boolean isConfirmed(String confirmation) {
        return confirmation != null && confirmation.trim().equals(CONFIRMATION_WORD);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}