package com.aalsikke.dto;

import jakarta.validation.constraints.NotBlank;

/** KAN-338: deleting an account needs the current password and the word "DELETE". */
public record DeleteAccountRequest(
        @NotBlank(message = "Current password is required.")
        String currentPassword,

        String confirmation,

        String lang
) {}