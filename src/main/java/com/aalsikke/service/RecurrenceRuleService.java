package com.aalsikke.service;

import com.aalsikke.dto.RecurrenceRuleRequest;
import com.aalsikke.dto.RecurrenceRuleResponse;
import com.aalsikke.model.RecurrenceRule;
import com.aalsikke.model.ScheduleItem;
import com.aalsikke.model.User;
import com.aalsikke.repository.RecurrenceRuleRepository;
import com.aalsikke.repository.ScheduleItemRepository;
import com.aalsikke.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.List;

@Service
public class RecurrenceRuleService {
    private final RecurrenceRuleRepository recurrenceRuleRepository;
    private final ScheduleItemRepository scheduleItemRepository;
    private final UserRepository userRepository;

    public RecurrenceRuleService(RecurrenceRuleRepository recurrenceRuleRepository,
                                 ScheduleItemRepository scheduleItemRepository,
                                 UserRepository userRepository) {
        this.recurrenceRuleRepository = recurrenceRuleRepository;
        this.scheduleItemRepository = scheduleItemRepository;
        this.userRepository = userRepository;
    }

    /** GET /api/recurrence-rules: only the caller's rules. */
    public List<RecurrenceRuleResponse> getRulesForCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return recurrenceRuleRepository.findAllForUser(user.getId())
                .stream()
                .map(RecurrenceRuleResponse::from)
                .toList();
    }

    /**
     * POST /api/recurrence-rules: make one of the caller's items repeat weekly.
     * An item has at most one rule; posting again for the same item replaces its rule.
     */
    public RecurrenceRuleResponse createOrReplaceRule(RecurrenceRuleRequest request, String email) {
        // Same "not found" message for someone else's item, so the API never reveals it exists
        ScheduleItem item = scheduleItemRepository.findById(request.scheduleItemId())
                .filter(existing -> existing.getUser().getEmail().equals(email))
                .orElseThrow(() -> new IllegalArgumentException("Schedule item not found."));

        if (!ConflictRules.hasTimeSlot(item)) {
            throw new IllegalArgumentException("Only items with a start and end time can repeat.");
        }
        if (request.untilDate() != null) {
            var firstDay = item.getStartTime().atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();
            if (request.untilDate().isBefore(firstDay)) {
                throw new IllegalArgumentException("untilDate cannot be before the item's own date.");
            }
        }

        RecurrenceRule rule = recurrenceRuleRepository.findByScheduleItemId(item.getId())
                .orElseGet(RecurrenceRule::new);
        rule.setScheduleItem(item);
        rule.setFrequency(request.frequency());
        rule.setIntervalValue(request.intervalValue() != null ? request.intervalValue() : 1);
        rule.setDaysOfWeek(request.daysOfWeek());
        rule.setUntilDate(request.untilDate());

        item.setRecurring(true);
        scheduleItemRepository.save(item);
        return RecurrenceRuleResponse.from(recurrenceRuleRepository.save(rule));
    }
}