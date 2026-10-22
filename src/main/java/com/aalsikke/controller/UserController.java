package com.aalsikke.controller;

import com.aalsikke.config.AppTime;
import com.aalsikke.dto.ColorPaletteRequest;
import com.aalsikke.dto.ColorPaletteResponse;
import com.aalsikke.dto.DeleteAccountRequest;
import com.aalsikke.dto.UserProfileResponse;
import com.aalsikke.service.AccountService;
import com.aalsikke.model.User;
import com.aalsikke.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final AccountService accountService;

    public UserController(UserRepository userRepository, AccountService accountService) {
        this.userRepository = userRepository;
        this.accountService = accountService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUserProfile(Authentication authentication) {

        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserProfileResponse response = new UserProfileResponse(
                user.getFullName(),
                user.getEmail(),
                user.getUsername(),
                user.getProfileMode(),
                user.getPhoneNumber(),
                user.getDateOfBirth()
        );

        return ResponseEntity.ok(response);
    }
    /** KAN-291: the signed-in user's saved color palette. */
    @GetMapping("/me/palette")
    public ResponseEntity<ColorPaletteResponse> getPalette(Authentication authentication) {
        User user = findCurrentUser(authentication);
        return ResponseEntity.ok(new ColorPaletteResponse(user.getColorPalette()));
    }

    /** KAN-291: saves the signed-in user's color palette (validated against the preset list). */
    @PutMapping("/me/palette")
    public ResponseEntity<ColorPaletteResponse> updatePalette(@Valid @RequestBody ColorPaletteRequest request,
                                                              Authentication authentication) {
        User user = findCurrentUser(authentication);
        user.setColorPalette(request.getPalette());
        userRepository.save(user);
        return ResponseEntity.ok(new ColorPaletteResponse(user.getColorPalette()));
    }

    /** KAN-338: everything the signed-in user has stored, downloaded as a JSON file. */
    @GetMapping("/me/export")
    public ResponseEntity<Map<String, Object>> exportMyData(Authentication authentication) {
        Map<String, Object> data = accountService.exportData(authentication.getName());
        String fileName = AccountService.exportFileName(AppTime.today());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(data);
    }

    /** KAN-338: deletes the signed-in user's account and all of its data (needs the password and "DELETE"). */
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMyAccount(@Valid @RequestBody DeleteAccountRequest request,
                                                Authentication authentication) {
        accountService.deleteAccount(authentication.getName(), request.currentPassword(), request.confirmation(), request.lang());
        return ResponseEntity.noContent().build();
    }
    private User findCurrentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
