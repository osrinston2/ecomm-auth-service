package com.ecomm.auth.exception;

import org.springframework.http.HttpStatus;

public class TokenRefreshException extends RuntimeException {

    private final HttpStatus status;

    public TokenRefreshException(String message) {
        super(message);
        this.status = HttpStatus.UNAUTHORIZED;
    }

    public TokenRefreshException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

