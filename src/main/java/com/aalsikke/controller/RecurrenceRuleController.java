package com.aalsikke.controller;

import com.aalsikke.model.RecurrenceRule;
import com.aalsikke.service.RecurrenceRuleService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recurrence-rules")
public class RecurrenceRuleController {
    private final RecurrenceRuleService recurrenceRuleService;

    public RecurrenceRuleController(RecurrenceRuleService recurrenceRuleService) {
        this.recurrenceRuleService = recurrenceRuleService;
    }

    @PostMapping
    public ResponseEntity<RecurrenceRule> createRule(@RequestBody RecurrenceRule rule, Authentication authentication) {
        return ResponseEntity.ok(recurrenceRuleService.createRule(rule, authentication.getName()));
    }
}