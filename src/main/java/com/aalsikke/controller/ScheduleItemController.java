package com.aalsikke.controller;

import com.aalsikke.dto.ScheduleItemRequest;
import com.aalsikke.dto.ScheduleItemResponse;
import com.aalsikke.service.ScheduleItemService;
import jakarta.validation.Valid;
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
    public List<ScheduleItemResponse> getMyItems(Authentication authentication) {
        return scheduleItemService.getItemsForCurrentUser(authentication.getName());
    }

    @PostMapping
    public ResponseEntity<ScheduleItemResponse> createItem(@Valid @RequestBody ScheduleItemRequest request, Authentication authentication) {
        return ResponseEntity.ok(scheduleItemService.createItem(request, authentication.getName()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ScheduleItemResponse> updateItem(@PathVariable UUID id, @RequestBody ScheduleItemRequest request, Authentication authentication) {
        return scheduleItemService.updateItem(id, request, authentication.getName())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(403).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable UUID id, Authentication authentication) {
        if (scheduleItemService.deleteItem(id, authentication.getName())) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(403).build();
    }
}