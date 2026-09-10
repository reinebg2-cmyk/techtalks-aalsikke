package com.aalsikke.service;

import com.aalsikke.model.RecurrenceRule;
import com.aalsikke.repository.RecurrenceRuleRepository;
import org.springframework.stereotype.Service;

@Service
public class RecurrenceRuleService {
    private final RecurrenceRuleRepository recurrenceRuleRepository;

    public RecurrenceRuleService(RecurrenceRuleRepository recurrenceRuleRepository) {
        this.recurrenceRuleRepository = recurrenceRuleRepository;
    }

    public RecurrenceRule createRule(RecurrenceRule rule) {
        return recurrenceRuleRepository.save(rule);
    }
}