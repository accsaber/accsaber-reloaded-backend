package com.accsaber.backend.client;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.accsaber.backend.config.PlatformProperties;
import com.accsaber.backend.model.dto.platform.beatsaver.BeatSaverMapResponse;

import lombok.extern.slf4j.Slf4j;
import reactor.util.retry.Retry;

@Slf4j
@Service
public class BeatSaverClient {

    private final WebClient webClient;
    private final PlatformProperties properties;

    public BeatSaverClient(@Qualifier("beatSaverWebClient") WebClient webClient,
            PlatformProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    public Optional<BeatSaverMapResponse> getMapByHash(String hash) {
        try {
            return Optional.ofNullable(webClient.get()
                    .uri("/maps/hash/{hash}", hash)
                    .retrieve()
                    .bodyToMono(BeatSaverMapResponse.class)
                    .retryWhen(retrySpec())
                    .block(timeout()));
        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to fetch BeatSaver map by hash {}: {}", hash, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<byte[]> downloadMapZip(String hash) {
        try {
            URI uri = URI.create("https://cdn.beatsaver.com/" + hash.toLowerCase() + ".zip");
            return Optional.ofNullable(webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .retryWhen(retrySpec())
                    .block(Duration.ofSeconds(30)));
        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to download BeatSaver zip for hash {}: {}", hash, e.getMessage());
            return Optional.empty();
        }
    }

    private Retry retrySpec() {
        return Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(2))
                .maxBackoff(Duration.ofSeconds(60))
                .jitter(0.3)
                .filter(throwable -> {
                    if (throwable instanceof WebClientResponseException.NotFound) return false;
                    if (throwable instanceof WebClientResponseException wce) {
                        int code = wce.getStatusCode().value();
                        if (code == 429) {
                            log.warn("BeatSaver 429 rate limited, backing off and retrying");
                            return true;
                        }
                        if (code >= 400 && code < 500) return false;
                    }
                    return true;
                });
    }

    private Duration timeout() {
        return Duration.ofMillis(properties.getBeatsaver().getTimeoutMs());
    }
}
