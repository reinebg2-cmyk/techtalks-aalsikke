package com.aalsikke.service;

import com.aalsikke.model.RecurrenceRule;
import com.aalsikke.model.ScheduleItem;
import com.aalsikke.repository.RecurrenceRuleRepository;
import com.aalsikke.repository.ScheduleItemRepository;
import org.springframework.stereotype.Service;

@Service
public class RecurrenceRuleService {
    private final RecurrenceRuleRepository recurrenceRuleRepository;
    private final ScheduleItemRepository scheduleItemRepository;

    public RecurrenceRuleService(RecurrenceRuleRepository recurrenceRuleRepository, ScheduleItemRepository scheduleItemRepository) {
        this.recurrenceRuleRepository = recurrenceRuleRepository;
        this.scheduleItemRepository = scheduleItemRepository;
    }

    public RecurrenceRule createRule(RecurrenceRule rule, String email) {
        ScheduleItem item = scheduleItemRepository.findById(rule.getScheduleItem().getId())
                .filter(existing -> existing.getUser().getEmail().equals(email))
                .orElseThrow(() -> new RuntimeException("Schedule item not found or not owned by you"));

        rule.setScheduleItem(item);
        return recurrenceRuleRepository.save(rule);
    }
}