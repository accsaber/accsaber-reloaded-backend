package com.accsaber.backend.client;

import java.time.Duration;

import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import lombok.extern.slf4j.Slf4j;
import reactor.core.Exceptions;
import reactor.util.retry.Retry;

@Slf4j
final class ClientSupport {

    private ClientSupport() {
    }

    static Retry rateLimitRetry(String name, long maxAttempts, Duration minBackoff) {
        return Retry.backoff(maxAttempts, minBackoff)
                .maxBackoff(Duration.ofSeconds(60))
                .jitter(0.3)
                .filter(throwable -> isRetryable(name, throwable))
                .onRetryExhaustedThrow((spec, signal) -> {
                    log.error("{} retries exhausted after {} attempts: {}", name, signal.totalRetries(),
                            signal.failure().getMessage());
                    return Exceptions.retryExhausted(
                            "Retries exhausted: " + signal.totalRetries() + "/" + maxAttempts, signal.failure());
                });
    }

    static MultipartBodyBuilder mapZipForm(byte[] zipBytes) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("zip", new ByteArrayResource(zipBytes) {
            @Override
            public String getFilename() {
                return "map.zip";
            }
        }).contentType(MediaType.APPLICATION_OCTET_STREAM);
        return builder;
    }

    private static boolean isRetryable(String name, Throwable throwable) {
        if (throwable instanceof WebClientResponseException.NotFound || throwable instanceof DecodingException) {
            return false;
        }
        if (throwable instanceof WebClientResponseException wce) {
            int code = wce.getStatusCode().value();
            if (code == 429) {
                log.warn("{} 429 rate limited, backing off and retrying", name);
                return true;
            }
            return code < 400 || code >= 500;
        }
        return true;
    }
}
