package com.aalsikke.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required.")
    private String currentPassword;

    @NotBlank(message = "New password is required.")
    @Size(min = 8, message = "New password must be at least 8 characters.")
    private String newPassword;

    // Optional: "en" or "ar". Used to pick the language of the security-alert email.
    private String lang;

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }

    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }
}