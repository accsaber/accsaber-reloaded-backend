package com.accsaber.backend.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.accsaber.backend.config.PlatformProperties;
import com.accsaber.backend.model.dto.platform.ai.AiComplexityResponse;

import lombok.extern.slf4j.Slf4j;
import reactor.util.retry.Retry;

@Slf4j
@Service
public class AiComplexityClient {

    private final WebClient webClient;
    private final PlatformProperties properties;

    public AiComplexityClient(@Qualifier("aiComplexityWebClient") WebClient webClient,
            PlatformProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    public Optional<BigDecimal> getComplexity(String songHash, String characteristic, int difficulty) {
        try {
            AiComplexityResponse response = webClient.get()
                    .uri("/json/{hash}/{characteristic}/{diff}/basic", songHash, characteristic, difficulty)
                    .retrieve()
                    .bodyToMono(AiComplexityResponse.class)
                    .retryWhen(retrySpec())
                    .block(timeout());

            if (response == null || response.getBalanced() == null) {
                return Optional.empty();
            }

            return Optional.of(response.getBalanced().setScale(1, RoundingMode.HALF_UP));
        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to fetch AI complexity for hash={} characteristic={} diff={}: {}",
                    songHash, characteristic, difficulty, e.getMessage());
            return Optional.empty();
        }
    }

    private Retry retrySpec() {
        return Retry.backoff(properties.getAiComplexity().getMaxRetries(), Duration.ofSeconds(1))
                .filter(throwable -> !(throwable instanceof WebClientResponseException.NotFound));
    }

    private Duration timeout() {
        return Duration.ofMillis(properties.getAiComplexity().getTimeoutMs());
    }
}
