package com.accsaber.backend.repository.user;

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
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserCategoryStatistics;

import jakarta.persistence.EntityManager;

@Tag("integration")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class XpRebuildQueryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private UserCategoryStatisticsRepository statisticsRepository;
    @Autowired
    private UserRepository userRepository;

    private User user;
    private Category trueAcc;
    private MapDifficulty difficulty;

    @BeforeEach
    void seed() {
        trueAcc = categoryByCode("true_acc");

        user = User.builder()
                .id(76561190000000001L)
                .name("RebuildPlayer")
                .country("ES")
                .build();
        entityManager.persist(user);

        Map map = Map.builder()
                .songName("Song")
                .songAuthor("Author")
                .songHash("hash-" + UUID.randomUUID())
                .mapAuthor("Mapper")
                .build();
        entityManager.persist(map);

        difficulty = MapDifficulty.builder()
                .map(map)
                .category(trueAcc)
                .difficulty(Difficulty.EXPERT_PLUS)
                .characteristic("Standard")
                .status(MapDifficultyStatus.RANKED)
                .maxScore(1000000)
                .build();
        entityManager.persist(difficulty);
    }

    private Category categoryByCode(String code) {
        return entityManager
                .createQuery("SELECT c FROM Category c WHERE c.code = :code AND c.active = true", Category.class)
                .setParameter("code", code)
                .getSingleResult();
    }

    private void persistScore(double xpGained, boolean active, String supersedesReason) {
        persistScore(xpGained, active, supersedesReason, null);
    }

    private void persistScore(double xpGained, boolean active, String supersedesReason, Instant timeSet) {
        Score score = Score.builder()
                .user(user)
                .mapDifficulty(difficulty)
                .score(950000)
                .scoreNoMods(950000)
                .rank(1)
                .rankWhenSet(1)
                .ap(400.0)
                .weightedAp(400.0)
                .xpGained(xpGained)
                .active(active)
                .supersedesReason(supersedesReason)
                .timeSet(timeSet)
                .build();
        entityManager.persist(score);
    }

    private UserCategoryStatistics persistStats(Category category, double scoreXp) {
        UserCategoryStatistics stats = UserCategoryStatistics.builder()
                .user(user)
                .category(category)
                .ap(400.0)
                .scoreXp(scoreXp)
                .rankedPlays(1)
                .active(true)
                .build();
        entityManager.persist(stats);
        return stats;
    }

    @Test
    @DisplayName("rebuildScoreXp repairs a stale per-category score_xp from the active scores")
    void rebuildScoreXpRepairsStaleCategoryTotal() {
        persistScore(300.0, true, null);
        persistScore(25.0, false, "Worse score");
        UserCategoryStatistics stats = persistStats(trueAcc, 999.0);
        entityManager.flush();

        statisticsRepository.rebuildScoreXp(user.getId());

        entityManager.refresh(stats);
        assertThat(stats.getScoreXp()).isEqualTo(300.0);
    }

    @Test
    @DisplayName("rebuildScoreXp sums the overall row across categories that count for overall")
    void rebuildScoreXpFillsTheOverallRow() {
        persistScore(300.0, true, null);
        persistStats(trueAcc, 0.0);
        UserCategoryStatistics overallStats = persistStats(categoryByCode("overall"), 0.0);
        entityManager.flush();

        statisticsRepository.rebuildScoreXp(user.getId());

        entityManager.refresh(overallStats);
        assertThat(overallStats.getScoreXp()).isEqualTo(300.0);
    }

    @Test
    @DisplayName("rebuildXpTotals sums every score row, attempts included")
    void rebuildXpTotalsCountsAttempts() {
        persistScore(300.0, true, null);
        persistScore(25.0, false, "Partial attempt");
        persistScore(0.0, false, "Campaign attempt");
        entityManager.flush();

        userRepository.recalculateTotalXpForUser(user.getId());

        entityManager.refresh(user);
        assertThat(user.getTotalXp()).isEqualTo(325.0);
    }

    @Test
    @DisplayName("findXpTimeline returns dated XP events oldest first")
    void findXpTimelineReturnsDatedEventsInOrder() {
        persistScore(300.0, true, null, Instant.parse("2024-03-01T00:00:00Z"));
        persistScore(25.0, false, "Worse score", Instant.parse("2022-01-01T00:00:00Z"));
        entityManager.flush();

        List<Object[]> timeline = userRepository.findXpTimeline(user.getId());

        assertThat(timeline).hasSize(2);
        assertThat(((Number) timeline.get(0)[1]).doubleValue()).isEqualTo(25.0);
        assertThat(((Number) timeline.get(1)[1]).doubleValue()).isEqualTo(300.0);
    }

    @Test
    @DisplayName("findXpTimeline skips zero-XP rows such as campaign attempts")
    void findXpTimelineSkipsZeroXpRows() {
        persistScore(0.0, false, "Campaign attempt", Instant.parse("2024-03-01T00:00:00Z"));
        persistScore(120.0, true, null, Instant.parse("2024-04-01T00:00:00Z"));
        entityManager.flush();

        List<Object[]> timeline = userRepository.findXpTimeline(user.getId());

        assertThat(timeline).hasSize(1);
        assertThat(((Number) timeline.get(0)[1]).doubleValue()).isEqualTo(120.0);
    }

    @Test
    @DisplayName("findUsersMissingLevelReward honours the threshold and an existing level grant")
    void findUsersMissingLevelRewardHonoursThresholdAndExistingGrant() {
        user.setTotalXp(5000.0);
        entityManager.flush();

        assertThat(userRepository.findUsersMissingLevelReward(10, 9000.0)).doesNotContain(user.getId());
        assertThat(userRepository.findUsersMissingLevelReward(10, 1000.0)).contains(user.getId());

        UUID itemId = (UUID) entityManager
                .createNativeQuery("SELECT id FROM items WHERE unlock_level = 10 LIMIT 1")
                .getSingleResult();
        entityManager.createNativeQuery("""
                INSERT INTO user_item_links (user_id, item_id, source, source_id)
                VALUES (:userId, :itemId, 'level', '10')
                """)
                .setParameter("userId", user.getId())
                .setParameter("itemId", itemId)
                .executeUpdate();

        assertThat(userRepository.findUsersMissingLevelReward(10, 1000.0)).doesNotContain(user.getId());
        assertThat(userRepository.findUsersMissingLevelReward(20, 1000.0)).contains(user.getId());
    }

    @Test
    @DisplayName("rebuildXpTotals runs across every user without arguments")
    void rebuildXpTotalsRunsForAllUsers() {
        persistScore(120.0, true, null);
        entityManager.flush();

        userRepository.recalculateTotalXpForAllActiveUsers();

        entityManager.refresh(user);
        assertThat(user.getTotalXp()).isEqualTo(120.0);
    }

    private UUID warWithHit(Double xpAwarded, Instant hitAt) {
        User rival = User.builder().id(76561190000000002L).name("Rival").country("ES").build();
        entityManager.persist(rival);
        Score attackerScore = Score.builder().user(user).mapDifficulty(difficulty).score(990000).scoreNoMods(990000)
                .rank(1).rankWhenSet(1).ap(0.0).weightedAp(0.0).xpGained(0.0).active(false)
                .supersedesReason("Worse score").build();
        entityManager.persist(attackerScore);
        entityManager.flush();
        UUID red = (UUID) entityManager.createNativeQuery(
                "INSERT INTO clans (name, tag, slug) VALUES ('Red', 'RED', 'red') RETURNING id").getSingleResult();
        UUID blue = (UUID) entityManager.createNativeQuery(
                "INSERT INTO clans (name, tag, slug) VALUES ('Blue', 'BLUE', 'blue') RETURNING id").getSingleResult();
        UUID season = (UUID) entityManager.createNativeQuery("INSERT INTO clan_seasons (name, slug, starts_at, ends_at) "
                + "VALUES ('S', 's', NOW() - INTERVAL '1 day', NOW() + INTERVAL '30 days') RETURNING id")
                .getSingleResult();
        UUID war = (UUID) entityManager.createNativeQuery("INSERT INTO clan_wars (season_id, attacker_clan_id, "
                + "defender_clan_id, declared_by, arena, arena_spec, ruleset, status, starts_at) VALUES "
                + "(?1, ?2, ?3, ?4, 'mixed', CAST('{}' AS jsonb), 'duel', 'active', NOW()) RETURNING id")
                .setParameter(1, season).setParameter(2, red).setParameter(3, blue).setParameter(4, user.getId())
                .getSingleResult();
        for (Object[] side : new Object[][] { { red, user.getId() }, { blue, rival.getId() } }) {
            entityManager.createNativeQuery("INSERT INTO clan_war_sides (war_id, clan_id, stake, stake_remaining, "
                    + "standing_at_declare) VALUES (?1, ?2, 100, 100, 100)").setParameter(1, war)
                    .setParameter(2, side[0]).executeUpdate();
            entityManager.createNativeQuery("INSERT INTO clan_war_participants (war_id, user_id, clan_id, "
                    + "standing_weight, guard) VALUES (?1, ?2, ?3, 1, 100)").setParameter(1, war)
                    .setParameter(2, side[1]).setParameter(3, side[0]).executeUpdate();
        }
        entityManager.createNativeQuery("INSERT INTO clan_war_hits (war_id, attacker_user_id, victim_user_id, "
                + "victim_cycle, map_difficulty_id, attacker_score_id, damage, guard_after, broke, standing_moved, "
                + "xp_awarded, created_at) VALUES (?1, ?2, ?3, 0, ?4, ?5, 100, 0, true, 10, ?6, ?7)")
                .setParameter(1, war).setParameter(2, user.getId()).setParameter(3, rival.getId())
                .setParameter(4, difficulty.getId()).setParameter(5, attackerScore.getId())
                .setParameter(6, xpAwarded).setParameter(7, hitAt).executeUpdate();
        return war;
    }

    @Test
    @DisplayName("rebuildXpTotals and findXpTimeline both count war break XP, dated at the break")
    void warBreakXpIsASourceOfBoth() {
        persistScore(300.0, true, null, Instant.parse("2024-03-01T00:00:00Z"));
        warWithHit(40.0, Instant.parse("2025-01-01T00:00:00Z"));
        entityManager.flush();

        userRepository.recalculateTotalXpForUser(user.getId());
        List<Object[]> timeline = userRepository.findXpTimeline(user.getId());

        entityManager.refresh(user);
        assertThat(user.getTotalXp()).isEqualTo(340.0);
        assertThat(timeline).hasSize(2);
        assertThat(((Number) timeline.get(1)[1]).doubleValue()).isEqualTo(40.0);
    }

    @Test
    @DisplayName("rebuildXpTotals and findXpTimeline both count settled war participation XP")
    void warParticipationXpIsASourceOfBoth() {
        UUID war = warWithHit(null, Instant.parse("2025-01-01T00:00:00Z"));
        entityManager.createNativeQuery("UPDATE clan_war_participants SET xp_awarded = 75, rewarded_at = ?1 "
                + "WHERE war_id = ?2 AND user_id = ?3").setParameter(1, Instant.parse("2025-02-01T00:00:00Z"))
                .setParameter(2, war).setParameter(3, user.getId()).executeUpdate();
        entityManager.flush();

        userRepository.recalculateTotalXpForUser(user.getId());
        List<Object[]> timeline = userRepository.findXpTimeline(user.getId());

        entityManager.refresh(user);
        assertThat(user.getTotalXp()).isEqualTo(75.0);
        assertThat(timeline).singleElement().satisfies(row -> assertThat(((Number) row[1]).doubleValue()).isEqualTo(75.0));
    }
}
