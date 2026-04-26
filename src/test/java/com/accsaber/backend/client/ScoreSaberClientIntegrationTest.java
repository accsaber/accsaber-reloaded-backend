package com.accsaber.backend.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberLeaderboardResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberPlayerResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoreResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoresPage;

@Tag("integration")
class ScoreSaberClientIntegrationTest {

    private static final String SS_LEADERBOARD_ID = "680316";
    private static final String KNOWN_PLAYER_ID = "76561198087536397";
    private static final String EXPECTED_HASH = "47A4E1E2AEC435105CEEEDDA437BDCCC7DB18058";

    private static ScoreSaberClient client;

    @BeforeAll
    static void setUp() {
        WebClient webClient = WebClient.builder()
                .baseUrl("https://scoresaber.com/api")
                .build();

        client = new ScoreSaberClient(webClient);
    }

    @Nested
    class GetPlayer {

        @Test
        void returnsPlayerWithAllMappedFields() {
            Optional<ScoreSaberPlayerResponse> result = client.getPlayer(KNOWN_PLAYER_ID);

            assertThat(result).isPresent();
            ScoreSaberPlayerResponse player = result.get();
            assertThat(player.getId()).isEqualTo(KNOWN_PLAYER_ID);
            assertThat(player.getName()).isNotBlank();
            assertThat(player.getCountry()).isNotBlank();
            assertThat(player.getProfilePicture()).isNotBlank();
        }

        @Test
        void returnsPlayerDataSuitableForImport() {
            Optional<ScoreSaberPlayerResponse> result = client.getPlayer(KNOWN_PLAYER_ID);

            assertThat(result).isPresent();
            ScoreSaberPlayerResponse player = result.get();

            assertThat(player.getName()).as("name usable as display name")
                    .isNotBlank()
                    .hasSizeLessThanOrEqualTo(255);
            assertThat(player.getProfilePicture()).as("profilePicture usable as avatar URL")
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
            Optional<ScoreSaberLeaderboardResponse> result = client.getLeaderboard(SS_LEADERBOARD_ID);

            assertThat(result).isPresent();
            ScoreSaberLeaderboardResponse lb = result.get();
            assertThat(lb.getId()).isEqualTo(Long.parseLong(SS_LEADERBOARD_ID));
            assertThat(lb.getSongHash()).isEqualToIgnoringCase(EXPECTED_HASH);
            assertThat(lb.getSongName()).isNotBlank();
            assertThat(lb.getSongAuthorName()).isNotNull();
            assertThat(lb.getLevelAuthorName()).isNotBlank();
            assertThat(lb.getMaxScore()).isGreaterThan(0);
        }
    }

    @Nested
    class GetLeaderboardScores {

        @Test
        void returnsScoresWithAllMappedFields() {
            ScoreSaberScoresPage page = client.getLeaderboardScores(SS_LEADERBOARD_ID, 1);

            assertThat(page).isNotNull();
            assertThat(page.getScores()).isNotEmpty();
            assertThat(page.getMetadata()).isNotNull();
            assertThat(page.getMetadata().getTotal()).isGreaterThan(0);

            for (ScoreSaberScoreResponse score : page.getScores()) {
                assertThat(score.getId()).as("id").isGreaterThan(0);
                assertThat(score.getBaseScore()).as("baseScore").isGreaterThan(0);
                assertThat(score.getModifiedScore()).as("modifiedScore").isGreaterThan(0);
                assertThat(score.getRank()).as("rank").isGreaterThan(0);
                assertThat(score.getMaxCombo()).as("maxCombo").isGreaterThan(0);
                assertThat(score.getTimeSet()).as("timeSet").isNotBlank();
                assertThat(score.getModifiers()).as("modifiers").isNotNull();
                assertThat(score.getBadCuts()).as("badCuts").isGreaterThanOrEqualTo(0);
                assertThat(score.getMissedNotes()).as("missedNotes").isGreaterThanOrEqualTo(0);

                assertThat(score.getLeaderboardPlayerInfo()).as("leaderboardPlayerInfo").isNotNull();
                assertThat(score.getLeaderboardPlayerInfo().getId()).as("leaderboardPlayerInfo.id").isNotBlank();
                assertThat(Long.parseLong(score.getLeaderboardPlayerInfo().getId()))
                        .as("leaderboardPlayerInfo.id parseable as Steam ID")
                        .isGreaterThan(0);
            }
        }
    }

    @Nested
    class GetRecentScores {

        @Test
        void returnsPageWithScores() {
            long oneYearAgo = System.currentTimeMillis() / 1000 - 365L * 24 * 3600;
            ScoreSaberScoresPage page = client.getRecentScores(SS_LEADERBOARD_ID, oneYearAgo);

            assertThat(page).isNotNull();
            assertThat(page.getScores()).isNotNull().isNotEmpty();
        }
    }
}
