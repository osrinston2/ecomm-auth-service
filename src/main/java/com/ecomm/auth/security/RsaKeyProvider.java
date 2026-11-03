package com.ecomm.auth.security;

import com.ecomm.auth.config.JwtConfigProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

@Component
public class RsaKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(RsaKeyProvider.class);

    private final JwtConfigProperties jwtConfigProperties;

    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;
    private String keyId;

    public RsaKeyProvider(JwtConfigProperties jwtConfigProperties) {
        this.jwtConfigProperties = jwtConfigProperties;
    }

    @PostConstruct
    public void init() {
        this.keyId = jwtConfigProperties.getRsa().getKeyId();
        String privKeyPem = jwtConfigProperties.getRsa().getPrivateKey();
        String pubKeyPem = jwtConfigProperties.getRsa().getPublicKey();

        if (privKeyPem != null && !privKeyPem.isBlank() && pubKeyPem != null && !pubKeyPem.isBlank()) {
            try {
                this.privateKey = parsePrivateKey(privKeyPem);
                this.publicKey = parsePublicKey(pubKeyPem);
                log.info("Initialized RSA keys from configuration (kid: {})", keyId);
                return;
            } catch (Exception e) {
                log.warn("Failed to parse configured RSA keys: {}. Generating an ephemeral RSA key pair.", e.getMessage());
            }
        }

        generateEphemeralKeyPair();
    }

    private void generateEphemeralKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048, new SecureRandom());
            KeyPair keyPair = generator.generateKeyPair();
            this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
            this.publicKey = (RSAPublicKey) keyPair.getPublic();
            log.info("Generated ephemeral RSA 2048-bit key pair for JWT signing (kid: {})", keyId);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA algorithm not supported on this JVM", e);
        }
    }

    private RSAPrivateKey parsePrivateKey(String pem) throws Exception {
        String cleanPem = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(cleanPem);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) kf.generatePrivate(spec);
    }

    private RSAPublicKey parsePublicKey(String pem) throws Exception {
        String cleanPem = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("-----BEGIN RSA PUBLIC KEY-----", "")
            .replace("-----END RSA PUBLIC KEY-----", "")
            .replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(cleanPem);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) kf.generatePublic(spec);
    }

    public RSAPrivateKey getPrivateKey() {
        return privateKey;
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    public String getKeyId() {
        return keyId;
    }

    public Map<String, Object> getJwk() {
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("kid", keyId);
        jwk.put("n", toBase64Url(publicKey.getModulus()));
        jwk.put("e", toBase64Url(publicKey.getPublicExponent()));
        return jwk;
    }

    public String getPublicKeyPem() {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----\n";
    }

    private static String toBase64Url(BigInteger bigInt) {
        byte[] bytes = bigInt.toByteArray();
        // Remove leading zero byte from two's complement representation if present
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

