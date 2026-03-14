package com.accsaber.backend.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public class AccSaberException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public AccSaberException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public AccSaberException(String message, HttpStatus status, String errorCode, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }
}
