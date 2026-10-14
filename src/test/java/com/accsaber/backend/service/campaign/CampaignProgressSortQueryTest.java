package com.accsaber.backend.service.campaign;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignCollaboratorStatus;
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
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.campaign.CampaignRepository;

import jakarta.persistence.EntityManager;

@Tag("integration")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CampaignProgressSortQueryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private CampaignRepository campaignRepository;

    private List<MapDifficulty> difficulties;

    @Test
    @DisplayName("progress sort lists finished runs newest first, then runs by completed share")
    void progressSortOrdersFinishedThenByShare() {
        User player = User.builder().id(1L).name("Player").country("ES").build();
        entityManager.persist(player);
        difficulties = difficulties();
        UUID olderFinish = run(player, "older-finish", UserCampaignStatus.COMPLETED, "2025-01-01T00:00:00Z", 2);
        UUID newerFinish = run(player, "newer-finish", UserCampaignStatus.COMPLETED, "2025-06-01T00:00:00Z", 2);
        UUID quarter = run(player, "quarter", UserCampaignStatus.IN_PROGRESS, null, 1);
        UUID threeQuarters = run(player, "three-quarters", UserCampaignStatus.IN_PROGRESS, null, 3);
        entityManager.flush();
        entityManager.clear();

        List<UUID> order = campaignRepository.findFiltered(false, List.of(CampaignStatus.PUBLISHED), null, null,
                false, List.of(new UUID(0L, 0L)), CampaignStatus.DRAFT, null, false,
                CampaignCollaboratorStatus.ACCEPTED, null, null, null, player.getId(),
                UserCampaignStatus.PARTICIPATING, PageRequest.of(0, 10, CampaignService.PROGRESS_SORT))
                .map(Campaign::getId).getContent();

        assertThat(order).containsExactly(newerFinish, olderFinish, threeQuarters, quarter);
    }

    private UUID run(User player, String slug, UserCampaignStatus status, String completedAt, int cleared) {
        Campaign campaign = Campaign.builder().creator(player).name(slug).slug(slug).status(CampaignStatus.PUBLISHED).build();
        entityManager.persist(campaign);
        UserCampaign.UserCampaignBuilder builder = UserCampaign.builder().user(player).campaign(campaign)
                .status(status);
        entityManager.persist(completedAt != null ? builder.completedAt(Instant.parse(completedAt)).build()
                : builder.build());
        for (int i = 0; i < 4; i++) {
            CampaignDifficulty node = CampaignDifficulty.builder().campaign(campaign)
                    .mapDifficulty(difficulties.get(i)).positionX(i)
                    .requirementType(CampaignRequirementType.PASS).requirementValue(0.0).build();
            entityManager.persist(node);
            if (i < cleared) {
                entityManager.persist(UserCampaignScore.builder().user(player).campaign(campaign)
                        .campaignDifficulty(node).build());
            }
        }
        return campaign.getId();
    }

    private List<MapDifficulty> difficulties() {
        Map map = Map.builder().songName("Song").songAuthor("Artist").songHash("hash").mapAuthor("Mapper")
                .beatsaverCode("1a").build();
        entityManager.persist(map);
        Category category = entityManager
                .createQuery("SELECT c FROM Category c WHERE c.code = 'true_acc' AND c.active = true", Category.class)
                .getSingleResult();
        return List.of(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD, Difficulty.EXPERT).stream()
                .map(difficulty -> {
                    MapDifficulty md = MapDifficulty.builder().map(map).category(category).difficulty(difficulty)
                            .characteristic("Standard").status(MapDifficultyStatus.RANKED).maxScore(1_000_000)
                            .build();
                    entityManager.persist(md);
                    return md;
                })
                .toList();
    }
}
