package com.aalsikke.controller;

import com.aalsikke.dto.AuthResponse;
import com.aalsikke.dto.ChangePasswordRequest;
import com.aalsikke.dto.LoginRequest;
import com.aalsikke.dto.ProfileUpdateResponse;
import com.aalsikke.dto.RegisterRequest;
import com.aalsikke.dto.RemovePhoneRequest;
import com.aalsikke.dto.UpdateProfileRequest;
import com.aalsikke.dto.UserProfileResponse;
import com.aalsikke.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(Authentication authentication) {
        return ResponseEntity.ok(authService.getCurrentUserProfile(authentication.getName()));
    }

    /** KAN-257: returns a new token in "token" when the email changed; null otherwise. */
    @PutMapping("/profile")
    public ResponseEntity<ProfileUpdateResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(authService.updateProfile(authentication.getName(), request));
    }

    /** KAN-257: deliberate phone removal; needs the password, blocked while 2FA is on. */
    @PostMapping("/profile/remove-phone")
    public ResponseEntity<ProfileUpdateResponse> removePhone(
            @Valid @RequestBody RemovePhoneRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(authService.removePhone(authentication.getName(), request));
    }

    @PutMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                               Authentication authentication) {
        authService.changePassword(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }
}