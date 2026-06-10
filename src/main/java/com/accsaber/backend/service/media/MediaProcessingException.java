package com.accsaber.backend.service.media;

import org.springframework.http.HttpStatus;

import com.accsaber.backend.exception.AccSaberException;

public class MediaProcessingException extends AccSaberException {

    public MediaProcessingException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, "MEDIA_PROCESSING_ERROR");
    }

    public MediaProcessingException(String message, Throwable cause) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, "MEDIA_PROCESSING_ERROR", cause);
    }
}
