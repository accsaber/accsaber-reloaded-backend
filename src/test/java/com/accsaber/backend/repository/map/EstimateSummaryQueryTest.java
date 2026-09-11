package com.accsaber.backend.repository.map;

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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.accsaber.backend.model.dto.projection.EstimateSummaryRow;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyComplexityEstimate;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;

@Tag("integration")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EstimateSummaryQueryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private MapDifficultyComplexityEstimateRepository estimateRepository;

    private Category trueAcc;
    private UUID priced;
    private UUID stale;
    private UUID unpriced;

    @BeforeEach
    void seed() {
        trueAcc = entityManager
                .createQuery("SELECT c FROM Category c WHERE c.code = 'true_acc' AND c.active = true", Category.class)
                .getSingleResult();
        priced = persistEstimate(7.4, "note-accuracy-9", "sha-new", Instant.parse("2026-09-09T10:00:00Z"));
        stale = persistEstimate(3.1, "note-accuracy-8", "sha-old", Instant.parse("2026-08-01T10:00:00Z"));
        unpriced = persistDifficulty();
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("the summary comes back typed, with the model hash pulled out of the inputs")
    void theSummaryComesBackTyped() {
        List<EstimateSummaryRow> rows = estimateRepository.findSummaryRows(List.of(priced, stale, unpriced));

        assertThat(rows).hasSize(2);
        EstimateSummaryRow newest = rows.stream()
                .filter(row -> priced.equals(row.getMapDifficultyId())).findFirst().orElseThrow();
        assertThat(newest.getComplexity()).isEqualTo(7.4);
        assertThat(newest.getVersion()).isEqualTo("note-accuracy-9");
        assertThat(newest.getModelHash()).isEqualTo("sha-new");
        assertThat(newest.getUpdatedAt()).isNotNull();
        assertThat(rows.stream().filter(row -> stale.equals(row.getMapDifficultyId())).findFirst().orElseThrow()
                .getModelHash()).isEqualTo("sha-old");
    }

    @Test
    @DisplayName("a difficulty the script has never priced simply has no row")
    void anUnpricedDifficultyHasNoRow() {
        assertThat(estimateRepository.findSummaryRows(List.of(unpriced))).isEmpty();
    }

    private UUID persistEstimate(double complexity, String version, String modelHash, Instant updatedAt) {
        UUID difficultyId = persistDifficulty();
        MapDifficultyComplexityEstimate estimate = MapDifficultyComplexityEstimate.builder()
                .mapDifficulty(entityManager.getReference(MapDifficulty.class, difficultyId))
                .complexity(complexity)
                .version(version)
                .inputs(objectMapper.createObjectNode().put("modelHash", modelHash).put("meanNoteAccuracy", 0.97))
                .createdAt(updatedAt)
                .updatedAt(updatedAt)
                .build();
        entityManager.persist(estimate);
        return difficultyId;
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
}
