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

    private static final Retry RATE_LIMIT_RETRY = ClientSupport.rateLimitRetry("BeatSaver", 20, Duration.ofSeconds(2));

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
                    .retryWhen(RATE_LIMIT_RETRY)
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
                    .retryWhen(RATE_LIMIT_RETRY)
                    .block(Duration.ofSeconds(30)));
        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to download BeatSaver zip for hash {}: {}", hash, e.getMessage());
            return Optional.empty();
        }
    }

    private Duration timeout() {
        return Duration.ofMillis(properties.getBeatsaver().getTimeoutMs());
    }
}
