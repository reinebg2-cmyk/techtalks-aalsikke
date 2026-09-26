package com.aalsikke.service;

import com.aalsikke.dto.LoginRequest;
import com.aalsikke.dto.AuthResponse;
import com.aalsikke.dto.RegisterRequest;
import com.aalsikke.model.User;
import com.aalsikke.repository.UserRepository;
import com.aalsikke.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.aalsikke.dto.UserProfileResponse;
import com.aalsikke.exception.EmailAlreadyExistsException;
import com.aalsikke.dto.UpdateProfileRequest;
import com.aalsikke.dto.ChangePasswordRequest;
import org.springframework.security.core.Authentication;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("This email is already registered.");
        }

        if (request.getUsername() != null && userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("This username is already taken.");
        }

        User user = new User();
        user.setFullName(request.getFullName() != null ? request.getFullName() : request.getUsername());
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setProfileMode(request.getProfileMode() != null ? request.getProfileMode() : "STUDENT");

        if (request.getDateOfBirth() != null && !request.getDateOfBirth().isBlank()) {
            user.setDateOfBirth(java.time.LocalDate.parse(request.getDateOfBirth()));
        }
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            user.setPhoneNumber(request.getPhoneNumber());
        }

        userRepository.save(user);

        String jwtToken = jwtService.generateToken(user.getEmail());
        return new AuthResponse(jwtToken, "User registered successfully");
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String jwtToken = jwtService.generateToken(user.getEmail());
        return new AuthResponse(jwtToken, "User authenticated successfully");
    }

    public UserProfileResponse getCurrentUserProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return new UserProfileResponse(
                user.getFullName(),
                user.getEmail(),
                user.getUsername(),
                user.getProfileMode(),
                user.getPhoneNumber(),
                user.getDateOfBirth()
        );
    }

    public void changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("New password must be different from your current password.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    public UserProfileResponse updateProfile(String currentEmail, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!request.getEmail().equalsIgnoreCase(user.getEmail())
                && userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("This email is already registered to another account.");
        }

        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setProfileMode(request.getProfileMode());

        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            userRepository.findByPhoneNumber(request.getPhoneNumber())
                    .ifPresent(existingUser -> {
                        if (!existingUser.getId().equals(user.getId())) {
                            throw new IllegalArgumentException("Phone number is already in use by another account.");
                        }
                    });
            user.setPhoneNumber(request.getPhoneNumber());
        } else {
            user.setPhoneNumber(null);
        }

        userRepository.save(user);

        return new UserProfileResponse(
                user.getFullName(),
                user.getEmail(),
                user.getUsername(),
                user.getProfileMode(),
                user.getPhoneNumber(),
                user.getDateOfBirth()
        );
    }
}