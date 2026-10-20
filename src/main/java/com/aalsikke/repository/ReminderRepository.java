package com.aalsikke.repository;

import com.aalsikke.model.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, UUID> {

    /** Due = not yet triggered and remindAt has passed. Backs GET /api/reminders/due (KAN-235/236). */
    @Query("""
            SELECT r FROM Reminder r
            LEFT JOIN FETCH r.scheduleItem i
            WHERE r.user.email = :email
              AND r.isTriggered = false
              AND r.remindAt <= :now
              AND (i IS NULL OR i.reminderEnabled = true)
            ORDER BY r.remindAt ASC
            """)
    List<Reminder> findDueForUser(@Param("email") String email, @Param("now") OffsetDateTime now);

    /**
     * KAN-381: everything the bell list shows: upcoming reminders plus due ones not yet dismissed.
     * Leaves out dismissed past ones, reminders on tasks with reminders switched off, and finished tasks.
     */
    @Query("""
            SELECT r FROM Reminder r
            LEFT JOIN FETCH r.scheduleItem i
            WHERE r.user.email = :email
              AND (r.isTriggered = false OR r.remindAt > :now)
              AND (i IS NULL OR (i.reminderEnabled = true
                   AND (i.status IS NULL OR i.status <> 'COMPLETED' OR i.isRecurring = true)))
            ORDER BY r.remindAt ASC
            """)
    List<Reminder> findAllForUser(@Param("email") String email, @Param("now") OffsetDateTime now);

    /** KAN-381: ownership is stored on the reminder itself, so it works with or without a task. */
    @Query("""
            SELECT r FROM Reminder r
            WHERE r.id = :id AND r.user.email = :email
            """)
    Optional<Reminder> findByIdAndOwner(@Param("id") UUID id, @Param("email") String email);
    List<Reminder> findByScheduleItemId(UUID scheduleItemId);

    /**
     * KAN-336: reminders whose email should go out now, across all users.
     * Skips: already seen in the bell, already emailed, task reminders switched off (KAN-352),
     * completed tasks, unverified emails,
     * and users who turned email off (no preferences row = default on).
     * Item and user are fetched in the same query so building the email needs no extra queries.
     */
    @Query("""
            SELECT r FROM Reminder r
            LEFT JOIN FETCH r.scheduleItem i
            JOIN FETCH r.user u
            WHERE r.remindAt <= :now
              AND r.isTriggered = false
              AND r.emailedAt IS NULL
              AND (i IS NULL OR (i.reminderEnabled = true
                   AND (i.status IS NULL OR i.status <> 'COMPLETED' OR i.isRecurring = true)))
              AND u.emailVerified = true
              AND NOT EXISTS (
                  SELECT p FROM NotificationPreference p
                  WHERE p.user = u AND p.emailEnabled = false
              )
            ORDER BY r.remindAt ASC
            """)
    List<Reminder> findDueForEmail(@Param("now") OffsetDateTime now);

    /**
     * KAN-370: reminders on repeating items whose time has passed. The reminder job moves the ones whose
     * repeat is over to the next repeat. Rolled reminders are in the future again, so this list stays small.
     */
    @Query("""
            SELECT r FROM Reminder r
            JOIN FETCH r.scheduleItem i
            WHERE i.isRecurring = true
              AND r.remindAt <= :now
            """)
    List<Reminder> findPastOnRecurringItems(@Param("now") OffsetDateTime now);

    /** KAN-336: record that the email went out. Own transaction per reminder, so one failure can't undo the others. */
    @Modifying
    @Transactional
    @Query("UPDATE Reminder r SET r.emailedAt = :at WHERE r.id = :id AND r.emailedAt IS NULL")
    int markEmailed(@Param("id") UUID id, @Param("at") OffsetDateTime at);
}

