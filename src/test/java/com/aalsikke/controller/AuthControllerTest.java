package com.aalsikke.controller;

import com.aalsikke.dto.AuthResponse;
import com.aalsikke.exception.EmailNotVerifiedException;
import com.aalsikke.security.CustomUserDetailsService;
import com.aalsikke.security.JwtService;
import com.aalsikke.service.AuthService;
import com.aalsikke.service.EmailVerificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** KAN-335: HTTP layer of /api/auth (real security config and error handler, mocked services, no database). */
@WebMvcTest(AuthController.class)
@Import(WebSecurityTestConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private EmailVerificationService emailVerificationService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Test
    void loginWithUnverifiedEmailReturns403WithCode() throws Exception {
        when(authService.login(any())).thenThrow(new EmailNotVerifiedException("new@example.com"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\",\"password\":\"Test12345!\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"))
                .andExpect(jsonPath("$.email").value("new@example.com"));
    }

    @Test
    void loginWithWrongPasswordReturns401WithGenericMessage() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid login details or password."));
    }

    @Test
    void loginWithRightPasswordReturnsToken() throws Exception {
        when(authService.login(any())).thenReturn(new AuthResponse("jwt-token", "Login successful"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"Test12345!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    @Test
    void registerWithInvalidInputReturns400WithFieldList() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"a b","email":"not-an-email","password":"short",
                                 "fullName":"","profileMode":"STUDENT"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[*].field", containsInAnyOrder("username", "email", "password", "fullName")))
                .andExpect(jsonPath("$[?(@.field == 'email')].message").value("Please enter a valid email address."));

        verifyNoInteractions(authService);   // nothing reaches the service when the input is invalid
    }

    @Test
    void registerWithEmptyBodyListsEveryRequiredField() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$[*].field",
                        containsInAnyOrder("username", "email", "password", "fullName", "profileMode")));

        verifyNoInteractions(authService);
    }

    @Test
    void currentUserWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(authService);
    }
}