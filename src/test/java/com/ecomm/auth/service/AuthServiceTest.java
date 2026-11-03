package com.ecomm.auth.service;

import com.ecomm.auth.dto.request.LoginRequest;
import com.ecomm.auth.dto.request.RegisterRequest;
import com.ecomm.auth.dto.response.AuthResponse;
import com.ecomm.auth.entity.Role;
import com.ecomm.auth.entity.RoleName;
import com.ecomm.auth.entity.User;
import com.ecomm.auth.entity.UserStatus;
import com.ecomm.auth.exception.AuthException;
import com.ecomm.auth.repository.AuditLogRepository;
import com.ecomm.auth.repository.RoleRepository;
import com.ecomm.auth.repository.UserRepository;
import com.ecomm.auth.security.JwtTokenProvider;
import com.ecomm.auth.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private TokenBlacklistService tokenBlacklistService;
    @Mock
    private RateLimiterService rateLimiterService;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
            userRepository,
            roleRepository,
            auditLogRepository,
            passwordEncoder,
            authenticationManager,
            jwtTokenProvider,
            refreshTokenService,
            tokenBlacklistService,
            rateLimiterService,
            redisTemplate
        );
    }

    @Test
    @DisplayName("Should successfully register a new customer")
    void shouldRegisterNewCustomer() {
        RegisterRequest request = new RegisterRequest(
            "john.doe@example.com",
            "SecurePassword123!",
            "John",
            "Doe",
            "+1234567890",
            RoleName.ROLE_CUSTOMER
        );

        when(userRepository.existsByEmailIgnoreCase(request.getEmail())).thenReturn(false);
        Role customerRole = new Role(RoleName.ROLE_CUSTOMER, "Standard customer");
        when(roleRepository.findByName(RoleName.ROLE_CUSTOMER)).thenReturn(Optional.of(customerRole));
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encodedPassword");

        User savedUser = new User();
        savedUser.setId(1L);
        savedUser.setUuid("uuid-12345");
        savedUser.setEmail(request.getEmail());
        savedUser.setFirstName(request.getFirstName());
        savedUser.setLastName(request.getLastName());
        savedUser.setStatus(UserStatus.ACTIVE);
        savedUser.getRoles().add(customerRole);

        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtTokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("access-token-xyz");
        when(refreshTokenService.createRefreshToken(any(UserPrincipal.class), any(), any())).thenReturn("refresh-token-abc");
        when(jwtTokenProvider.getAccessTokenExpirationSeconds()).thenReturn(900L);

        AuthResponse response = authService.register(request, "127.0.0.1", "JUnit-Agent");

        assertNotNull(response);
        assertEquals("access-token-xyz", response.getAccessToken());
        assertEquals("refresh-token-abc", response.getRefreshToken());
        assertEquals("john.doe@example.com", response.getUser().getEmail());
        verify(auditLogRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Should throw AuthException when registering with duplicate email")
    void shouldThrowWhenDuplicateEmail() {
        RegisterRequest request = new RegisterRequest(
            "existing@example.com",
            "SecurePassword123!",
            "Jane",
            "Doe",
            null,
            RoleName.ROLE_CUSTOMER
        );

        when(userRepository.existsByEmailIgnoreCase("existing@example.com")).thenReturn(true);

        assertThrows(AuthException.class, () -> authService.register(request, "127.0.0.1", "JUnit-Agent"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should login successfully with valid credentials")
    void shouldLoginSuccessfully() {
        LoginRequest request = new LoginRequest("user@example.com", "Password123!");

        User user = new User();
        user.setId(1L);
        user.setUuid("uuid-12345");
        user.setEmail(request.getEmail());
        user.setFirstName("Alice");
        user.setLastName("Smith");
        user.setStatus(UserStatus.ACTIVE);

        UserPrincipal principal = UserPrincipal.create(user);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("login-access-token");
        when(refreshTokenService.createRefreshToken(any(UserPrincipal.class), any(), any())).thenReturn("login-refresh-token");
        when(jwtTokenProvider.getAccessTokenExpirationSeconds()).thenReturn(900L);

        AuthResponse response = authService.login(request, "127.0.0.1", "JUnit-Agent");

        assertNotNull(response);
        assertEquals("login-access-token", response.getAccessToken());
        verify(rateLimiterService).clearLoginAttempts(anyString());
        verify(auditLogRepository).save(any());
    }

    @Test
    @DisplayName("Should record failed attempt when login credentials are bad")
    void shouldRecordFailedAttemptOnBadCredentials() {
        LoginRequest request = new LoginRequest("user@example.com", "WrongPassword");

        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class, () -> authService.login(request, "127.0.0.1", "JUnit-Agent"));
        verify(rateLimiterService).recordFailedAttempt(anyString());
    }
}

