package com.aalsikke.controller;

import com.aalsikke.dto.DashboardSummaryDTO;
import com.aalsikke.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import com.aalsikke.model.ScheduleItem;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryDTO> getSummary(Authentication authentication) {
        return ResponseEntity.ok(dashboardService.getSummary(authentication.getName()));
    }
    @GetMapping("/today")
    public ResponseEntity<List<ScheduleItem>> getTodaysTasks(Authentication authentication) {
        return ResponseEntity.ok(dashboardService.getTodaysTasks(authentication.getName()));
    }
    @GetMapping("/upcoming")
    public ResponseEntity<List<ScheduleItem>> getUpcomingDeadlines(Authentication authentication) {
        return ResponseEntity.ok(dashboardService.getUpcomingDeadlines(authentication.getName()));
    }
    @GetMapping("/overdue")
    public ResponseEntity<List<ScheduleItem>> getOverdueTasks(Authentication authentication) {
        return ResponseEntity.ok(dashboardService.getOverdueTasks(authentication.getName()));
    }
}