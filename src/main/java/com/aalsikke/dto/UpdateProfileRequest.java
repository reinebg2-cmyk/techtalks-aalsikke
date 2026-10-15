package com.aalsikke.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProfileRequest {
    @Size(max = 100, message = "Full name must not exceed 100 characters")
    @NotBlank(message = "Full name is required.")
    private String fullName;

    @NotBlank(message = "Email is required.")
    @Email(message = "Please enter a valid email address.")
    private String email;

    @NotBlank(message = "Profile mode is required.")
    @Pattern(regexp = "STUDENT|WORK|HYBRID", message = "Profile mode must be STUDENT, WORK, or HYBRID.")
    private String profileMode;

    /** Blank or missing = keep the current phone (KAN-257). Use remove-phone to delete it. */
    @Size(max = 50, message = "Phone number must be 50 characters or fewer.")
    private String phoneNumber;

    /** Required only when the email or phone number is being changed (KAN-257). */
    private String currentPassword;
}