package com.aalsikke.dto;

import com.aalsikke.model.User;

import java.util.UUID;

/**
 * What we hand back to the frontend after a successful register/login.
 * dashboard.js already reads a user id out of localStorage
 * (key "aalsikke_user_id") to call /api/schedule-items/user/{userId}, so
 * that's the field the frontend cares about most.
 */
public class AuthResponse {

    private UUID id;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String profileMode;

    public AuthResponse() {}

    public static AuthResponse fromUser(User user) {
        AuthResponse response = new AuthResponse();
        response.id = user.getId();
        response.fullName = user.getFullName();
        response.email = user.getEmail();
        response.phoneNumber = user.getPhoneNumber();
        response.profileMode = user.getProfileMode();
        return response;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getProfileMode() { return profileMode; }
    public void setProfileMode(String profileMode) { this.profileMode = profileMode; }
}
