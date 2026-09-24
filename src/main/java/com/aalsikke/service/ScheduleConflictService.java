package com.aalsikke.service;

import com.aalsikke.dto.ConflictPairResponse;
import com.aalsikke.model.ScheduleItem;
import com.aalsikke.model.User;
import com.aalsikke.repository.ScheduleItemRepository;
import com.aalsikke.repository.UserRepository;
import org.springframework.stereotype.Service;

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

        // Only the caller's own items that have both a start and an end time, sorted by start.
        List<ScheduleItem> items = scheduleItemRepository
                .findByUserIdAndStartTimeIsNotNullAndEndTimeIsNotNullOrderByStartTimeAsc(user.getId());

        List<ConflictPairResponse> pairs = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ScheduleItem current = items.get(i);
            // Items are sorted by start, so once a later item starts at or after current ends,
            // no further item can overlap current and we can stop looking.
            for (int j = i + 1; j < items.size()
                    && items.get(j).getStartTime().isBefore(current.getEndTime()); j++) {
                ScheduleItem other = items.get(j);
                if (ConflictRules.overlaps(current, other)) {
                    pairs.add(ConflictPairResponse.of(current, other));
                }
            }
        }
        return pairs;
    }
}