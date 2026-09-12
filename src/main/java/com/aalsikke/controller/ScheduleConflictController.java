package com.aalsikke.controller;

import com.aalsikke.model.ScheduleConflict;
import com.aalsikke.service.ScheduleConflictService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/schedule-conflicts")
public class ScheduleConflictController {
    private final ScheduleConflictService scheduleConflictService;

    public ScheduleConflictController(ScheduleConflictService scheduleConflictService) {
        this.scheduleConflictService = scheduleConflictService;
    }

    @GetMapping("/me")
    public List<ScheduleConflict> getMyConflicts(Authentication authentication) {
        return scheduleConflictService.getConflictsForCurrentUser(authentication.getName());
    }

    @PostMapping
    public ResponseEntity<ScheduleConflict> createConflict(@RequestBody ScheduleConflict conflict, Authentication authentication) {
        return ResponseEntity.ok(scheduleConflictService.createConflict(conflict, authentication.getName()));
    }
}