package com.accsaber.backend.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends AccSaberException {

    public ForbiddenException(String message) {
        super(message, HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    public ForbiddenException() {
        super("You do not have permission to perform this action", HttpStatus.FORBIDDEN, "FORBIDDEN");
    }
}
