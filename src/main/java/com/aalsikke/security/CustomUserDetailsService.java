package com.aalsikke.security;

import com.aalsikke.model.User;
import com.aalsikke.repository.UserRepository;
import com.aalsikke.util.PhoneNumbers;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @NonNull
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        // Login accepts email, then phone, then username (first match wins).
        User user = userRepository.findByEmail(identifier)
                .or(() -> findByPhone(identifier))
                .or(() -> findByUsername(identifier))
                .orElseThrow(() -> new UsernameNotFoundException("No user found with email, phone number or username: " + identifier));

        boolean enabled = "ACTIVE".equalsIgnoreCase(user.getAccountStatus()) && user.getDeletedAt() == null;
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .disabled(!enabled)
                .build();
    }

    /**
     * KAN-257: phones are stored as +961..., so normalize what the user typed
     * ("03 000 000", "+961 3 000 000", ...) before looking it up.
     * Anything that isn't a valid phone (e.g. an unknown email) just means "not found",
     * so a bad login stays a normal 401 instead of becoming a 500.
     */
    private Optional<User> findByPhone(String identifier) {
        if (identifier == null || identifier.contains("@")) {
            return Optional.empty();
        }
        try {
            String normalized = PhoneNumbers.normalize(identifier);
            return normalized == null ? Optional.empty() : userRepository.findByPhoneNumber(normalized);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }


    /** Usernames never contain "@", so an email-looking value skips this lookup. */
    private Optional<User> findByUsername(String identifier) {
        if (identifier == null || identifier.isBlank() || identifier.contains("@")) {
            return Optional.empty();
        }
        return userRepository.findByUsername(identifier.trim());
    }
}