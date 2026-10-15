package com.aalsikke.dto;

import java.time.LocalDate;

/**
 * KAN-257: same fields as UserProfileResponse, plus a new JWT when the email changed.
 * The JWT subject is the email, so the old token stops working after an email change;
 * the frontend must replace its stored token when this field is not null.
 */
public record ProfileUpdateResponse(
        String fullName,
        String email,
        String username,
        String profileMode,
        String phoneNumber,
        LocalDate dateOfBirth,
        String token
) {}