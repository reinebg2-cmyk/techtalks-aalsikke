package com.aalsikke.controller;

import com.aalsikke.model.ScheduleItem;
import com.aalsikke.service.ScheduleItemService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/schedule-items")
public class ScheduleItemController {
    private final ScheduleItemService scheduleItemService;

    public ScheduleItemController(ScheduleItemService scheduleItemService) {
        this.scheduleItemService = scheduleItemService;
    }

    @GetMapping("/user/{userId}")
    public List<ScheduleItem> getItemsByUser(@PathVariable UUID userId) {
        return scheduleItemService.getItemsByUser(userId);
    }

    @PostMapping
    public ResponseEntity<ScheduleItem> createItem(@RequestBody ScheduleItem item) {
        return ResponseEntity.ok(scheduleItemService.createItem(item));
    }
}