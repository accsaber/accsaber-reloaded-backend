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
            assertThat(player.getAvatar()).isNotBlank();
        }

        @Test
        void returnsPlayerDataSuitableForImport() {
            Optional<ScoreSaberPlayerResponse> result = client.getPlayer(KNOWN_PLAYER_ID);

            assertThat(result).isPresent();
            ScoreSaberPlayerResponse player = result.get();

            assertThat(player.getName()).as("name usable as display name")
                    .isNotBlank()
                    .hasSizeLessThanOrEqualTo(255);
            assertThat(player.getAvatar()).as("avatar usable as avatar URL")
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
            assertThat(lb.getMap()).isNotNull();
            assertThat(lb.getMap().getHash()).isEqualToIgnoringCase(EXPECTED_HASH);
            assertThat(lb.getMap().getSongName()).isNotBlank();
            assertThat(lb.getMap().getSongAuthorName()).isNotNull();
            assertThat(lb.getMap().getLevelAuthorName()).isNotBlank();
            assertThat(lb.getMaxScore()).isGreaterThan(0);
        }
    }

    @Nested
    class GetLeaderboardScores {

        @Test
        void returnsScoresWithAllMappedFields() {
            ScoreSaberScoresPage page = client.getLeaderboardScores(SS_LEADERBOARD_ID, 1);

            assertThat(page).isNotNull();
            assertThat(page.getData()).isNotEmpty();
            assertThat(page.getMetadata()).isNotNull();
            assertThat(page.getMetadata().getTotalItems()).isGreaterThan(0);

            for (ScoreSaberScoreResponse score : page.getData()) {
                assertThat(score.getId()).as("id").isGreaterThan(0);
                assertThat(score.getUnmodifiedScore()).as("unmodifiedScore").isGreaterThan(0);
                assertThat(score.getModifiedScore()).as("modifiedScore").isGreaterThan(0);
                assertThat(score.getRank()).as("rank").isGreaterThan(0);
                assertThat(score.getMaxCombo()).as("maxCombo").isGreaterThan(0);
                assertThat(score.getCreatedAt()).as("createdAt").isNotBlank();
                assertThat(score.getMods()).as("mods").isNotNull();
                assertThat(score.getBadCuts()).as("badCuts").isGreaterThanOrEqualTo(0);
                assertThat(score.getMissedNotes()).as("missedNotes").isGreaterThanOrEqualTo(0);

                assertThat(score.getPlayer()).as("player").isNotNull();
                assertThat(score.getPlayer().getId()).as("player.id").isNotBlank();
                assertThat(Long.parseLong(score.getPlayer().getId()))
                        .as("player.id parseable as numeric")
                        .isGreaterThan(0);
            }
        }
    }

    @Nested
    class GetLeaderboardScoresSortedByDate {

        @Test
        void returnsPageWithScores() {
            ScoreSaberScoresPage page = client.getLeaderboardScoresSortedByDate(SS_LEADERBOARD_ID, 1);

            assertThat(page).isNotNull();
            assertThat(page.getData()).isNotNull().isNotEmpty();
        }
    }
}
