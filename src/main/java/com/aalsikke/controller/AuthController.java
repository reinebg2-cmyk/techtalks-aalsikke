package com.aalsikke.controller;

import com.aalsikke.dto.ApiError;
import com.aalsikke.dto.AuthResponse;
import com.aalsikke.dto.LoginRequest;
import com.aalsikke.dto.RegisterRequest;
import com.aalsikke.exception.EmailAlreadyExistsException;
import com.aalsikke.exception.InvalidCredentialsException;
import com.aalsikke.exception.PhoneAlreadyExistsException;
import com.aalsikke.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            AuthResponse response = authService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (EmailAlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of("email", e.getMessage()));
        } catch (PhoneAlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of("phone", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiError.of(e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (InvalidCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError.of("login-password", e.getMessage()));
        }
    }
}
