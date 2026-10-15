package com.aalsikke.service;

import com.aalsikke.dto.ConflictPairResponse;
import com.aalsikke.model.ScheduleItem;
import com.aalsikke.model.User;
import com.aalsikke.repository.ScheduleItemRepository;
import com.aalsikke.repository.UserRepository;
import org.springframework.stereotype.Service;
import com.aalsikke.model.TimeType; // Adjust the package path to match where TimeType lives

import java.util.ArrayList;
import java.util.List;

/**
 * S3-07: conflicts are computed from the items' current times on every request,
 * so they are never stale after an edit or delete. The old schedule_conflicts table is no longer used.
 */
@Service
public class ScheduleConflictService {
    private final ScheduleItemRepository scheduleItemRepository;
    private final UserRepository userRepository;

    public ScheduleConflictService(ScheduleItemRepository scheduleItemRepository, UserRepository userRepository) {
        this.scheduleItemRepository = scheduleItemRepository;
        this.userRepository = userRepository;
    }

    public List<ConflictPairResponse> getConflictsForCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // The caller's items that have a start time (TIME_BLOCKED and START_ONLY), sorted by start.
        // DEADLINE_ONLY items have no start time, so they are never loaded.
        List<ScheduleItem> items = scheduleItemRepository
                .findByUserIdAndStartTimeIsNotNullOrderByStartTimeAsc(user.getId());

        List<ConflictPairResponse> pairs = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ScheduleItem current = items.get(i);
            // Sorted by start, so once a later item is out of current's reach we can stop looking.
            for (int j = i + 1; j < items.size() && canReach(current, items.get(j)); j++) {
                ScheduleItem other = items.get(j);
                if (ConflictRules.overlaps(current, other)) {
                    pairs.add(ConflictPairResponse.of(current, other));
                }
            }
        }
        return pairs;
    }

    /**
     * Can a later-starting item still conflict with current?
     */
    private static boolean canReach(ScheduleItem current, ScheduleItem next) {
        if (current.getTimeType() == TimeType.TIME_BLOCKED) {
            return next.getStartTime().isBefore(current.getEndTime());
        }
        // START_ONLY is a single instant: only an item starting at the same instant can still conflict
        return !next.getStartTime().isAfter(current.getStartTime());
    }
}