package com.cloudstore.controller;

import com.cloudstore.dto.auth.AuthResponse;
import com.cloudstore.dto.auth.RegisterRequest;
import com.cloudstore.security.CustomUserDetailsService;
import com.cloudstore.security.JwtAuthenticationFilter;
import com.cloudstore.security.JwtService;
import com.cloudstore.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    // Security beans are required to load the filter chain in a sliced test context.
    @MockBean
    private JwtService jwtService;
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void register_returns201AndToken_forValidRequest() throws Exception {
        RegisterRequest request = new RegisterRequest("jane@example.com", "Jane Doe", "password123");
        AuthResponse response = AuthResponse.of("token123", 3600, "jane@example.com", "CUSTOMER");
        when(authService.register(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("token123"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void register_returns400_whenEmailIsInvalid() throws Exception {
        String badRequest = """
                {"email":"not-an-email","fullName":"Jane","password":"password123"}
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(badRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_returns400_whenPasswordTooShort() throws Exception {
        String badRequest = """
                {"email":"jane@example.com","fullName":"Jane","password":"short"}
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(badRequest))
                .andExpect(status().isBadRequest());
    }
}
