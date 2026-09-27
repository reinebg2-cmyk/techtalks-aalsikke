package com.aalsikke.dto;

import jakarta.validation.constraints.NotBlank;

/** KAN-257: removing a phone is a separate, deliberate action that needs the password. */
public record RemovePhoneRequest(
        @NotBlank(message = "Current password is required.")
        String currentPassword
) {}