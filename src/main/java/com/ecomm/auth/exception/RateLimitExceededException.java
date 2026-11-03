package com.ecomm.auth.exception;

import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends RuntimeException {

    private final HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;

    public RateLimitExceededException(String message) {
        super(message);
    }

    public HttpStatus getStatus() {
        return status;
    }
}

