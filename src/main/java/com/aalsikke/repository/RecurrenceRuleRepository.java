package com.aalsikke.repository;

import com.aalsikke.model.RecurrenceRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Repository
public interface RecurrenceRuleRepository extends JpaRepository<RecurrenceRule, UUID> {

    /** All of the user's rules, with their schedule item loaded in the same query. */
    @Query("""
            SELECT r FROM RecurrenceRule r
            JOIN FETCH r.scheduleItem s
            WHERE s.user.id = :userId
            ORDER BY s.startTime ASC
            """)
    List<RecurrenceRule> findAllForUser(@Param("userId") UUID userId);

    Optional<RecurrenceRule> findByScheduleItemId(UUID scheduleItemId);

    /**
     * Weekly rules that can produce occurrences in a range: the base item has a time slot,
     * starts before the range ends, and the rule has not ended before the range starts.
     */
    @Query("""
            SELECT r FROM RecurrenceRule r
            JOIN FETCH r.scheduleItem s
            LEFT JOIN FETCH s.course
            LEFT JOIN FETCH s.workplace
            WHERE s.user.id = :userId
              AND r.frequency = 'WEEKLY'
              AND s.startTime IS NOT NULL AND s.endTime IS NOT NULL
              AND s.startTime < :to
              AND (r.untilDate IS NULL OR r.untilDate >= :fromDate)
            """)
    List<RecurrenceRule> findActiveWeeklyForUser(@Param("userId") UUID userId,
                                                 @Param("to") OffsetDateTime to,
                                                 @Param("fromDate") LocalDate fromDate);
}