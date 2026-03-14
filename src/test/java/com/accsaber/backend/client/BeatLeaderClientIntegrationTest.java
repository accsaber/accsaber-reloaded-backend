package com.accsaber.backend.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.accsaber.backend.config.PlatformProperties;
import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderLeaderboardResponse;
import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderPlayerResponse;
import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderScoreResponse;

@Tag("integration")
class BeatLeaderClientIntegrationTest {

    private static final String BL_LEADERBOARD_ID = "4c2b591";
    private static final String KNOWN_PLAYER_ID = "76561198087536397";
    private static final String EXPECTED_HASH = "47a4e1e2aec435105ceeedda437bdccc7db18058";

    private static BeatLeaderClient client;

    @BeforeAll
    static void setUp() {
        PlatformProperties properties = new PlatformProperties();
        PlatformProperties.PlatformConfig config = new PlatformProperties.PlatformConfig();
        config.setBaseUrl("https://api.beatleader.com");
        config.setTimeoutMs(15000);
        config.setMaxRetries(2);
        properties.setBeatleader(config);

        WebClient webClient = WebClient.builder()
                .baseUrl(config.getBaseUrl())
                .build();

        client = new BeatLeaderClient(webClient, properties);
    }

    @Nested
    class GetPlayer {

        @Test
        void returnsPlayerWithAllMappedFields() {
            Optional<BeatLeaderPlayerResponse> result = client.getPlayer(KNOWN_PLAYER_ID);

            assertThat(result).isPresent();
            BeatLeaderPlayerResponse player = result.get();
            assertThat(player.getId()).isEqualTo(KNOWN_PLAYER_ID);
            assertThat(player.getName()).isNotBlank();
            assertThat(player.getAvatar()).isNotBlank();
            assertThat(player.getCountry()).isNotBlank();
        }

        @Test
        void returnsPlayerDataSuitableForImport() {
            Optional<BeatLeaderPlayerResponse> result = client.getPlayer(KNOWN_PLAYER_ID);

            assertThat(result).isPresent();
            BeatLeaderPlayerResponse player = result.get();

            assertThat(player.getName()).as("name usable as display name")
                    .isNotBlank()
                    .hasSizeLessThanOrEqualTo(255);
            assertThat(player.getAvatar()).as("avatar usable as URL")
                    .startsWith("http");
            assertThat(player.getCountry()).as("country is a valid code")
                    .hasSizeBetween(2, 3)
                    .isUpperCase();
        }

        @Test
        void returnsEmpty_forNonexistentPlayer() {
            assertThat(client.getPlayer("999")).isEmpty();
        }
    }

    @Nested
    class GetLeaderboard {

        @Test
        void returnsLeaderboardWithAllMappedFields() {
            Optional<BeatLeaderLeaderboardResponse> result = client.getLeaderboard(BL_LEADERBOARD_ID);

            assertThat(result).isPresent();
            BeatLeaderLeaderboardResponse lb = result.get();
            assertThat(lb.getId()).isEqualTo(BL_LEADERBOARD_ID);
            assertThat(lb.getPlays()).isGreaterThan(0);

            assertThat(lb.getSong()).isNotNull();
            assertThat(lb.getSong().getHash()).isEqualToIgnoringCase(EXPECTED_HASH);
            assertThat(lb.getSong().getName()).isNotBlank();
            assertThat(lb.getSong().getAuthor()).isNotNull();
            assertThat(lb.getSong().getMapper()).isNotNull();

            assertThat(lb.getDifficulty()).isNotNull();
            assertThat(lb.getDifficulty().getDifficultyName()).isNotBlank();
            assertThat(lb.getDifficulty().getModeName()).isNotBlank();
            assertThat(lb.getDifficulty().getMaxScore()).isGreaterThan(0);
        }
    }

    @Nested
    class GetLeaderboardScores {

        @Test
        void returnsScoresWithAllMappedFields() {
            List<BeatLeaderScoreResponse> scores = client.getLeaderboardScores(BL_LEADERBOARD_ID, 1, 5);

            assertThat(scores).hasSizeGreaterThanOrEqualTo(1)
                    .hasSizeLessThanOrEqualTo(5);

            for (BeatLeaderScoreResponse score : scores) {
                assertThat(score.getId()).as("id").isGreaterThan(0);
                assertThat(score.getBaseScore()).as("baseScore").isGreaterThan(0);
                assertThat(score.getModifiedScore()).as("modifiedScore").isGreaterThan(0);
                assertThat(score.getRank()).as("rank").isGreaterThan(0);
                assertThat(score.getTimepost()).as("timepost").isGreaterThan(0);
                assertThat(score.getLeaderboardId()).as("leaderboardId").isEqualTo(BL_LEADERBOARD_ID);
                assertThat(score.getModifiers()).as("modifiers").isNotNull();

                assertThat(score.getPlayer()).as("player").isNotNull();
                assertThat(score.getPlayer().getId()).as("player.id").isNotBlank();
                assertThat(Long.parseLong(score.getPlayer().getId()))
                        .as("player.id parseable as Steam ID")
                        .isGreaterThan(0);

                assertThat(score.getMaxCombo()).as("maxCombo").isGreaterThanOrEqualTo(0);
                assertThat(score.getBadCuts()).as("badCuts").isGreaterThanOrEqualTo(0);
                assertThat(score.getMissedNotes()).as("missedNotes").isGreaterThanOrEqualTo(0);
                assertThat(score.getWallsHit()).as("wallsHit").isGreaterThanOrEqualTo(0);
                assertThat(score.getBombCuts()).as("bombCuts").isGreaterThanOrEqualTo(0);
                assertThat(score.getPauses()).as("pauses").isGreaterThanOrEqualTo(0);
                assertThat(score.getMaxStreak()).as("maxStreak").isGreaterThanOrEqualTo(0);
                assertThat(score.getPlayCount()).as("playCount").isGreaterThanOrEqualTo(0);
            }
        }
    }

    @Nested
    class GetRecentScores {

        @Test
        void returnsScoresAfterTimestamp() {
            long oneYearAgo = System.currentTimeMillis() / 1000 - 365L * 24 * 3600;
            List<BeatLeaderScoreResponse> scores = client.getRecentScores(BL_LEADERBOARD_ID, oneYearAgo);

            assertThat(scores).isNotNull();
            for (BeatLeaderScoreResponse score : scores) {
                assertThat(score.getTimepost()).isGreaterThan(oneYearAgo);
                assertThat(score.getPlayer()).isNotNull();
                assertThat(score.getPlayer().getId()).isNotBlank();
            }
        }
    }
}
