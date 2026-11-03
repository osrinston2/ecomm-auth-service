package com.ecomm.auth.controller;

import com.ecomm.auth.dto.request.LoginRequest;
import com.ecomm.auth.dto.request.RegisterRequest;
import com.ecomm.auth.dto.response.AuthResponse;
import com.ecomm.auth.dto.response.UserSummaryResponse;
import com.ecomm.auth.entity.RoleName;
import com.ecomm.auth.entity.UserStatus;
import com.ecomm.auth.security.JwtAuthenticationEntryPoint;
import com.ecomm.auth.security.JwtAuthenticationFilter;
import com.ecomm.auth.security.JwtTokenProvider;
import com.ecomm.auth.security.RsaKeyProvider;
import com.ecomm.auth.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.context.ActiveProfiles;

@WebMvcTest(controllers = {AuthController.class, JwksController.class})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private RsaKeyProvider rsaKeyProvider;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    @DisplayName("POST /api/v1/auth/register should register customer successfully")
    void shouldRegisterCustomer() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "buyer@ecomm.com",
            "SuperPassword123!",
            "Alice",
            "Smith",
            "+1000000000",
            RoleName.ROLE_CUSTOMER
        );

        UserSummaryResponse summary = new UserSummaryResponse(
            "uuid-999",
            "buyer@ecomm.com",
            "Alice",
            "Smith",
            Set.of("ROLE_CUSTOMER"),
            UserStatus.ACTIVE,
            false
        );

        AuthResponse authResponse = new AuthResponse(
            "access-jwt-token",
            "refresh-jwt-token",
            900L,
            summary
        );

        when(authService.register(any(), any(), any())).thenReturn(authResponse);

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").value("access-jwt-token"))
            .andExpect(jsonPath("$.data.user.email").value("buyer@ecomm.com"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login should authenticate user and return tokens")
    void shouldLoginUser() throws Exception {
        LoginRequest request = new LoginRequest("buyer@ecomm.com", "SuperPassword123!");

        UserSummaryResponse summary = new UserSummaryResponse(
            "uuid-999",
            "buyer@ecomm.com",
            "Alice",
            "Smith",
            Set.of("ROLE_CUSTOMER"),
            UserStatus.ACTIVE,
            true
        );

        AuthResponse authResponse = new AuthResponse(
            "access-jwt-token",
            "refresh-jwt-token",
            900L,
            summary
        );

        when(authService.login(any(), any(), any())).thenReturn(authResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").value("access-jwt-token"));
    }

    @Test
    @DisplayName("GET /api/v1/auth/.well-known/jwks.json should return RFC 7517 JWKS")
    void shouldReturnJwks() throws Exception {
        Map<String, Object> jwk = Map.of(
            "kty", "RSA",
            "use", "sig",
            "alg", "RS256",
            "kid", "ecomm-auth-key-1",
            "n", "sample-modulus",
            "e", "AQAB"
        );

        when(rsaKeyProvider.getJwk()).thenReturn(jwk);

        mockMvc.perform(get("/api/v1/auth/.well-known/jwks.json"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
            .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
            .andExpect(jsonPath("$.keys[0].kid").value("ecomm-auth-key-1"));
    }

    @Test
    @DisplayName("GET /api/v1/auth/me should return current user identity")
    void shouldReturnCurrentUserIdentity() throws Exception {
        UserSummaryResponse summary = new UserSummaryResponse(
            "uuid-999",
            "buyer@ecomm.com",
            "Alice",
            "Smith",
            Set.of("ROLE_CUSTOMER"),
            UserStatus.ACTIVE,
            true
        );

        when(authService.getCurrentUser(any())).thenReturn(summary);

        mockMvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.email").value("buyer@ecomm.com"))
            .andExpect(jsonPath("$.data.roles[0]").value("ROLE_CUSTOMER"));
    }
}
