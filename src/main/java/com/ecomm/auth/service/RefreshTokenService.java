package com.ecomm.auth.service;

import com.ecomm.auth.config.JwtConfigProperties;
import com.ecomm.auth.entity.User;
import com.ecomm.auth.entity.UserSession;
import com.ecomm.auth.exception.TokenRefreshException;
import com.ecomm.auth.repository.UserRepository;
import com.ecomm.auth.repository.UserSessionRepository;
import com.ecomm.auth.security.JwtTokenProvider;
import com.ecomm.auth.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final String REFRESH_PREFIX = "auth:refresh:";
    private static final String FAMILY_PREFIX = "auth:family:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserSessionRepository userSessionRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtConfigProperties jwtConfig;

    public RefreshTokenService(RedisTemplate<String, Object> redisTemplate,
                              UserSessionRepository userSessionRepository,
                              UserRepository userRepository,
                              JwtTokenProvider jwtTokenProvider,
                              JwtConfigProperties jwtConfig) {
        this.redisTemplate = redisTemplate;
        this.userSessionRepository = userSessionRepository;
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtConfig = jwtConfig;
    }

    @Transactional
    public String createRefreshToken(UserPrincipal principal, String clientIp, String userAgent) {
        String tokenId = UUID.randomUUID().toString();
        String familyId = UUID.randomUUID().toString();
        return storeAndGenerateToken(principal, tokenId, familyId, clientIp, userAgent);
    }

    @Transactional
    public RotatedTokens rotateRefreshToken(String rawRefreshToken, String clientIp, String userAgent) {
        if (!jwtTokenProvider.validateToken(rawRefreshToken)) {
            throw new TokenRefreshException("Refresh token is invalid or expired");
        }

        Map<String, Object> claims = jwtTokenProvider.parseAndVerifyClaims(rawRefreshToken);
        String tokenType = (String) claims.get("token_type");
        if (!"REFRESH".equals(tokenType)) {
            throw new TokenRefreshException("Token provided is not a refresh token");
        }

        String tokenId = (String) claims.get("jti");
        String familyId = (String) claims.get("family_id");
        String userUuid = (String) claims.get("sub");

        // Check if token exists in Redis
        String refreshKey = REFRESH_PREFIX + tokenId;
        String familyKey = FAMILY_PREFIX + familyId;

        Object currentActiveTokenId = redisTemplate.opsForValue().get(familyKey);

        if (currentActiveTokenId == null) {
            // Family expired or completely revoked
            revokeFamily(familyId);
            throw new TokenRefreshException("Session expired. Please log in again.");
        }

        if (!tokenId.equals(currentActiveTokenId)) {
            // Token Reuse Detected! A previously rotated token is being used again.
            log.error("CRITICAL SECURITY ALERT: Refresh token reuse detected for familyId: {} and user: {}. Revoking family!",
                familyId, userUuid);
            revokeFamily(familyId);
            throw new TokenRefreshException("Security violation: Token reuse detected. All active sessions in this chain have been revoked.");
        }

        // Token is valid and current: rotate!
        redisTemplate.delete(refreshKey);

        User user = userRepository.findByUuid(userUuid)
            .orElseThrow(() -> new TokenRefreshException("User not found for token"));

        UserPrincipal principal = UserPrincipal.create(user);
        String newTokenId = UUID.randomUUID().toString();
        String newRefreshToken = storeAndGenerateToken(principal, newTokenId, familyId, clientIp, userAgent);
        String newAccessToken = jwtTokenProvider.generateAccessToken(principal);

        return new RotatedTokens(newAccessToken, newRefreshToken, jwtTokenProvider.getAccessTokenExpirationSeconds());
    }

    private String storeAndGenerateToken(UserPrincipal principal, String tokenId, String familyId,
                                         String clientIp, String userAgent) {
        Duration ttl = Duration.ofDays(jwtConfig.getRefreshTokenExpirationDays());
        String refreshKey = REFRESH_PREFIX + tokenId;
        String familyKey = FAMILY_PREFIX + familyId;

        // Store in Redis
        redisTemplate.opsForValue().set(refreshKey, principal.getUuid(), ttl);
        redisTemplate.opsForValue().set(familyKey, tokenId, ttl);

        // Store session in MySQL
        User user = userRepository.findById(principal.getId()).orElse(null);
        if (user != null) {
            UserSession session = new UserSession();
            session.setTokenId(tokenId);
            session.setFamilyId(familyId);
            session.setUser(user);
            session.setIpAddress(clientIp);
            session.setUserAgent(userAgent);
            session.setExpiresAt(Instant.now().plus(jwtConfig.getRefreshTokenExpirationDays(), ChronoUnit.DAYS));
            userSessionRepository.save(session);
        }

        return jwtTokenProvider.generateRefreshToken(principal, tokenId, familyId);
    }

    @Transactional
    public void revokeToken(String rawRefreshToken) {
        try {
            if (jwtTokenProvider.validateToken(rawRefreshToken)) {
                Map<String, Object> claims = jwtTokenProvider.parseAndVerifyClaims(rawRefreshToken);
                String tokenId = (String) claims.get("jti");
                String familyId = (String) claims.get("family_id");
                if (tokenId != null) {
                    redisTemplate.delete(REFRESH_PREFIX + tokenId);
                }
                if (familyId != null) {
                    revokeFamily(familyId);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to revoke refresh token: {}", e.getMessage());
        }
    }

    @Transactional
    public void revokeFamily(String familyId) {
        if (familyId == null) return;
        redisTemplate.delete(FAMILY_PREFIX + familyId);
        userSessionRepository.revokeFamily(familyId);
    }

    @Transactional
    public void revokeAllUserSessions(Long userId) {
        userSessionRepository.revokeAllForUser(userId);
    }

    public static record RotatedTokens(String accessToken, String refreshToken, long expiresIn) {}
}

