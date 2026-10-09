package com.aalsikke.controller;

import com.aalsikke.dto.ChecklistItemRequest;
import com.aalsikke.dto.ChecklistItemResponse;
import com.aalsikke.dto.ChecklistOrderRequest;
import com.aalsikke.service.ChecklistService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * KAN-342: checklist of one task. Only the task's owner can see or change it;
 * anyone else (or an unknown task / item) gets 403, like the other schedule-item endpoints.
 */
@RestController
@RequestMapping("/api/schedule-items/{taskId}/checklist")
public class ChecklistController {

    private final ChecklistService checklistService;

    public ChecklistController(ChecklistService checklistService) {
        this.checklistService = checklistService;
    }

    @GetMapping
    public ResponseEntity<List<ChecklistItemResponse>> list(@PathVariable UUID taskId, Authentication authentication) {
        return checklistService.list(taskId, authentication.getName())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    @PostMapping
    public ResponseEntity<ChecklistItemResponse> add(@PathVariable UUID taskId,
                                                     @RequestBody ChecklistItemRequest request,
                                                     Authentication authentication) {
        return checklistService.add(taskId, request, authentication.getName())
                .map(item -> ResponseEntity.status(HttpStatus.CREATED).body(item))
                .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    @PatchMapping("/{itemId}")
    public ResponseEntity<ChecklistItemResponse> update(@PathVariable UUID taskId, @PathVariable UUID itemId,
                                                        @RequestBody ChecklistItemRequest request,
                                                        Authentication authentication) {
        return checklistService.update(taskId, itemId, request, authentication.getName())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> delete(@PathVariable UUID taskId, @PathVariable UUID itemId,
                                       Authentication authentication) {
        return checklistService.delete(taskId, itemId, authentication.getName())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PutMapping("/order")
    public ResponseEntity<List<ChecklistItemResponse>> reorder(@PathVariable UUID taskId,
                                                               @RequestBody ChecklistOrderRequest request,
                                                               Authentication authentication) {
        return checklistService.reorder(taskId, request == null ? null : request.itemIds(), authentication.getName())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }
}