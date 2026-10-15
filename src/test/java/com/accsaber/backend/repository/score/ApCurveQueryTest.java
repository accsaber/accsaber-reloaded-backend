package com.accsaber.backend.repository.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

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
class ApCurveQueryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ScoreRepository scoreRepository;

    private User player;

    @BeforeEach
    void seed() {
        player = User.builder().id(1L).name("Player").country("ES").build();
        entityManager.persist(player);
        score("true_acc", 300.0, true);
        score("true_acc", 900.0, true);
        score("true_acc", 600.0, true);
        score("true_acc", 999.0, false);
        score("tech_acc", 450.0, true);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("returns each category's active AP best first, cut at the limit, in one query")
    void topApPerCategory() {
        List<Object[]> rows = scoreRepository.findTopApByCategory(player.getId(), 2);

        assertThat(rows).extracting(row -> (String) row[1], row -> ((Number) row[2]).doubleValue())
                .containsExactly(
                        tuple("tech_acc", 450.0),
                        tuple("true_acc", 900.0),
                        tuple("true_acc", 600.0));
    }

    private void score(String categoryCode, double ap, boolean active) {
        Map map = Map.builder().songName("Song " + ap).songAuthor("Artist").songHash("hash" + ap)
                .mapAuthor("Mapper").beatsaverCode("1a").build();
        entityManager.persist(map);
        Category category = entityManager
                .createQuery("SELECT c FROM Category c WHERE c.code = :code AND c.active = true", Category.class)
                .setParameter("code", categoryCode)
                .getSingleResult();
        MapDifficulty difficulty = MapDifficulty.builder().map(map).category(category).difficulty(Difficulty.EXPERT)
                .characteristic("Standard").status(MapDifficultyStatus.RANKED).maxScore(1_000_000).build();
        entityManager.persist(difficulty);
        entityManager.persist(Score.builder().user(player).mapDifficulty(difficulty).score(990_000)
                .scoreNoMods(990_000).rank(1).rankWhenSet(1).ap(ap).weightedAp(ap).active(active).build());
    }
}
