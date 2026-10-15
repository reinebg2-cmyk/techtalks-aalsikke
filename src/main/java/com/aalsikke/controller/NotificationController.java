package com.aalsikke.controller;

import com.aalsikke.model.Notification;
import com.aalsikke.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/me")
    public List<Notification> getMyNotifications(Authentication authentication) {
        return notificationService.getNotificationsForCurrentUser(authentication.getName());
    }

    @PostMapping
    public ResponseEntity<Notification> createNotification(@RequestBody Notification notification, Authentication authentication) {
        return ResponseEntity.ok(notificationService.createNotification(notification, authentication.getName()));
    }
}