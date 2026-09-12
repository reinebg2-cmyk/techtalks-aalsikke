package com.aalsikke.controller;

import com.aalsikke.model.ScheduleItem;
import com.aalsikke.service.ScheduleItemService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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

    @GetMapping("/me")
    public List<ScheduleItem> getMyItems(Authentication authentication) {
        return scheduleItemService.getItemsForCurrentUser(authentication.getName());
    }

    @PostMapping
    public ResponseEntity<ScheduleItem> createItem(@RequestBody ScheduleItem item, Authentication authentication) {
        return ResponseEntity.ok(scheduleItemService.createItem(item, authentication.getName()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ScheduleItem> updateItem(@PathVariable UUID id, @RequestBody ScheduleItem item, Authentication authentication) {
        return scheduleItemService.updateItem(id, item, authentication.getName())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(403).build()); // 403 if not found OR not owned — don't leak which
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable UUID id, Authentication authentication) {
        if (scheduleItemService.deleteItem(id, authentication.getName())) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(403).build();
    }
}