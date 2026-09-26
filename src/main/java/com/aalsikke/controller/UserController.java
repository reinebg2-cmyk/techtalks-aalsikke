package com.aalsikke.controller;

import com.aalsikke.dto.UserProfileResponse;
import com.aalsikke.model.User;
import com.aalsikke.repository.UserRepository;
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
}