package com.aalsikke.dto;

public class UserProfileResponse {

    private String fullName;
    private String email;
    private String username;
    private String profileMode;

    public UserProfileResponse(String fullName, String email, String username, String profileMode) {
        this.fullName = fullName;
        this.email = email;
        this.username = username;
        this.profileMode = profileMode;
    }

    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getUsername() { return username; }
    public String getProfileMode() { return profileMode; }
}