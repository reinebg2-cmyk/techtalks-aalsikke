package com.aalsikke.controller;

import com.aalsikke.model.ScheduleConflict;
import com.aalsikke.service.ScheduleConflictService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/schedule-conflicts")
public class ScheduleConflictController {
    private final ScheduleConflictService scheduleConflictService;

    public ScheduleConflictController(ScheduleConflictService scheduleConflictService) {
        this.scheduleConflictService = scheduleConflictService;
    }

    @GetMapping("/user/{userId}")
    public List<ScheduleConflict> getConflictsByUser(@PathVariable UUID userId) {
        return scheduleConflictService.getConflictsByUser(userId);
    }

    @PostMapping
    public ResponseEntity<ScheduleConflict> createConflict(@RequestBody ScheduleConflict conflict) {
        return ResponseEntity.ok(scheduleConflictService.createConflict(conflict));
    }
}