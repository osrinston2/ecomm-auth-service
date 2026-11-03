package com.ecomm.auth.security;

import com.ecomm.auth.config.JwtConfigProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private RsaKeyProvider rsaKeyProvider;
    private JwtConfigProperties configProperties;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        configProperties = new JwtConfigProperties();
        configProperties.setIssuer("test-ecomm-auth");
        configProperties.setAccessTokenExpirationMinutes(15);
        configProperties.setRefreshTokenExpirationDays(7);

        rsaKeyProvider = new RsaKeyProvider(configProperties);
        rsaKeyProvider.init();

        objectMapper = new ObjectMapper();
        jwtTokenProvider = new JwtTokenProvider(rsaKeyProvider, configProperties, objectMapper);
    }

    @Test
    @DisplayName("Should generate and validate a valid RS256 Access Token")
    void shouldGenerateAndValidateAccessToken() {
        UserPrincipal principal = new UserPrincipal(
            1L,
            "11111111-2222-3333-4444-555555555555",
            "buyer@example.com",
            "hashedpwd",
            com.ecomm.auth.entity.UserStatus.ACTIVE,
            true,
            Collections.singleton(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );

        String token = jwtTokenProvider.generateAccessToken(principal);
        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3);

        boolean isValid = jwtTokenProvider.validateToken(token);
        assertTrue(isValid);

        Map<String, Object> claims = jwtTokenProvider.parseAndVerifyClaims(token);
        assertEquals("test-ecomm-auth", claims.get("iss"));
        assertEquals("11111111-2222-3333-4444-555555555555", claims.get("sub"));
        assertEquals("buyer@example.com", claims.get("email"));
        assertEquals("ACCESS", claims.get("token_type"));
        assertNotNull(claims.get("jti"));
    }

    @Test
    @DisplayName("Should reject tampered JWT token")
    void shouldRejectTamperedToken() {
        UserPrincipal principal = new UserPrincipal(
            1L,
            "11111111-2222-3333-4444-555555555555",
            "buyer@example.com",
            "hashedpwd",
            com.ecomm.auth.entity.UserStatus.ACTIVE,
            true,
            Collections.singleton(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );

        String token = jwtTokenProvider.generateAccessToken(principal);
        String[] parts = token.split("\\.");
        // Tamper with payload
        String tamperedToken = parts[0] + "." + parts[1] + "X" + "." + parts[2];

        assertFalse(jwtTokenProvider.validateToken(tamperedToken));
    }

    @Test
    @DisplayName("Should expose valid JWKS matching RFC 7517")
    void shouldExposeValidJwk() {
        Map<String, Object> jwk = rsaKeyProvider.getJwk();
        assertNotNull(jwk);
        assertEquals("RSA", jwk.get("kty"));
        assertEquals("sig", jwk.get("use"));
        assertEquals("RS256", jwk.get("alg"));
        assertNotNull(jwk.get("kid"));
        assertNotNull(jwk.get("n"));
        assertNotNull(jwk.get("e"));
    }
}

