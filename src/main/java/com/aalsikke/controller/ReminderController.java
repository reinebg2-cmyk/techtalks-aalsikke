package com.aalsikke.controller;

import com.aalsikke.model.Reminder;
import com.aalsikke.service.ReminderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<Reminder> createReminder(@RequestBody Reminder reminder, Authentication authentication) {
        return ResponseEntity.ok(reminderService.createReminder(reminder, authentication.getName()));
    }

    /** KAN-236: bell/panel polls this on app open. */
    @GetMapping("/due")
    public List<Reminder> getDueReminders(Authentication authentication) {
        return reminderService.getDueRemindersForCurrentUser(authentication.getName());
    }

    @PatchMapping("/{id}/trigger")
    public ResponseEntity<Void> markTriggered(@PathVariable UUID id, Authentication authentication) {
        if (reminderService.markTriggered(id, authentication.getName())) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(403).build();
    }
}