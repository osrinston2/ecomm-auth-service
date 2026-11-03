package com.ecomm.auth.security;

import com.ecomm.auth.config.JwtConfigProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final RsaKeyProvider rsaKeyProvider;
    private final JwtConfigProperties jwtConfig;
    private final ObjectMapper objectMapper;

    public JwtTokenProvider(RsaKeyProvider rsaKeyProvider,
                            JwtConfigProperties jwtConfig,
                            ObjectMapper objectMapper) {
        this.rsaKeyProvider = rsaKeyProvider;
        this.jwtConfig = jwtConfig;
        this.objectMapper = objectMapper;
    }

    public String generateAccessToken(UserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtConfig.getAccessTokenExpirationMinutes(), ChronoUnit.MINUTES);
        String jti = UUID.randomUUID().toString();

        List<String> roles = principal.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toList());

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", jwtConfig.getIssuer());
        claims.put("sub", principal.getUuid());
        claims.put("email", principal.getEmail());
        claims.put("roles", roles);
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", expiry.getEpochSecond());
        claims.put("jti", jti);
        claims.put("token_type", "ACCESS");

        return createSignedJwt(claims);
    }

    public String generateRefreshToken(UserPrincipal principal, String tokenId, String familyId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtConfig.getRefreshTokenExpirationDays(), ChronoUnit.DAYS);

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", jwtConfig.getIssuer());
        claims.put("sub", principal.getUuid());
        claims.put("email", principal.getEmail());
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", expiry.getEpochSecond());
        claims.put("jti", tokenId);
        claims.put("family_id", familyId);
        claims.put("token_type", "REFRESH");

        return createSignedJwt(claims);
    }

    private String createSignedJwt(Map<String, Object> claims) {
        try {
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "RS256");
            header.put("typ", "JWT");
            header.put("kid", rsaKeyProvider.getKeyId());

            String encodedHeader = base64UrlEncode(objectMapper.writeValueAsBytes(header));
            String encodedPayload = base64UrlEncode(objectMapper.writeValueAsBytes(claims));
            String dataToSign = encodedHeader + "." + encodedPayload;

            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(rsaKeyProvider.getPrivateKey());
            signer.update(dataToSign.getBytes(StandardCharsets.UTF_8));
            byte[] signature = signer.sign();

            String encodedSignature = base64UrlEncode(signature);
            return dataToSign + "." + encodedSignature;
        } catch (Exception e) {
            log.error("Error creating signed JWT", e);
            throw new IllegalStateException("Failed to generate JWT token", e);
        }
    }

    public boolean validateToken(String token) {
        try {
            Map<String, Object> claims = parseAndVerifyClaims(token);
            long exp = ((Number) claims.get("exp")).longValue();
            if (Instant.now().getEpochSecond() > exp) {
                log.debug("JWT token is expired");
                return false;
            }
            return true;
        } catch (Exception e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public Map<String, Object> parseAndVerifyClaims(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWT format");
        }

        String dataToVerify = parts[0] + "." + parts[1];
        byte[] signatureBytes = base64UrlDecode(parts[2]);

        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(rsaKeyProvider.getPublicKey());
            verifier.update(dataToVerify.getBytes(StandardCharsets.UTF_8));

            if (!verifier.verify(signatureBytes)) {
                throw new SecurityException("JWT signature validation failed");
            }

            byte[] payloadBytes = base64UrlDecode(parts[1]);
            return objectMapper.readValue(payloadBytes, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new SecurityException("JWT verification error: " + e.getMessage(), e);
        }
    }

    public String getUserUuidFromToken(String token) {
        return (String) parseAndVerifyClaims(token).get("sub");
    }

    public String getEmailFromToken(String token) {
        return (String) parseAndVerifyClaims(token).get("email");
    }

    public String getJtiFromToken(String token) {
        return (String) parseAndVerifyClaims(token).get("jti");
    }

    public long getExpirationFromToken(String token) {
        return ((Number) parseAndVerifyClaims(token).get("exp")).longValue();
    }

    public long getAccessTokenExpirationSeconds() {
        return jwtConfig.getAccessTokenExpirationMinutes() * 60;
    }

    private static String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static byte[] base64UrlDecode(String str) {
        return Base64.getUrlDecoder().decode(str);
    }
}

