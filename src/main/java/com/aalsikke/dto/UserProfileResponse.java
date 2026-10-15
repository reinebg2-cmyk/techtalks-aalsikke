package com.aalsikke.dto;

import java.time.LocalDate;

public class UserProfileResponse {

    private String fullName;
    private String email;
    private String username;
    private String profileMode;
    private String phoneNumber;
    private LocalDate dateOfBirth;

    public UserProfileResponse(String fullName, String email, String username, String profileMode,
                               String phoneNumber, LocalDate dateOfBirth) {
        this.fullName = fullName;
        this.email = email;
        this.username = username;
        this.profileMode = profileMode;
        this.phoneNumber = phoneNumber;
        this.dateOfBirth = dateOfBirth;
    }

    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getUsername() { return username; }
    public String getProfileMode() { return profileMode; }
    public String getPhoneNumber() { return phoneNumber; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
}