package com.accsaber.backend.repository.campaign;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignDifficulty;
import com.accsaber.backend.model.entity.campaign.CampaignRequirementType;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.campaign.UserCampaign;
import com.accsaber.backend.model.entity.campaign.UserCampaignScore;
import com.accsaber.backend.model.entity.campaign.UserCampaignStatus;
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
class CampaignAveragesQueryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private CampaignLeaderboardRepository leaderboardRepository;

    private UUID campaignId;

    @BeforeEach
    void seed() {
        MapDifficulty difficulty = difficulty();
        User creator = player(1L);
        Campaign campaign = Campaign.builder().creator(creator).name("averages").slug("averages")
                .status(CampaignStatus.PUBLISHED).build();
        entityManager.persist(campaign);
        campaignId = campaign.getId();
        CampaignDifficulty node = CampaignDifficulty.builder().campaign(campaign).mapDifficulty(difficulty)
                .positionX(0).requirementType(CampaignRequirementType.PASS).requirementValue(0.0).build();
        entityManager.persist(node);

        complete(player(2L), campaign, node, difficulty, 990_000, 100.0);
        complete(player(3L), campaign, node, difficulty, 950_000, 900.0);
        complete(player(4L), campaign, node, difficulty, 970_000, 500.0);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("sorting by average accuracy runs on Postgres and pages highest first")
    void sortsByAverageAccuracy() {
        Page<Object[]> page = leaderboardRepository.averages(campaignId,
                PageRequest.of(0, 2).withSort(Sort.Direction.DESC, "avg_acc"));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(row -> ((Number) row[0]).longValue()).containsExactly(2L, 4L);
    }

    @Test
    @DisplayName("sorting by average AP runs on Postgres and pages highest first")
    void sortsByAverageAp() {
        Page<Object[]> page = leaderboardRepository.averages(campaignId,
                PageRequest.of(1, 2).withSort(Sort.Direction.DESC, "avg_ap"));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(row -> ((Number) row[0]).longValue()).containsExactly(2L);
        assertThat(leaderboardRepository.averages(campaignId,
                PageRequest.of(0, 2).withSort(Sort.Direction.DESC, "avg_ap")).getContent())
                .extracting(row -> ((Number) row[0]).longValue()).containsExactly(3L, 4L);
    }

    private void complete(User player, Campaign campaign, CampaignDifficulty node, MapDifficulty difficulty,
            int value, double ap) {
        entityManager.persist(UserCampaign.builder().user(player).campaign(campaign)
                .status(UserCampaignStatus.COMPLETED).completedAt(Instant.parse("2025-01-01T00:00:00Z")).build());
        Score score = Score.builder().user(player).mapDifficulty(difficulty).score(value).scoreNoMods(value)
                .rank(1).rankWhenSet(1).ap(ap).weightedAp(ap).active(true).build();
        entityManager.persist(score);
        entityManager.persist(UserCampaignScore.builder().user(player).campaign(campaign)
                .campaignDifficulty(node).score(score).build());
    }

    private User player(long id) {
        User user = User.builder().id(id).name("Player " + id).country("ES").build();
        entityManager.persist(user);
        return user;
    }

    private MapDifficulty difficulty() {
        Map map = Map.builder().songName("Song").songAuthor("Artist").songHash("hash").mapAuthor("Mapper")
                .beatsaverCode("1a").build();
        entityManager.persist(map);
        Category category = entityManager
                .createQuery("SELECT c FROM Category c WHERE c.code = 'true_acc' AND c.active = true", Category.class)
                .getSingleResult();
        MapDifficulty difficulty = MapDifficulty.builder().map(map).category(category).difficulty(Difficulty.EXPERT)
                .characteristic("Standard").status(MapDifficultyStatus.RANKED).maxScore(1_000_000).build();
        entityManager.persist(difficulty);
        return difficulty;
    }
}
