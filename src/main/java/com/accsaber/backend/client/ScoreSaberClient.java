package com.accsaber.backend.client;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.codec.DecodingException;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.accsaber.backend.config.PlatformProperties;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberLeaderboardResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberPlayerResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoresPage;

import lombok.extern.slf4j.Slf4j;
import reactor.util.retry.Retry;

@Slf4j
@Service
public class ScoreSaberClient {

    private final WebClient webClient;
    private final PlatformProperties properties;

    public ScoreSaberClient(@Qualifier("scoreSaberWebClient") WebClient webClient,
            PlatformProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    public Optional<ScoreSaberPlayerResponse> getPlayer(String steamId) {
        try {
            return Optional.ofNullable(webClient.get()
                    .uri("/player/{id}/basic", steamId)
                    .retrieve()
                    .bodyToMono(ScoreSaberPlayerResponse.class)
                    .retryWhen(retrySpec())
                    .block(timeout()));
        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to fetch ScoreSaber player {}: {}", steamId, e.getMessage());
            return Optional.empty();
        }
    }

    public ScoreSaberScoresPage getLeaderboardScores(String leaderboardId, int page) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/leaderboard/by-id/{id}/scores")
                        .queryParam("page", page)
                        .build(leaderboardId))
                .retrieve()
                .bodyToMono(ScoreSaberScoresPage.class)
                .retryWhen(rateLimitRetrySpec())
                .block(timeout());
    }

    public Optional<ScoreSaberLeaderboardResponse> getLeaderboard(String leaderboardId) {
        try {
            return Optional.ofNullable(webClient.get()
                    .uri("/leaderboard/by-id/{id}/info", leaderboardId)
                    .retrieve()
                    .bodyToMono(ScoreSaberLeaderboardResponse.class)
                    .retryWhen(retrySpec())
                    .block(timeout()));
        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to fetch SS leaderboard {}: {}", leaderboardId, e.getMessage());
            return Optional.empty();
        }
    }

    public ScoreSaberScoresPage getRecentScores(String leaderboardId, long afterTimestamp) {
        try {
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/leaderboard/by-id/{id}/scores")
                            .queryParam("withMetadata", true)
                            .build(leaderboardId))
                    .retrieve()
                    .bodyToMono(ScoreSaberScoresPage.class)
                    .retryWhen(rateLimitRetrySpec())
                    .block(timeout());
        } catch (Exception e) {
            log.error("Failed to fetch SS recent scores for {}: {}", leaderboardId, e.getMessage());
            return null;
        }
    }

    private Retry retrySpec() {
        return Retry.backoff(properties.getScoresaber().getMaxRetries(), Duration.ofSeconds(1))
                .maxBackoff(Duration.ofSeconds(10))
                .jitter(0.5)
                .filter(throwable -> !(throwable instanceof WebClientResponseException.NotFound)
                        && !(throwable instanceof DecodingException));
    }

    private Retry rateLimitRetrySpec() {
        return Retry.backoff(10, Duration.ofSeconds(3))
                .maxBackoff(Duration.ofSeconds(60))
                .jitter(0.3)
                .filter(throwable -> {
                    if (throwable instanceof WebClientResponseException.NotFound) return false;
                    if (throwable instanceof DecodingException) return false;
                    if (throwable instanceof WebClientResponseException wce && wce.getStatusCode().value() == 429) {
                        log.warn("ScoreSaber 429 rate limited, backing off and retrying");
                        return true;
                    }
                    return true;
                });
    }

    private Duration timeout() {
        return Duration.ofMillis(properties.getScoresaber().getTimeoutMs());
    }
}
