package com.accsaber.backend.exception;

import org.springframework.http.HttpStatus;

public class TooManyRequestsException extends AccSaberException {

    public TooManyRequestsException(String message) {
        super(message, HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS");
    }
}
