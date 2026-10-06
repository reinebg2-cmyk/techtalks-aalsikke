package com.aalsikke.controller;

import com.aalsikke.dto.CreateReminderRequest;
import com.aalsikke.dto.ReminderResponseDTO;
import com.aalsikke.service.ReminderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.aalsikke.dto.UpdateReminderRequest;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reminders")
public class ReminderController {
    private final ReminderService reminderService;

    public ReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @PostMapping
    public ResponseEntity<ReminderResponseDTO> createReminder(@RequestBody CreateReminderRequest request,
                                                              Authentication authentication) {
        if (request == null || request.scheduleItemId() == null || request.remindAt() == null) {
            return ResponseEntity.badRequest().build();
        }
        return reminderService.createReminder(request, authentication.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** KAN-236: bell/panel polls this on app open. */
    @GetMapping("/due")
    public List<ReminderResponseDTO> getDueReminders(Authentication authentication) {
        return reminderService.getDueRemindersForCurrentUser(authentication.getName());
    }

    @PatchMapping("/{id}/trigger")
    public ResponseEntity<Void> markTriggered(@PathVariable UUID id, Authentication authentication) {
        if (reminderService.markTriggered(id, authentication.getName())) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(403).build();
    }
    @PutMapping("/{id}")
    public ResponseEntity<ReminderResponseDTO> updateReminder(@PathVariable UUID id,
                                                              @RequestBody UpdateReminderRequest request,
                                                              Authentication authentication) {
        if (request == null || request.remindAt() == null) {
            return ResponseEntity.badRequest().build();
        }
        return reminderService.updateReminder(id, request, authentication.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReminder(@PathVariable UUID id, Authentication authentication) {
        if (reminderService.deleteReminder(id, authentication.getName())) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}