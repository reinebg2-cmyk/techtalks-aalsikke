package com.aalsikke.controller;

import com.aalsikke.dto.ColorPaletteRequest;
import com.aalsikke.dto.ColorPaletteResponse;
import com.aalsikke.dto.UserProfileResponse;
import com.aalsikke.model.User;
import com.aalsikke.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
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
    private User findCurrentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
