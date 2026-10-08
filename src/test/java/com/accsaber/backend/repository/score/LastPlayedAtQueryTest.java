package com.accsaber.backend.repository.score;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;

import jakarta.persistence.EntityManager;

@Tag("integration")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LastPlayedAtQueryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    private static final String SORT_EXPRESSION = "(SELECT MAX(COALESCE(s2.timeSet, s2.createdAt)) "
            + "FROM Score s2 WHERE s2.user = s.user AND s2.mapDifficulty = s.mapDifficulty)";

    private static final Long PLAYER_ID = 76561190000000021L;

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ScoreRepository scoreRepository;

    private User player;
    private Category trueAcc;
    private UUID retriedRecently;
    private UUID untouchedSince;

    @BeforeEach
    void seed() {
        trueAcc = entityManager
                .createQuery("SELECT c FROM Category c WHERE c.code = 'true_acc' AND c.active = true", Category.class)
                .getSingleResult();
        player = User.builder().id(PLAYER_ID).name("Craedien").country("DE").build();
        entityManager.persist(player);

        retriedRecently = persistDifficulty();
        persistScore(retriedRecently, 950_000, true, Instant.parse("2024-03-01T00:00:00Z"), null);
        persistScore(retriedRecently, 940_000, false, Instant.parse("2026-08-01T00:00:00Z"), "Worse score");

        untouchedSince = persistDifficulty();
        persistScore(untouchedSince, 980_000, true, Instant.parse("2025-06-01T00:00:00Z"), null);

        entityManager.flush();
    }

    @Test
    @DisplayName("ascending puts the difficulty you have not touched in the longest on top")
    void ascendingLeadsWithTheStalestDifficulty() {
        assertThat(orderedBy(Sort.Direction.ASC)).containsExactly(untouchedSince, retriedRecently);
    }

    @Test
    @DisplayName("the order follows the newest attempt, not the time the active score was set")
    void ordersOnTheNewestAttemptRatherThanTheActiveScore() {
        assertThat(orderedBy(Sort.Direction.DESC)).containsExactly(retriedRecently, untouchedSince);
        assertThat(orderedBy(Sort.by(Sort.Direction.DESC, "timeSet")))
                .containsExactly(untouchedSince, retriedRecently);
    }

    @Test
    @DisplayName("the aggregate reports the newest attempt per difficulty")
    void aggregateReportsTheNewestAttempt() {
        List<Object[]> rows = scoreRepository.findAttemptAggregatesByUsersAndDifficulties(
                List.of(PLAYER_ID), List.of(retriedRecently, untouchedSince));

        assertThat(rows).hasSize(2);
        assertThat(lastPlayedAt(rows, retriedRecently)).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
        assertThat(lastPlayedAt(rows, untouchedSince)).isEqualTo(Instant.parse("2025-06-01T00:00:00Z"));
    }

    @Test
    @DisplayName("a player's last score counts attempts that did not set a pb")
    void lastScoreTimeCountsAttemptsThatWereNotPbs() {
        List<ScoreRepository.LastPlayedView> rows = scoreRepository.findLastScoreTimes(List.of(PLAYER_ID));

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getUserId()).isEqualTo(PLAYER_ID);
            assertThat(row.getLastPlayedAt()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
        });
    }

    private Instant lastPlayedAt(List<Object[]> rows, UUID difficultyId) {
        return rows.stream()
                .filter(row -> difficultyId.equals(row[1]))
                .map(row -> (Instant) row[4])
                .findFirst()
                .orElseThrow();
    }

    private List<UUID> orderedBy(Sort.Direction direction) {
        return orderedBy(JpaSort.unsafe(direction, SORT_EXPRESSION));
    }

    private List<UUID> orderedBy(Sort sort) {
        return scoreRepository.findActiveByUser(PLAYER_ID, PageRequest.of(0, 20, sort))
                .getContent().stream()
                .map(s -> s.getMapDifficulty().getId())
                .toList();
    }

    private UUID persistDifficulty() {
        Map map = Map.builder()
                .songName("Song")
                .songAuthor("Author")
                .songHash("hash-" + UUID.randomUUID())
                .mapAuthor("Mapper")
                .build();
        entityManager.persist(map);

        MapDifficulty difficulty = MapDifficulty.builder()
                .map(map)
                .category(trueAcc)
                .difficulty(Difficulty.EXPERT_PLUS)
                .characteristic("Standard")
                .status(MapDifficultyStatus.RANKED)
                .maxScore(1_000_000)
                .build();
        entityManager.persist(difficulty);
        return difficulty.getId();
    }

    private void persistScore(UUID difficultyId, int value, boolean active, Instant timeSet, String supersedesReason) {
        entityManager.persist(Score.builder()
                .user(player)
                .mapDifficulty(entityManager.getReference(MapDifficulty.class, difficultyId))
                .score(value)
                .scoreNoMods(value)
                .rank(1)
                .rankWhenSet(1)
                .ap(500.0)
                .weightedAp(500.0)
                .timeSet(timeSet)
                .supersedesReason(supersedesReason)
                .active(active)
                .build());
    }
}
