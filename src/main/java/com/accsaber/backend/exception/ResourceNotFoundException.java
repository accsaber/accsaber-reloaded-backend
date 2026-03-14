package com.accsaber.backend.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends AccSaberException {

    public ResourceNotFoundException(String resourceType, Object identifier) {
        super(
                String.format("%s not found with identifier: %s", resourceType, identifier),
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND");
    }

    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
    }
}
