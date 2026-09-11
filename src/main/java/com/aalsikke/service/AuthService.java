package com.aalsikke.service;

import com.aalsikke.dto.AuthResponse;
import com.aalsikke.dto.LoginRequest;
import com.aalsikke.dto.RegisterRequest;
import com.aalsikke.exception.EmailAlreadyExistsException;
import com.aalsikke.exception.InvalidCredentialsException;
import com.aalsikke.exception.PhoneAlreadyExistsException;
import com.aalsikke.model.User;
import com.aalsikke.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class AuthService {

    private static final int MIN_AGE_YEARS = 13;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final Set<String> ALLOWED_ROLES = Set.of("student", "work", "hybrid");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String fullName = trimToNull(request.getFullname());
        String email = normalizeEmail(request.getEmail());
        String phone = trimToNull(request.getPhone());
        String password = request.getPassword();

        if (fullName == null || fullName.length() < 2) {
            throw new IllegalArgumentException("Please enter your full name.");
        }
        if (email == null && phone == null) {
            throw new IllegalArgumentException("An email address or phone number is required.");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }

        LocalDate dob = parseDob(request.getDob());
        if (dob != null && Period.between(dob, LocalDate.now()).getYears() < MIN_AGE_YEARS) {
            throw new IllegalArgumentException("You must be at least " + MIN_AGE_YEARS + " years old to register.");
        }

        if (email != null && userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException("This email is already registered. Try logging in.");
        }
        if (phone != null && userRepository.existsByPhoneNumber(phone)) {
            throw new PhoneAlreadyExistsException("This phone number is already registered. Try logging in.");
        }

        User user = new User();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPhoneNumber(phone);
        user.setDateOfBirth(dob);
        user.setProfileMode(mapRole(request.getRole()));
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setAccountStatus("ACTIVE");

        User saved = userRepository.save(user);
        return AuthResponse.fromUser(saved);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String identifier = trimToNull(request.getIdentifier());
        String password = request.getPassword();

        if (identifier == null || password == null || password.isEmpty()) {
            throw new InvalidCredentialsException("Email/phone and password are required.");
        }

        User user = findByIdentifier(identifier)
                .orElseThrow(() -> new InvalidCredentialsException("No account found for that email or phone number."));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Incorrect password.");
        }
        if ("SUSPENDED".equals(user.getAccountStatus()) || "DELETED".equals(user.getAccountStatus())) {
            throw new InvalidCredentialsException("This account is not active. Contact support for help.");
        }

        return AuthResponse.fromUser(user);
    }

    private Optional<User> findByIdentifier(String identifier) {
        if (identifier.contains("@")) {
            return userRepository.findByEmail(identifier.toLowerCase(Locale.ROOT));
        }
        // Not email-shaped: try phone number, then fall back to username
        // so a value typed into either field still works.
        Optional<User> byPhone = userRepository.findByPhoneNumber(identifier);
        if (byPhone.isPresent()) {
            return byPhone;
        }
        return userRepository.findByUsername(identifier);
    }

    private String mapRole(String role) {
        String normalized = role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_ROLES.contains(normalized)) {
            throw new IllegalArgumentException("Role must be one of: student, work, hybrid.");
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private LocalDate parseDob(String dob) {
        if (dob == null || dob.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dob.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Date of birth must be a valid date (YYYY-MM-DD).");
        }
    }

    private String normalizeEmail(String email) {
        String trimmed = trimToNull(email);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
