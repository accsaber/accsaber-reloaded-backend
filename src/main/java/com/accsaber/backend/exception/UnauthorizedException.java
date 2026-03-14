package com.accsaber.backend.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends AccSaberException {

    public UnauthorizedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }
}
