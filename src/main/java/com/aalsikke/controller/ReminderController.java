package com.aalsikke.controller;

import com.aalsikke.dto.CreateReminderRequest;
import com.aalsikke.dto.ReminderResponseDTO;
import com.aalsikke.service.ReminderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.aalsikke.dto.UpdateReminderRequest;
import com.aalsikke.dto.SnoozeReminderRequest;

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
        // KAN-381: scheduleItemId may be null now (a reminder on its own); the service then requires a title
        if (request == null || request.remindAt() == null) {
            return ResponseEntity.badRequest().build();
        }
        return reminderService.createReminder(request, authentication.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** KAN-381: the bell list: upcoming reminders plus due ones not dismissed yet, with or without a task. */
    @GetMapping
    public List<ReminderResponseDTO> getAllReminders(Authentication authentication) {
        return reminderService.getAllRemindersForCurrentUser(authentication.getName());
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
    /** KAN-346: postpone a reminder instead of dismissing it. */
    @PatchMapping("/{id}/snooze")
    public ResponseEntity<ReminderResponseDTO> snoozeReminder(@PathVariable UUID id,
                                                              @RequestBody(required = false) SnoozeReminderRequest request,
                                                              Authentication authentication) {
        int minutes = (request == null || request.minutes() == null) ? 10 : request.minutes();
        if (minutes < 1 || minutes > 1440) {
            return ResponseEntity.badRequest().build();
        }
        return reminderService.snoozeReminder(id, minutes, authentication.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
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