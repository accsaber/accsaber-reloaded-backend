package com.accsaber.backend.service.media;

public class MediaUnavailableException extends MediaProcessingException {

    public MediaUnavailableException(String message) {
        super(message);
    }

    public MediaUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
