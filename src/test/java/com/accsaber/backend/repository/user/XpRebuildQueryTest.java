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

    @Test
    @DisplayName("rebuildXpTotals splits event missions and event bonuses out of mission XP")
    void rebuildXpTotalsSplitsEventXpFromMissionXp() {
        UUID eventId = (UUID) entityManager.createNativeQuery("""
                INSERT INTO events (title, slug, starts_at, ends_at, bonus_xp)
                VALUES ('Event', 'event-xp-rebuild', NOW() - INTERVAL '7 days', NOW() + INTERVAL '7 days', 500)
                RETURNING id
                """).getSingleResult();
        UUID dailyTemplate = (UUID) entityManager.createNativeQuery("""
                INSERT INTO mission_templates (code, name, description, type, pool)
                VALUES ('daily_rebuild', 'Daily', 'Daily', 'PLAY_N_MAPS', 'daily')
                RETURNING id
                """).getSingleResult();
        UUID eventTemplate = (UUID) entityManager.createNativeQuery("""
                INSERT INTO mission_templates (code, name, description, type, pool, event_id)
                VALUES ('event_rebuild', 'Event', 'Event', 'PLAY_N_MAPS', 'event', :eventId)
                RETURNING id
                """)
                .setParameter("eventId", eventId)
                .getSingleResult();
        persistCompletedMission(dailyTemplate, "daily", 100);
        persistCompletedMission(eventTemplate, "event", 200);
        entityManager.createNativeQuery("""
                INSERT INTO user_event_profiles (event_id, user_id, bonus_xp, bonus_awarded_at)
                VALUES (:eventId, :userId, 500, NOW())
                """)
                .setParameter("eventId", eventId)
                .setParameter("userId", user.getId())
                .executeUpdate();
        entityManager.flush();

        userRepository.recalculateTotalXpForUser(user.getId());

        entityManager.refresh(user);
        assertThat(user.getMissionXp()).isEqualTo(100.0);
        assertThat(user.getEventXp()).isEqualTo(700.0);
        assertThat(user.getTotalXp()).isEqualTo(800.0);
    }

    private void persistCompletedMission(UUID templateId, String pool, int xpReward) {
        entityManager.createNativeQuery("""
                INSERT INTO user_missions (user_id, template_id, pool, xp_reward, status, expires_at, completed_at)
                VALUES (:userId, :templateId, :pool, :xpReward, 'completed', NOW() + INTERVAL '1 day', NOW())
                """)
                .setParameter("userId", user.getId())
                .setParameter("templateId", templateId)
                .setParameter("pool", pool)
                .setParameter("xpReward", xpReward)
                .executeUpdate();
    }
}
