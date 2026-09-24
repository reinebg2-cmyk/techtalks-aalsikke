package com.aalsikke.controller;

import com.aalsikke.dto.RecurrenceRuleRequest;
import com.aalsikke.dto.RecurrenceRuleResponse;
import com.aalsikke.service.RecurrenceRuleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recurrence-rules")
public class RecurrenceRuleController {
    private final RecurrenceRuleService recurrenceRuleService;

    public RecurrenceRuleController(RecurrenceRuleService recurrenceRuleService) {
        this.recurrenceRuleService = recurrenceRuleService;
    }

    /** KAN-248: the caller's recurrence rules. */
    @GetMapping
    public List<RecurrenceRuleResponse> getMyRules(Authentication authentication) {
        return recurrenceRuleService.getRulesForCurrentUser(authentication.getName());
    }

    @PostMapping
    public ResponseEntity<RecurrenceRuleResponse> createRule(@Valid @RequestBody RecurrenceRuleRequest request,
                                                             Authentication authentication) {
        return ResponseEntity.ok(recurrenceRuleService.createOrReplaceRule(request, authentication.getName()));
    }
}