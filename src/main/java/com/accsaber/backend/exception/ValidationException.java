package com.accsaber.backend.exception;

import org.springframework.http.HttpStatus;

public class ValidationException extends AccSaberException {

    public ValidationException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR");
    }

    public ValidationException(String field, String message) {
        super(
                String.format("Validation failed for field '%s': %s", field, message),
                HttpStatus.UNPROCESSABLE_CONTENT,
                "VALIDATION_ERROR");
    }
}
