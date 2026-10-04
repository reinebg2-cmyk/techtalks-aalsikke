package com.aalsikke.service;

import com.aalsikke.dto.AuthResponse;
import com.aalsikke.dto.ChangePasswordRequest;
import com.aalsikke.dto.LoginRequest;
import com.aalsikke.dto.ProfileUpdateResponse;
import com.aalsikke.dto.RegisterRequest;
import com.aalsikke.dto.RemovePhoneRequest;
import com.aalsikke.dto.UpdateProfileRequest;
import com.aalsikke.dto.UserProfileResponse;
import com.aalsikke.exception.EmailAlreadyExistsException;
import com.aalsikke.exception.EmailNotVerifiedException;
import com.aalsikke.model.User;
import com.aalsikke.repository.UserRepository;
import com.aalsikke.security.JwtService;
import com.aalsikke.util.PhoneNumbers;
import com.aalsikke.exception.PhoneAlreadyExistsException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final EmailVerificationService emailVerificationService; // KAN-262

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager,
                       EmailVerificationService emailVerificationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.emailVerificationService = emailVerificationService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("This email is already registered.");
        }

        if (request.getUsername() != null && userRepository.existsByUsername(request.getUsername())) {
            throw new com.aalsikke.exception.UsernameAlreadyExistsException("This username is already taken.");
        }

        // KAN-257: store phones in one format and give a clear error for duplicates
        String phone = PhoneNumbers.normalize(request.getPhoneNumber());
        if (phone != null && userRepository.existsByPhoneNumber(phone)) {
            throw new PhoneAlreadyExistsException("Phone number is already in use by another account.");
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
        user.setPhoneNumber(phone);

        userRepository.save(user);

        // KAN-272: new accounts start unverified and get a verification link.
        // No token is returned: the user can log in once the email is verified (KAN-274).
        emailVerificationService.sendVerification(user);
        return new AuthResponse(null, "Account created. Check your email for a verification link, then log in.");
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

        // KAN-274: checked after the password, so this never reveals anything to someone
        // who doesn't know the password.
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException(user.getEmail());
        }

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
            throw new com.aalsikke.exception.InvalidCurrentPasswordException("Current password is incorrect.");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("New password must be different from your current password.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    /**
     * KAN-257 rules:
     * - blank/missing phone = keep the current one (use removePhone to delete it)
     *      * - changing the full name, email or phone requires the current password
     * - a new phone resets phoneVerified; a new email resets emailVerified
     * - a new email returns a new JWT (the subject is the email, the old token stops working)
     */
    @Transactional
    public ProfileUpdateResponse updateProfile(String currentEmail, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String newEmail = request.getEmail().trim();
        boolean emailChanged = !newEmail.equalsIgnoreCase(user.getEmail());

        String newPhone = PhoneNumbers.normalize(request.getPhoneNumber()); // null = keep; throws if invalid
        boolean phoneChanged = newPhone != null && !newPhone.equals(user.getPhoneNumber());

        // The name is shown to others and identifies the account, so it is protected too.
        String newFullName = request.getFullName().trim();
        String oldFullName = user.getFullName() == null ? "" : user.getFullName().trim();
        boolean nameChanged = !newFullName.equals(oldFullName);

        if (emailChanged || phoneChanged || nameChanged) {
            requireCurrentPassword(user, request.getCurrentPassword());
        }

        if (emailChanged && userRepository.existsByEmail(newEmail)) {
            throw new EmailAlreadyExistsException("This email is already registered to another account.");
        }

        if (phoneChanged) {
            userRepository.findByPhoneNumber(newPhone)
                    .filter(existing -> !existing.getId().equals(user.getId()))
                    .ifPresent(existing -> {
                        throw new PhoneAlreadyExistsException("Phone number is already in use by another account.");
                    });
            user.setPhoneNumber(newPhone);
            user.setPhoneVerified(false);
        }

        if (emailChanged) {
            user.setEmail(newEmail);
            user.setEmailVerified(false);
        }

        user.setFullName(newFullName);
        user.setProfileMode(request.getProfileMode());
        userRepository.save(user);

        // KAN-262: a new email starts unverified, so send a link to the new address right away.
        if (emailChanged) {
            emailVerificationService.sendVerification(user);
        }

        String newToken = emailChanged ? jwtService.generateToken(user.getEmail()) : null;
        return toProfileUpdateResponse(user, newToken);
    }

    /** KAN-257: removing a phone is deliberate, needs the password, and is blocked while 2FA is on. */
    @Transactional
    public ProfileUpdateResponse removePhone(String email, RemovePhoneRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        requireCurrentPassword(user, request.currentPassword());

        if (user.isTwoFactorEnabled()) {
            throw new IllegalArgumentException("Turn off two-factor authentication before removing your phone number.");
        }

        user.setPhoneNumber(null);
        user.setPhoneVerified(false);
        userRepository.save(user);

        return toProfileUpdateResponse(user, null);
    }

    private void requireCurrentPassword(User user, String currentPassword) {
        if (currentPassword == null || currentPassword.isBlank()) {
            throw new IllegalArgumentException("Current password is required to change your name, email or phone number.");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
    }

    private ProfileUpdateResponse toProfileUpdateResponse(User user, String token) {
        return new ProfileUpdateResponse(
                user.getFullName(),
                user.getEmail(),
                user.getUsername(),
                user.getProfileMode(),
                user.getPhoneNumber(),
                user.getDateOfBirth(),
                token
        );
    }
}