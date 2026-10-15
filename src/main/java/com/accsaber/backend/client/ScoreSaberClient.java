package com.accsaber.backend.client;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberLeaderboardResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberPlayerResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberPlayerScoresPage;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoreResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoreStats;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoresPage;

import lombok.extern.slf4j.Slf4j;
import reactor.util.retry.Retry;

@Slf4j
@Service
public class ScoreSaberClient {

    private static final Retry RATE_LIMIT_RETRY = ClientSupport.rateLimitRetry("ScoreSaber", 20, Duration.ofSeconds(3));

    private final WebClient webClient;

    public ScoreSaberClient(@Qualifier("scoreSaberWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Optional<ScoreSaberPlayerResponse> getPlayer(String playerId) {
        try {
            return Optional.ofNullable(webClient.get()
                    .uri("/v2/players/{id}/basic", playerId)
                    .retrieve()
                    .bodyToMono(ScoreSaberPlayerResponse.class)
                    .retryWhen(RATE_LIMIT_RETRY)
                    .block());
        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to fetch ScoreSaber player {}: {}", playerId, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<ScoreSaberScoreResponse> getPlayerScoreOnLeaderboard(String playerId, String leaderboardId) {
        try {
            ScoreSaberPlayerScoresPage page = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/players/{id}/scores")
                            .queryParam("leaderboardId", leaderboardId)
                            .queryParam("limit", 1)
                            .build(playerId))
                    .retrieve()
                    .bodyToMono(ScoreSaberPlayerScoresPage.class)
                    .retryWhen(RATE_LIMIT_RETRY)
                    .block();
            if (page == null || page.getData() == null || page.getData().isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(page.getData().get(0).getScore());
        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to fetch SS player score for player={} leaderboard={}: {}",
                    playerId, leaderboardId, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<ScoreSaberScoreStats> getScoreStats(Long scoreId) {
        try {
            return Optional.ofNullable(webClient.get()
                    .uri("/v2/scores/{id}/stats", scoreId)
                    .retrieve()
                    .bodyToMono(ScoreSaberScoreStats.class)
                    .retryWhen(RATE_LIMIT_RETRY)
                    .block());
        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to fetch SS score stats for score {}: {}", scoreId, e.getMessage());
            return Optional.empty();
        }
    }

    public ScoreSaberScoresPage getLeaderboardScores(String leaderboardId, int page) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2/leaderboards/{id}/scores")
                        .queryParam("page", page)
                        .queryParam("limit", 100)
                        .build(leaderboardId))
                .retrieve()
                .bodyToMono(ScoreSaberScoresPage.class)
                .retryWhen(RATE_LIMIT_RETRY)
                .block();
    }

    public ScoreSaberScoresPage getLeaderboardScoresSortedByDate(String leaderboardId, int page) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2/leaderboards/{id}/scores")
                        .queryParam("page", page)
                        .queryParam("limit", 100)
                        .queryParam("sort", "timeSet")
                        .queryParam("sortDirection", "desc")
                        .build(leaderboardId))
                .retrieve()
                .bodyToMono(ScoreSaberScoresPage.class)
                .retryWhen(RATE_LIMIT_RETRY)
                .block();
    }

    public Optional<ScoreSaberLeaderboardResponse> getLeaderboard(String leaderboardId) {
        try {
            return Optional.ofNullable(webClient.get()
                    .uri("/v2/leaderboards/{id}", leaderboardId)
                    .retrieve()
                    .bodyToMono(ScoreSaberLeaderboardResponse.class)
                    .retryWhen(RATE_LIMIT_RETRY)
                    .block());
        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to fetch SS leaderboard {}: {}", leaderboardId, e.getMessage());
            return Optional.empty();
        }
    }
}
