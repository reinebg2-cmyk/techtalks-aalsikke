package com.aalsikke.repository;

import com.aalsikke.model.ScheduleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ScheduleItemRepository extends JpaRepository<ScheduleItem, UUID> {

    List<ScheduleItem> findByUserIdAndStatusAndEndTimeBeforeOrderByEndTimeAsc(
            UUID userId, String status, OffsetDateTime now);

    List<ScheduleItem> findByUserIdAndEndTimeBetweenOrderByEndTimeAsc(
            UUID userId, OffsetDateTime start, OffsetDateTime end);

    List<ScheduleItem> findByUserId(UUID userId);

    long countByUserId(UUID userId);

    long countByUserIdAndStatus(UUID userId, String status);

    long countByUserIdAndStatusAndEndTimeBefore(UUID userId, String status, OffsetDateTime now);

    /**
     * Calendar range (S3-02): the user's items that should appear between from (inclusive)
     * and to (exclusive). An item is included if
     *  - its time slot overlaps the range (start < to AND end > from), which also catches
     *    items that start before the range and end inside it, or
     *  - its deadline falls inside the range, so the calendar can show a deadline marker.
     * Course and workplace are fetched in the same query to avoid one extra query per item.
     */
    @Query("""
            SELECT s FROM ScheduleItem s
            LEFT JOIN FETCH s.course
            LEFT JOIN FETCH s.workplace
            WHERE s.user.id = :userId
              AND (
                    (s.startTime IS NOT NULL AND s.endTime IS NOT NULL
                        AND s.startTime < :to AND s.endTime > :from)
                 OR (s.deadline IS NOT NULL
                        AND s.deadline >= :from AND s.deadline < :to)
              )
            ORDER BY COALESCE(s.startTime, s.deadline) ASC
            """)
    List<ScheduleItem> findInRange(@Param("userId") UUID userId,
                                   @Param("from") OffsetDateTime from,
                                   @Param("to") OffsetDateTime to);

    /**
     * Conflict candidates (S3-07): the user's OTHER items that have a time slot
     * overlapping [start, end). Back-to-back items are excluded by the strict < and >.
     */
    @Query("""
            SELECT s FROM ScheduleItem s
            WHERE s.user.id = :userId
              AND s.id <> :excludeId
              AND s.startTime IS NOT NULL AND s.endTime IS NOT NULL
              AND s.startTime < :end AND s.endTime > :start
            ORDER BY s.startTime ASC
            """)
    List<ScheduleItem> findOverlappingTimedItems(@Param("userId") UUID userId,
                                                 @Param("excludeId") UUID excludeId,
                                                 @Param("start") OffsetDateTime start,
                                                 @Param("end") OffsetDateTime end);

    /** All of the user's items that have both a start and an end time, earliest first (S3-07). */
    List<ScheduleItem> findByUserIdAndStartTimeIsNotNullAndEndTimeIsNotNullOrderByStartTimeAsc(UUID userId);
}
