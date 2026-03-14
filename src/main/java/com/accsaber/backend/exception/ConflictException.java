package com.accsaber.backend.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends AccSaberException {

    public ConflictException(String message) {
        super(message, HttpStatus.CONFLICT, "CONFLICT");
    }

    public ConflictException(String resourceType, Object identifier) {
        super(
                String.format("%s already exists with identifier: %s", resourceType, identifier),
                HttpStatus.CONFLICT,
                "CONFLICT");
    }
}
