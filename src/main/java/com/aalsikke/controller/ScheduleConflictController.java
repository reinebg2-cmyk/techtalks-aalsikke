package com.aalsikke.controller;

import com.aalsikke.dto.ConflictPairResponse;
import com.aalsikke.service.ScheduleConflictService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/schedule-conflicts")
public class ScheduleConflictController {
    private final ScheduleConflictService scheduleConflictService;

    public ScheduleConflictController(ScheduleConflictService scheduleConflictService) {
        this.scheduleConflictService = scheduleConflictService;
    }

    /** All pairs of the caller's items whose time slots overlap, computed on the fly (S3-07). */
    @GetMapping("/me")
    public List<ConflictPairResponse> getMyConflicts(Authentication authentication) {
        return scheduleConflictService.getConflictsForCurrentUser(authentication.getName());
    }
}