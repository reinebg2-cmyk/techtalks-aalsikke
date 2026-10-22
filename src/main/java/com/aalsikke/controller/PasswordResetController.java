package com.aalsikke.controller; // TODO: use the package your auth controller is in

import com.aalsikke.service.PasswordResetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class PasswordResetController {


    // max 72: BCrypt ignores everything past 72 bytes.
    // TODO: replace with the same password rules your registration uses.
    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {}

    private final PasswordResetService service;

    public PasswordResetController(PasswordResetService service) {
        this.service = service;
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        service.requestReset(request.email(), request.lang());
        // identical answer whether or not the email exists
        return ResponseEntity.ok(Map.of("message",
                "If that email is registered, we've sent a reset link."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        service.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(Map.of("message",
                "Your password has been reset. You can log in now."));
    }
    /** Opening the link: 200 if it can still be used, 400 RESET_LINK_INVALID if not. Changes nothing. */
    @PostMapping("/reset-password/check")
    public ResponseEntity<Map<String, String>> checkResetLink(
            @RequestBody ResetLinkCheckRequest request) {
        service.checkResetLink(request.token());
        return ResponseEntity.ok(Map.of("message", "This reset link can be used."));
    }

    public record ResetLinkCheckRequest(String token) {}
    public record ForgotPasswordRequest(@NotBlank @Email String email, String lang) {}
}