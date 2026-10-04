package com.aalsikke.repository;

import com.aalsikke.model.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;
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
}

