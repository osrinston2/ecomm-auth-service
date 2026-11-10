package com.ecomm.auth.controller;

import com.ecomm.auth.dto.response.JwksResponse;
import com.ecomm.auth.security.RsaKeyProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Key Management & JWKS", description = "Public cryptographic keys for downstream microservices to verify RS256 JWT tokens")
public class JwksController {

    private final RsaKeyProvider rsaKeyProvider;

    public JwksController(RsaKeyProvider rsaKeyProvider) {
        this.rsaKeyProvider = rsaKeyProvider;
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
        summary = "RFC 7517 JSON Web Key Set (JWKS)",
        description = "Provides public cryptographic keys (modulus 'n', exponent 'e', and key ID 'kid') so downstream microservices (Cart, Order, Payment, Gateway) can verify JWT signatures statelessly."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "JWKS successfully returned")
    })
    public ResponseEntity<JwksResponse> getJwks() {
        return ResponseEntity.ok(new JwksResponse(List.of(rsaKeyProvider.getJwk())));
    }

    @GetMapping(value = "/public-key", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(
        summary = "PEM encoded RSA Public Key",
        description = "Returns the X.509 PEM formatted RSA public key for services or gateways that consume standard PEM certificates."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "PEM formatted public key")
    })
    public ResponseEntity<String> getPublicKeyPem() {
        return ResponseEntity.ok(rsaKeyProvider.getPublicKeyPem());
    }
}

