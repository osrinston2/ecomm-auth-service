package com.ecomm.auth.service;

import com.ecomm.auth.dto.request.*;
import com.ecomm.auth.dto.response.AuthResponse;
import com.ecomm.auth.dto.response.TokenRefreshResponse;
import com.ecomm.auth.dto.response.UserSummaryResponse;
import com.ecomm.auth.entity.*;
import com.ecomm.auth.exception.AuthException;
import com.ecomm.auth.exception.ResourceNotFoundException;
import com.ecomm.auth.repository.AuditLogRepository;
import com.ecomm.auth.repository.RoleRepository;
import com.ecomm.auth.repository.UserRepository;
import com.ecomm.auth.security.JwtTokenProvider;
import com.ecomm.auth.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String RESET_TOKEN_PREFIX = "auth:pwdreset:";
    private static final Duration RESET_TOKEN_EXPIRATION = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final RateLimiterService rateLimiterService;
    private final RedisTemplate<String, Object> redisTemplate;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       AuditLogRepository auditLogRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtTokenProvider jwtTokenProvider,
                       RefreshTokenService refreshTokenService,
                       TokenBlacklistService tokenBlacklistService,
                       RateLimiterService rateLimiterService,
                       RedisTemplate<String, Object> redisTemplate) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.rateLimiterService = rateLimiterService;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request, String clientIp, String userAgent) {
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new AuthException("Email address is already registered", HttpStatus.CONFLICT);
        }

        RoleName targetRole = (request.getRole() != null) ? request.getRole() : RoleName.ROLE_CUSTOMER;
        // Security guard: Prevent self-registration into privileged roles
        if (targetRole == RoleName.ROLE_ADMIN || targetRole == RoleName.ROLE_SUPPORT) {
            log.warn("Attempt to register with restricted role: {} by email: {}", targetRole, request.getEmail());
            targetRole = RoleName.ROLE_CUSTOMER;
        }
        final RoleName finalRole = targetRole;

        Role role = roleRepository.findByName(finalRole)
            .orElseGet(() -> roleRepository.save(new Role(finalRole, "Assigned during registration")));

        User user = new User();
        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(false);
        user.getRoles().add(role);

        User savedUser = userRepository.save(user);
        auditLogRepository.save(new AuditLog(savedUser.getId(), "REGISTER", clientIp, userAgent, "User registered successfully with role " + finalRole));

        UserPrincipal principal = UserPrincipal.create(savedUser);
        String accessToken = jwtTokenProvider.generateAccessToken(principal);
        String refreshToken = refreshTokenService.createRefreshToken(principal, clientIp, userAgent);

        return new AuthResponse(
            accessToken,
            refreshToken,
            jwtTokenProvider.getAccessTokenExpirationSeconds(),
            mapToSummary(savedUser)
        );
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String clientIp, String userAgent) {
        String rateLimitKey = clientIp + ":" + request.getEmail().toLowerCase();
        rateLimiterService.checkLoginRateLimit(rateLimitKey);

        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail().toLowerCase(), request.getPassword())
            );

            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            rateLimiterService.clearLoginAttempts(rateLimitKey);
            auditLogRepository.save(new AuditLog(user.getId(), "LOGIN_SUCCESS", clientIp, userAgent, "Successful login"));

            String accessToken = jwtTokenProvider.generateAccessToken(principal);
            String refreshToken = refreshTokenService.createRefreshToken(principal, clientIp, userAgent);

            return new AuthResponse(
                accessToken,
                refreshToken,
                jwtTokenProvider.getAccessTokenExpirationSeconds(),
                mapToSummary(user)
            );
        } catch (AuthenticationException e) {
            rateLimiterService.recordFailedAttempt(rateLimitKey);
            auditLogRepository.save(new AuditLog(null, "LOGIN_FAILED", clientIp, userAgent, "Failed login attempt for email: " + request.getEmail()));
            throw new BadCredentialsException("Invalid email or password");
        }
    }

    @Transactional
    public TokenRefreshResponse refreshToken(RefreshTokenRequest request, String clientIp, String userAgent) {
        RefreshTokenService.RotatedTokens tokens = refreshTokenService.rotateRefreshToken(request.getRefreshToken(), clientIp, userAgent);
        return new TokenRefreshResponse(tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn());
    }

    @Transactional
    public void logout(String bearerToken, String refreshToken, UserPrincipal principal) {
        if (bearerToken != null) {
            try {
                if (jwtTokenProvider.validateToken(bearerToken)) {
                    String jti = jwtTokenProvider.getJtiFromToken(bearerToken);
                    long exp = jwtTokenProvider.getExpirationFromToken(bearerToken);
                    long remainingSeconds = exp - Instant.now().getEpochSecond();
                    if (remainingSeconds > 0) {
                        tokenBlacklistService.blacklistToken(jti, remainingSeconds);
                    }
                }
            } catch (Exception e) {
                log.warn("Error processing access token during logout: {}", e.getMessage());
            }
        }

        if (refreshToken != null) {
            refreshTokenService.revokeToken(refreshToken);
        }

        if (principal != null) {
            auditLogRepository.save(new AuditLog(principal.getId(), "LOGOUT", null, null, "User logged out"));
        }
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new AuthException("Current password does not match", HttpStatus.BAD_REQUEST);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Invalidate all active sessions across all devices for security
        refreshTokenService.revokeAllUserSessions(userId);
        auditLogRepository.save(new AuditLog(userId, "PASSWORD_CHANGE", null, null, "Password changed and all sessions invalidated"));
    }

    public String initiatePasswordReset(ForgotPasswordRequest request, String clientIp, String userAgent) {
        Optional<User> userOpt = userRepository.findByEmail(request.getEmail().toLowerCase());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            String resetToken = UUID.randomUUID().toString();
            String redisKey = RESET_TOKEN_PREFIX + resetToken;
            redisTemplate.opsForValue().set(redisKey, user.getEmail(), RESET_TOKEN_EXPIRATION);
            auditLogRepository.save(new AuditLog(user.getId(), "PASSWORD_RESET_REQUEST", clientIp, userAgent, "Password reset initiated"));
            log.info("Password reset token generated for user: {}", user.getEmail());
            return resetToken;
        }
        // Timing-safe behavior: do not reveal if email exists
        return UUID.randomUUID().toString();
    }

    @Transactional
    public void completePasswordReset(ResetPasswordRequest request) {
        String redisKey = RESET_TOKEN_PREFIX + request.getToken();
        Object emailObj = redisTemplate.opsForValue().get(redisKey);

        if (emailObj == null) {
            throw new AuthException("Password reset token is invalid or has expired", HttpStatus.BAD_REQUEST);
        }

        String email = emailObj.toString();
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        redisTemplate.delete(redisKey);
        refreshTokenService.revokeAllUserSessions(user.getId());
        auditLogRepository.save(new AuditLog(user.getId(), "PASSWORD_RESET_SUCCESS", null, null, "Password reset completed"));
    }

    public UserSummaryResponse mapToSummary(User user) {
        Set<String> roles = user.getRoles().stream()
            .map(role -> role.getName().name())
            .collect(Collectors.toSet());

        return new UserSummaryResponse(
            user.getUuid(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            roles,
            user.getStatus(),
            user.isEmailVerified()
        );
    }
}
