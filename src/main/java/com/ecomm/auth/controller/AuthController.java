package com.ecomm.auth.controller;

import com.ecomm.auth.dto.request.*;
import com.ecomm.auth.dto.response.ApiResponse;
import com.ecomm.auth.dto.response.AuthResponse;
import com.ecomm.auth.dto.response.TokenRefreshResponse;
import com.ecomm.auth.dto.response.UserSummaryResponse;
import com.ecomm.auth.security.UserPrincipal;
import com.ecomm.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "User registration, authentication, token lifecycle, and account recovery")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    @Operation(
        summary = "Get current authenticated identity",
        description = "Retrieves the authenticated user identity claims, email, and assigned authorities from the RS256 token.",
        security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Identity claims successfully retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized - invalid or expired access token")
    })
    public ResponseEntity<ApiResponse<UserSummaryResponse>> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        UserSummaryResponse response = authService.getCurrentUser(principal);
        return ResponseEntity.ok(ApiResponse.ok("Current user identity retrieved", response));
    }

    @PostMapping("/register")
    @Operation(
        summary = "Register new account",
        description = "Registers a new Customer or Seller account and returns an RS256 Access Token and rotating Refresh Token.",
        security = {}
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Account created and tokens generated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error on payload"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email already registered")
    })
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request,
                                                             HttpServletRequest servletRequest) {
        String clientIp = extractClientIp(servletRequest);
        String userAgent = servletRequest.getHeader("User-Agent");
        AuthResponse response = authService.register(request, clientIp, userAgent);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Registration successful", response));
    }

    @PostMapping("/login")
    @Operation(
        summary = "Authenticate user",
        description = "Validates credentials against BCrypt hash, checks brute-force rate limit, and issues an RS256 access token and rotating refresh token.",
        security = {}
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Authentication successful"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid email or password"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Too many failed attempts - account temporarily locked")
    })
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request,
                                                          HttpServletRequest servletRequest) {
        String clientIp = extractClientIp(servletRequest);
        String userAgent = servletRequest.getHeader("User-Agent");
        AuthResponse response = authService.login(request, clientIp, userAgent);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", response));
    }

    @PostMapping("/refresh")
    @Operation(
        summary = "Rotate refresh token",
        description = "Validates the refresh token, performs token family reuse detection (replay attack defense), invalidates the old token, and issues a new token pair.",
        security = {}
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tokens successfully rotated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid, expired, or compromised token")
    })
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request,
                                                                         HttpServletRequest servletRequest) {
        String clientIp = extractClientIp(servletRequest);
        String userAgent = servletRequest.getHeader("User-Agent");
        TokenRefreshResponse response = authService.refreshToken(request, clientIp, userAgent);
        return ResponseEntity.ok(ApiResponse.ok("Token refreshed successfully", response));
    }

    @PostMapping("/logout")
    @Operation(
        summary = "Logout user",
        description = "Revokes the active refresh token family in Redis/DB and blacklists the access token JTI for its remaining validity.",
        security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully logged out")
    })
    public ResponseEntity<ApiResponse<Void>> logout(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                                   @RequestBody(required = false) RefreshTokenRequest refreshRequest,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        String bearerToken = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            bearerToken = authHeader.substring(7);
        }
        String refreshToken = (refreshRequest != null) ? refreshRequest.getRefreshToken() : null;
        authService.logout(bearerToken, refreshToken, principal);
        return ResponseEntity.ok(ApiResponse.ok("Logged out successfully"));
    }

    @PostMapping("/change-password")
    @Operation(
        summary = "Change password",
        description = "Validates current password, updates to new password hash, and revokes all active refresh token families across all devices.",
        security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password changed and all other sessions invalidated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Current password invalid or new password does not meet complexity requirements"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<Void>> changePassword(@AuthenticationPrincipal UserPrincipal principal,
                                                           @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.getId(), request);
        return ResponseEntity.ok(ApiResponse.ok("Password changed successfully"));
    }

    @PostMapping("/forgot-password")
    @Operation(
        summary = "Request password reset",
        description = "Generates a time-limited (15 minutes) password reset token in Redis. Emits reset token for client delivery.",
        security = {}
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password reset token generated")
    })
    public ResponseEntity<ApiResponse<String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                                             HttpServletRequest servletRequest) {
        String clientIp = extractClientIp(servletRequest);
        String userAgent = servletRequest.getHeader("User-Agent");
        String resetToken = authService.initiatePasswordReset(request, clientIp, userAgent);
        return ResponseEntity.ok(ApiResponse.ok("If the email exists, a password reset link has been dispatched.", resetToken));
    }

    @PostMapping("/reset-password")
    @Operation(
        summary = "Reset password with token",
        description = "Validates the reset token from Redis, updates the user's password, and revokes all active sessions.",
        security = {}
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password reset successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or expired reset token")
    })
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.completePasswordReset(request);
        return ResponseEntity.ok(ApiResponse.ok("Password has been reset successfully"));
    }

    private String extractClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty() || !xfHeader.contains(",")) {
            return (xfHeader != null && !xfHeader.isEmpty()) ? xfHeader : request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}

