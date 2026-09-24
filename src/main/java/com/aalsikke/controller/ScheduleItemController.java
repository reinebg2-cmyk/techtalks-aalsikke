package com.aalsikke.controller;

import com.aalsikke.dto.ScheduleItemRequest;
import com.aalsikke.dto.ScheduleItemResponse;
import com.aalsikke.service.ScheduleItemService;
import com.aalsikke.dto.StatusUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;
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

    /**
     * Calendar range (S3-02).
     * Example: GET /api/schedule-items/range?from=2026-09-21T00:00:00Z&to=2026-09-28T00:00:00Z
     * from is inclusive, to is exclusive. Both must be ISO date-times with an offset.
     */
    @GetMapping("/range")
    public List<ScheduleItemResponse> getMyItemsInRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            Authentication authentication) {
        return scheduleItemService.getItemsInRange(authentication.getName(), from, to);
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

    /** Dashboard "done" checkbox: {"status":"COMPLETED"} or {"status":"PENDING"}. */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ScheduleItemResponse> updateStatus(@PathVariable UUID id,
                                                             @Valid @RequestBody StatusUpdateRequest request,
                                                             Authentication authentication) {
        return scheduleItemService.updateStatus(id, request.status(), authentication.getName())
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