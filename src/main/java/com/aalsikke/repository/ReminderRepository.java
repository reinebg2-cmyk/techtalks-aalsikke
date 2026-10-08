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
            WHERE r.scheduleItem.user.email = :email
              AND r.isTriggered = false
              AND r.remindAt <= :now
            ORDER BY r.remindAt ASC
            """)
    List<Reminder> findDueForUser(@Param("email") String email, @Param("now") OffsetDateTime now);

    /** Ownership derived through the schedule item, same idea as createReminder's check in the service. */
    @Query("""
            SELECT r FROM Reminder r
            WHERE r.id = :id AND r.scheduleItem.user.email = :email
            """)
    Optional<Reminder> findByIdAndOwner(@Param("id") UUID id, @Param("email") String email);
    List<Reminder> findByScheduleItemId(UUID scheduleItemId);

    /**
     * KAN-336: reminders whose email should go out now, across all users.
     * Skips: already seen in the bell, already emailed, completed tasks, unverified emails,
     * and users who turned email off (no preferences row = default on).
     * Item and user are fetched in the same query so building the email needs no extra queries.
     */
    @Query("""
            SELECT r FROM Reminder r
            JOIN FETCH r.scheduleItem i
            JOIN FETCH i.user u
            WHERE r.remindAt <= :now
              AND r.isTriggered = false
              AND r.emailedAt IS NULL
              AND (i.status IS NULL OR i.status <> 'COMPLETED')
              AND u.emailVerified = true
              AND NOT EXISTS (
                  SELECT p FROM NotificationPreference p
                  WHERE p.user = u AND p.emailEnabled = false
              )
            ORDER BY r.remindAt ASC
            """)
    List<Reminder> findDueForEmail(@Param("now") OffsetDateTime now);

    /** KAN-336: record that the email went out. Own transaction per reminder, so one failure can't undo the others. */
    @Modifying
    @Transactional
    @Query("UPDATE Reminder r SET r.emailedAt = :at WHERE r.id = :id AND r.emailedAt IS NULL")
    int markEmailed(@Param("id") UUID id, @Param("at") OffsetDateTime at);
}

