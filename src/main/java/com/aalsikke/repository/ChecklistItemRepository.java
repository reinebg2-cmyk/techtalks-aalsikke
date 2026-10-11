package com.aalsikke.repository;

import com.aalsikke.model.ChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, UUID> {

    /** KAN-342: a task's checklist in display order. */
    List<ChecklistItem> findByScheduleItemIdOrderByPositionAsc(UUID scheduleItemId);

    Optional<ChecklistItem> findByIdAndScheduleItemId(UUID id, UUID scheduleItemId);

    /**
     * KAN-342: "done / total" per task for all of a user's tasks, in one query (no query per card).
     * Each row: [scheduleItemId (UUID), total (Long), done (Long)].
     */
    @Query("""
            SELECT c.scheduleItem.id, COUNT(c), SUM(CASE WHEN c.done = true THEN 1 ELSE 0 END)
            FROM ChecklistItem c
            WHERE c.scheduleItem.user.email = :email
            GROUP BY c.scheduleItem.id
            """)
    List<Object[]> countsForUser(@Param("email") String email);
}