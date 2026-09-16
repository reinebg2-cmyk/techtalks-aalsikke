package com.aalsikke.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProfileRequest {

    @NotBlank(message = "Full name is required.")
    private String fullName;

    @NotBlank(message = "Email is required.")
    @Email(message = "Please enter a valid email address.")
    private String email;

    @NotBlank(message = "Profile mode is required.")
    @Pattern(regexp = "STUDENT|WORK|HYBRID", message = "Profile mode must be STUDENT, WORK, or HYBRID.")
    private String profileMode;

    private String phoneNumber;
}