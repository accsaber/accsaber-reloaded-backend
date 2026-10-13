package com.accsaber.backend.repository.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;

import jakarta.persistence.EntityManager;

@Tag("integration")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MapVersionQueryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    private static final String CODE = "3489c";

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private MapRepository mapRepository;

    private UUID firstVersion;
    private UUID secondVersion;

    @BeforeEach
    void seed() {
        firstVersion = persistVersion("2025-03-01T00:00:00Z", Difficulty.EASY, MapDifficultyStatus.RANKED, "true_acc");
        secondVersion = persistVersion("2025-06-01T00:00:00Z", Difficulty.NORMAL, MapDifficultyStatus.RANKED,
                "standard_acc");
    }

    @Test
    @DisplayName("each version of a shared code is found by its own ranked difficulty")
    void eachVersionIsFoundByItsOwnDifficulty() {
        assertThat(version(Difficulty.EASY, MapDifficultyStatus.RANKED)).isEqualTo(firstVersion);
        assertThat(version(Difficulty.NORMAL, MapDifficultyStatus.RANKED)).isEqualTo(secondVersion);
    }

    @Test
    @DisplayName("a newer queued upload with the same difficulty does not take the ranked one's place")
    void aQueuedUploadDoesNotTakeTheRankedVersionsPlace() {
        UUID queued = persistVersion("2025-09-01T00:00:00Z", Difficulty.EASY, MapDifficultyStatus.QUEUE, "true_acc");

        assertThat(version(Difficulty.EASY, MapDifficultyStatus.RANKED)).isEqualTo(firstVersion);
        assertThat(version(Difficulty.EASY, null)).isEqualTo(queued);
    }

    private UUID version(Difficulty difficulty, MapDifficultyStatus status) {
        return mapRepository.findActiveByBeatsaverCodeMatchingDifficulty(CODE, difficulty, "standard", status,
                PageRequest.of(0, 1)).getFirst().getId();
    }

    private UUID persistVersion(String createdAt, Difficulty difficulty, MapDifficultyStatus status,
            String categoryCode) {
        Map map = Map.builder()
                .songName("What You Know")
                .songAuthor("Two Door Cinema Club")
                .songHash("hash-" + UUID.randomUUID())
                .mapAuthor("Mapper")
                .beatsaverCode(CODE)
                .build();
        entityManager.persist(map);
        entityManager.persist(MapDifficulty.builder()
                .map(map)
                .category(category(categoryCode))
                .difficulty(difficulty)
                .characteristic("Standard")
                .status(status)
                .maxScore(1_000_000)
                .build());
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE maps SET created_at = CAST(?1 AS timestamptz) WHERE id = ?2")
                .setParameter(1, Instant.parse(createdAt).toString())
                .setParameter(2, map.getId())
                .executeUpdate();
        entityManager.clear();
        return map.getId();
    }

    private Category category(String code) {
        return entityManager
                .createQuery("SELECT c FROM Category c WHERE c.code = :code AND c.active = true", Category.class)
                .setParameter("code", code)
                .getSingleResult();
    }
}
