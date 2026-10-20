package com.aalsikke.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LoginRequest {
    private String email;
    private String password;
    private String lang;
    // Boolean, not boolean: Jackson builds this class through its all-args constructor,
    // and a missing primitive there is rejected as a malformed body (400 on every old login).
    private Boolean rememberMe;   // null when the frontend doesn't send it = not remembered
}