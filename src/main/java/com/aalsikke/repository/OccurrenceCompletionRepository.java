package com.aalsikke.repository;

import com.aalsikke.model.OccurrenceCompletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface OccurrenceCompletionRepository extends JpaRepository<OccurrenceCompletion, UUID> {

    boolean existsByScheduleItemIdAndOccurrenceDate(UUID scheduleItemId, LocalDate occurrenceDate);

    @Transactional
    long deleteByScheduleItemIdAndOccurrenceDate(UUID scheduleItemId, LocalDate occurrenceDate);

    /**
     * KAN-363: completed repeats of the given items between two dates (inclusive), in one query.
     * Each row: [scheduleItemId (UUID), occurrenceDate (LocalDate)].
     */
    @Query("""
            SELECT c.scheduleItem.id, c.occurrenceDate FROM OccurrenceCompletion c
            WHERE c.scheduleItem.id IN :itemIds
              AND c.occurrenceDate BETWEEN :fromDate AND :toDate
            """)
    List<Object[]> findCompletedDates(@Param("itemIds") Collection<UUID> itemIds,
                                      @Param("fromDate") LocalDate fromDate,
                                      @Param("toDate") LocalDate toDate);
}