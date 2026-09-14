package com.accsaber.backend.repository.mission;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

import com.accsaber.backend.model.entity.mission.MissionPool;
import com.accsaber.backend.model.entity.mission.MissionStatus;
import com.accsaber.backend.model.entity.mission.MissionTemplate;
import com.accsaber.backend.model.entity.mission.MissionType;
import com.accsaber.backend.model.entity.mission.UserMission;
import com.accsaber.backend.model.entity.user.User;

import jakarta.persistence.EntityManager;

@Tag("integration")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MissionContributionQueryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private MissionContributionRepository contributionRepository;
    @Autowired
    private UserMissionRepository userMissionRepository;

    private UserMission mission;
    private User alice;
    private User bob;

    @BeforeEach
    void seed() {
        alice = persistUser(76561190000000101L, "Alice");
        bob = persistUser(76561190000000102L, "Bob");
        mission = persistCommunityMission(500);
    }

    private User persistUser(Long id, String name) {
        User user = User.builder().id(id).name(name).country("ES").build();
        entityManager.persist(user);
        return user;
    }

    private UserMission persistCommunityMission(int targetCount) {
        MissionTemplate template = MissionTemplate.builder()
                .code("community_" + UUID.randomUUID())
                .name("Community Grind")
                .description("Set {count} scores together.")
                .type(MissionType.SCORES_N)
                .pool(MissionPool.community)
                .completableUntil(Instant.now().plus(7, ChronoUnit.DAYS))
                .fixedXp(500)
                .build();
        entityManager.persist(template);
        UserMission row = UserMission.builder()
                .template(template)
                .pool(MissionPool.community)
                .targetCount(targetCount)
                .xpReward(500)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
        entityManager.persist(row);
        entityManager.flush();
        return row;
    }

    private double accept(User user, double amount, Double cap) {
        double accepted = contributionRepository.acceptContribution(
                mission.getId(), user.getId(), amount, cap, Instant.now());
        entityManager.clear();
        return accepted;
    }

    @Test
    @DisplayName("a first contribution inserts the row and reports the whole amount as accepted")
    void firstContributionInsertsAndReportsEverything() {
        assertThat(accept(alice, 5.0, null)).isEqualTo(5.0);
    }

    @Test
    @DisplayName("later contributions add on and report only the newly accepted part")
    void laterContributionsReportTheDelta() {
        accept(alice, 5.0, null);

        assertThat(accept(alice, 3.0, null)).isEqualTo(3.0);
        assertThat(contributionOf(alice)).isEqualTo(8.0);
    }

    @Test
    @DisplayName("the cap clips the accepted delta so the bar never counts more than the player gave")
    void theCapClipsTheAcceptedDelta() {
        accept(alice, 4.0, 5.0);

        assertThat(accept(alice, 10.0, 5.0)).isEqualTo(1.0);
        assertThat(contributionOf(alice)).isEqualTo(5.0);
    }

    @Test
    @DisplayName("a player already at the cap accepts nothing more")
    void aCappedPlayerAcceptsNothing() {
        accept(alice, 5.0, 5.0);

        assertThat(accept(alice, 5.0, 5.0)).isEqualTo(0.0);
        assertThat(contributionOf(alice)).isEqualTo(5.0);
    }

    @Test
    @DisplayName("contributors are tracked separately and counted per mission")
    void contributorsAreTrackedSeparately() {
        accept(alice, 5.0, null);
        accept(bob, 2.0, null);

        assertThat(contributionOf(alice)).isEqualTo(5.0);
        assertThat(contributionOf(bob)).isEqualTo(2.0);
        assertThat(contributionRepository.countContributors(List.of(mission.getId())))
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.getMissionId()).isEqualTo(mission.getId());
                    assertThat(view.getContributors()).isEqualTo(2L);
                });
    }

    @Test
    @DisplayName("banking and claiming completion only fires for whoever crosses the target")
    void completionIsClaimedExactlyOnce() {
        userMissionRepository.bankSharedProgress(mission.getId(), 499, 0.0);
        entityManager.clear();
        assertThat(userMissionRepository.claimSharedCompletion(mission.getId(), Instant.now())).isZero();

        userMissionRepository.bankSharedProgress(mission.getId(), 1, 0.0);
        entityManager.clear();
        assertThat(userMissionRepository.claimSharedCompletion(mission.getId(), Instant.now())).isEqualTo(1);
        assertThat(userMissionRepository.claimSharedCompletion(mission.getId(), Instant.now())).isZero();

        entityManager.clear();
        assertThat(userMissionRepository.findCommunityById(mission.getId()))
                .get()
                .satisfies(row -> assertThat(row.getStatus()).isEqualTo(MissionStatus.completed));
    }

    @Test
    @DisplayName("the shared row is found by the community lookups and never by a per-user one")
    void theSharedRowIsInvisibleToPerUserQueries() {
        assertThat(userMissionRepository.findActiveSharedFor(alice.getId()))
                .extracting(UserMission::getId)
                .containsExactly(mission.getId());
        assertThat(userMissionRepository.findAllActiveByUser(alice.getId())).isEmpty();
    }

    private double contributionOf(User user) {
        return contributionRepository.findContributionsByUser(user.getId(), List.of(mission.getId()))
                .getFirst().getContribution();
    }
}
