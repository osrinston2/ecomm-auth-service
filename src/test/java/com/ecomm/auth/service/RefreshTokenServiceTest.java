package com.ecomm.auth.service;

import com.ecomm.auth.config.JwtConfigProperties;
import com.ecomm.auth.entity.User;
import com.ecomm.auth.exception.TokenRefreshException;
import com.ecomm.auth.repository.UserRepository;
import com.ecomm.auth.repository.UserSessionRepository;
import com.ecomm.auth.security.JwtTokenProvider;
import com.ecomm.auth.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private UserSessionRepository userSessionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private JwtConfigProperties jwtConfig;
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        jwtConfig = new JwtConfigProperties();
        jwtConfig.setRefreshTokenExpirationDays(7);
        jwtConfig.setAccessTokenExpirationMinutes(15);

        refreshTokenService = new RefreshTokenService(
            redisTemplate,
            userSessionRepository,
            userRepository,
            jwtTokenProvider,
            jwtConfig
        );
    }

    @Test
    @DisplayName("Should detect token reuse and revoke token family on replay attack")
    void shouldDetectReuseAndRevokeFamily() {
        String rawToken = "sample.refresh.token";
        when(jwtTokenProvider.validateToken(rawToken)).thenReturn(true);

        Map<String, Object> claims = Map.of(
            "token_type", "REFRESH",
            "jti", "old-token-id",
            "family_id", "family-123",
            "sub", "user-uuid-456"
        );
        when(jwtTokenProvider.parseAndVerifyClaims(rawToken)).thenReturn(claims);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // Redis says current active token for this family is "new-token-id", not "old-token-id"
        when(valueOperations.get("auth:family:family-123")).thenReturn("new-token-id");

        TokenRefreshException ex = assertThrows(TokenRefreshException.class, () ->
            refreshTokenService.rotateRefreshToken(rawToken, "127.0.0.1", "HackerAgent")
        );

        assertTrue(ex.getMessage().contains("Token reuse detected"));
        // Verify family was revoked from Redis and DB
        verify(redisTemplate).delete("auth:family:family-123");
        verify(userSessionRepository).revokeFamily("family-123");
    }

    @Test
    @DisplayName("Should successfully rotate refresh token when valid")
    void shouldRotateValidRefreshToken() {
        String rawToken = "valid.refresh.token";
        when(jwtTokenProvider.validateToken(rawToken)).thenReturn(true);

        Map<String, Object> claims = Map.of(
            "token_type", "REFRESH",
            "jti", "current-token-id",
            "family_id", "family-123",
            "sub", "user-uuid-456"
        );
        when(jwtTokenProvider.parseAndVerifyClaims(rawToken)).thenReturn(claims);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:family:family-123")).thenReturn("current-token-id");

        User user = new User();
        user.setId(10L);
        user.setUuid("user-uuid-456");
        user.setEmail("user@example.com");
        when(userRepository.findByUuid("user-uuid-456")).thenReturn(Optional.of(user));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        when(jwtTokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("new-access-token");
        when(jwtTokenProvider.generateRefreshToken(any(UserPrincipal.class), any(), eq("family-123"))).thenReturn("new-refresh-token");
        when(jwtTokenProvider.getAccessTokenExpirationSeconds()).thenReturn(900L);

        RefreshTokenService.RotatedTokens result = refreshTokenService.rotateRefreshToken(rawToken, "127.0.0.1", "ValidAgent");

        assertNotNull(result);
        assertEquals("new-access-token", result.accessToken());
        assertEquals("new-refresh-token", result.refreshToken());
        verify(redisTemplate).delete("auth:refresh:current-token-id");
        verify(userSessionRepository).save(any());
    }
}

