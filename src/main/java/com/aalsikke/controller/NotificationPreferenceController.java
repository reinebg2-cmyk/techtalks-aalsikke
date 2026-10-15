package com.aalsikke.controller;
import com.aalsikke.dto.NotificationPreferencesRequest;
import com.aalsikke.dto.NotificationPreferencesResponse;
import com.aalsikke.service.NotificationPreferenceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/notification-preferences")
public class NotificationPreferenceController {
    private final NotificationPreferenceService preferenceService;

    public NotificationPreferenceController(NotificationPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping("/me")
    public ResponseEntity<NotificationPreferencesResponse> getMyPreferences(Authentication authentication) {
        return ResponseEntity.ok(preferenceService.getPreferences(authentication.getName()));
    }

    @PutMapping("/me")
    public ResponseEntity<NotificationPreferencesResponse> updateMyPreferences(
            @Valid @RequestBody NotificationPreferencesRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(preferenceService.updatePreferences(authentication.getName(), request));
    }
}
