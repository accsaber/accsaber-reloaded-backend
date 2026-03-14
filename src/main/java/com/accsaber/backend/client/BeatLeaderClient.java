package com.accsaber.backend.client;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.codec.DecodingException;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.accsaber.backend.config.PlatformProperties;
import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderLeaderboardResponse;
import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderPlayerResponse;
import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderScoreResponse;

import lombok.extern.slf4j.Slf4j;
import reactor.util.retry.Retry;

@Slf4j
@Service
public class BeatLeaderClient {

    private final WebClient webClient;
    private final PlatformProperties properties;

    public BeatLeaderClient(@Qualifier("beatLeaderWebClient") WebClient webClient,
            PlatformProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    public Optional<BeatLeaderPlayerResponse> getPlayer(String steamId) {
        try {
            return Optional.ofNullable(webClient.get()
                    .uri("/player/{id}", steamId)
                    .retrieve()
                    .bodyToMono(BeatLeaderPlayerResponse.class)
                    .retryWhen(retrySpec())
                    .block(timeout()));
        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to fetch BeatLeader player {}: {}", steamId, e.getMessage());
            return Optional.empty();
        }
    }

    public List<BeatLeaderScoreResponse> getLeaderboardScores(String leaderboardId, int page, int count) {
        try {
            BeatLeaderLeaderboardResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/leaderboard/{id}")
                            .queryParam("page", page)
                            .queryParam("count", count)
                            .build(leaderboardId))
                    .retrieve()
                    .bodyToMono(BeatLeaderLeaderboardResponse.class)
                    .retryWhen(retrySpec())
                    .block(timeout());
            return response != null && response.getScores() != null
                    ? response.getScores()
                    : List.of();
        } catch (Exception e) {
            log.error("Failed to fetch BL leaderboard scores for {}: {}", leaderboardId, e.getMessage());
            return List.of();
        }
    }

    public Optional<BeatLeaderLeaderboardResponse> getLeaderboard(String leaderboardId) {
        try {
            return Optional.ofNullable(webClient.get()
                    .uri("/leaderboard/{id}", leaderboardId)
                    .retrieve()
                    .bodyToMono(BeatLeaderLeaderboardResponse.class)
                    .retryWhen(retrySpec())
                    .block(timeout()));
        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to fetch BL leaderboard {}: {}", leaderboardId, e.getMessage());
            return Optional.empty();
        }
    }

    public List<BeatLeaderScoreResponse> getRecentScores(String leaderboardId, long afterTimestamp) {
        try {
            BeatLeaderLeaderboardResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/leaderboard/{id}")
                            .queryParam("sortBy", "date")
                            .queryParam("count", 100)
                            .build(leaderboardId))
                    .retrieve()
                    .bodyToMono(BeatLeaderLeaderboardResponse.class)
                    .retryWhen(retrySpec())
                    .block(timeout());
            if (response == null || response.getScores() == null)
                return List.of();
            return response.getScores().stream()
                    .filter(s -> s.getTimepost() != null && s.getTimepost() > afterTimestamp)
                    .toList();
        } catch (Exception e) {
            log.error("Failed to fetch BL recent scores for {}: {}", leaderboardId, e.getMessage());
            return List.of();
        }
    }

    private Retry retrySpec() {
        return Retry.backoff(properties.getBeatleader().getMaxRetries(), Duration.ofSeconds(1))
                .maxBackoff(Duration.ofSeconds(10))
                .jitter(0.5)
                .filter(throwable -> !(throwable instanceof WebClientResponseException.NotFound)
                        && !(throwable instanceof DecodingException));
    }

    private Duration timeout() {
        return Duration.ofMillis(properties.getBeatleader().getTimeoutMs());
    }
}
