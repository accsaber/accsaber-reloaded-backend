package com.accsaber.backend.service.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyComplexity;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.service.map.MapDifficultyComplexityService.RankedChange;

import jakarta.persistence.EntityManager;

@Tag("integration")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MapDifficultyComplexityService.class)
class ComplexitySupersedeIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private MapDifficultyComplexityService complexityService;

    @Test
    @DisplayName("a bulk supersede swaps the active version of every map without tripping the one active index")
    void bulkSupersedeKeepsOneActivePerDifficulty() {
        Category trueAcc = entityManager
                .createQuery("SELECT c FROM Category c WHERE c.code = 'true_acc' AND c.active = true", Category.class)
                .getSingleResult();
        MapDifficulty first = persistDifficulty(trueAcc, 7.0);
        MapDifficulty second = persistDifficulty(trueAcc, 9.0);
        entityManager.flush();
        entityManager.clear();

        List<UUID> ids = List.of(first.getId(), second.getId());
        var current = complexityService.lockActive(ids);
        List<MapDifficulty> difficulties = ids.stream()
                .map(id -> entityManager.find(MapDifficulty.class, id)).toList();
        complexityService.supersedeAll(List.of(
                new RankedChange(difficulties.get(0), current.get(first.getId()), 7.8, "reweight"),
                new RankedChange(difficulties.get(1), current.get(second.getId()), 9.7, "reweight")),
                java.util.Map.of(), null);
        entityManager.flush();
        entityManager.clear();

        assertThat(complexityService.findActiveComplexitiesForDifficulties(ids))
                .containsEntry(first.getId(), 7.8)
                .containsEntry(second.getId(), 9.7);
    }

    private MapDifficulty persistDifficulty(Category category, double complexity) {
        Map map = Map.builder()
                .songName("Song")
                .songAuthor("Author")
                .songHash("hash-" + UUID.randomUUID())
                .mapAuthor("Mapper")
                .build();
        entityManager.persist(map);
        MapDifficulty difficulty = MapDifficulty.builder()
                .map(map)
                .category(category)
                .difficulty(Difficulty.EXPERT_PLUS)
                .characteristic("Standard")
                .status(MapDifficultyStatus.RANKED)
                .maxScore(1_000_000)
                .build();
        entityManager.persist(difficulty);
        entityManager.persist(MapDifficultyComplexity.builder()
                .mapDifficulty(difficulty)
                .complexity(complexity)
                .active(true)
                .build());
        return difficulty;
    }
}
