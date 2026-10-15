package com.aalsikke.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Username is required.")
    @Pattern(regexp = "^[A-Za-z0-9._]{3,30}$",
            message = "Username must be 3–30 characters: letters, numbers, dots or underscores only (no spaces).")
    private String username;

    @NotBlank(message = "Email is required.")
    @Email(message = "Please enter a valid email address.")
    private String email;

    @NotBlank(message = "Password is required.")
    @Size(min = 8, message = "Password must be at least 8 characters.")
    private String password;

    @NotBlank(message = "Full name is required.")
    private String fullName;

    @NotBlank(message = "Profile mode is required.")
    private String profileMode;

    private String dateOfBirth;
    private String phoneNumber;
}