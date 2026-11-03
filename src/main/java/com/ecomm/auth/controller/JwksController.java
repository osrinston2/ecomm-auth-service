package com.ecomm.auth.controller;

import com.ecomm.auth.dto.response.JwksResponse;
import com.ecomm.auth.security.RsaKeyProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
public class JwksController {

    private final RsaKeyProvider rsaKeyProvider;

    public JwksController(RsaKeyProvider rsaKeyProvider) {
        this.rsaKeyProvider = rsaKeyProvider;
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JwksResponse> getJwks() {
        return ResponseEntity.ok(new JwksResponse(List.of(rsaKeyProvider.getJwk())));
    }

    @GetMapping(value = "/public-key", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getPublicKeyPem() {
        return ResponseEntity.ok(rsaKeyProvider.getPublicKeyPem());
    }
}

