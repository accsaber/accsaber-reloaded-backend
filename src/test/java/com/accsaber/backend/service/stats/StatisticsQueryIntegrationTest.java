package com.accsaber.backend.service.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.statistics.MissionCalibrationResponse;
import com.accsaber.backend.model.dto.response.statistics.MissionShortfallResponse;
import com.accsaber.backend.model.dto.response.statistics.CampaignCreatorResponse;
import com.accsaber.backend.model.dto.response.statistics.EventMissionLeaderboardResponse;
import com.accsaber.backend.model.dto.response.statistics.EventSummaryResponse;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.mission.Event;
import com.accsaber.backend.model.entity.mission.MissionBand;
import com.accsaber.backend.model.entity.mission.MissionPool;
import com.accsaber.backend.model.entity.mission.MissionStatus;
import com.accsaber.backend.model.entity.mission.MissionTemplate;
import com.accsaber.backend.model.entity.mission.MissionType;
import com.accsaber.backend.model.entity.mission.UserMission;
import com.accsaber.backend.model.entity.mission.UserEventProfile;
import com.accsaber.backend.model.entity.user.User;

import jakarta.persistence.EntityManager;

@Tag("integration")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ StatsQueryRunner.class, MissionStatisticsService.class, MissionShortfallService.class,
        CampaignStatisticsService.class, EventStatisticsService.class })
class StatisticsQueryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    private static final MissionStatsFilter NO_FILTER = MissionStatsFilter.none();
    private static final CampaignStatsFilter NO_CAMPAIGN_FILTER = CampaignStatsFilter.none();

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private MissionStatisticsService missionStats;
    @Autowired
    private MissionShortfallService shortfallStats;
    @Autowired
    private CampaignStatisticsService campaignStats;
    @Autowired
    private EventStatisticsService eventStats;

    private MissionTemplate playN;
    private long nextUserId = 76561190000000500L;

    @BeforeEach
    void seed() {
        playN = persistTemplate("play_n", MissionType.PLAY_N_MAPS, MissionPool.daily);
        entityManager.flush();
    }

    @Nested
    @DisplayName("Skill tiers")
    class Tiers {

        @Test
        @DisplayName("buckets on the thresholds from docs/beat-saber-metrics.md")
        void bucketsOnDocumentedThresholds() {
            Map<Double, String> expected = Map.of(
                    350.0, "new",
                    500.0, "casual",
                    700.0, "moderate",
                    800.0, "strong",
                    1000.0, "top",
                    1200.0, "elite");
            expected.forEach((threshold, tier) -> persistMission(m -> m
                    .status(MissionStatus.completed)
                    .completedAt(Instant.now())
                    .assignedSkillThreshold(threshold)));
            persistMission(m -> m.status(MissionStatus.completed).completedAt(Instant.now()));
            entityManager.flush();

            List<MissionCalibrationResponse> rows = missionStats.getByTier(playN.getId(), NO_FILTER);

            assertThat(rows).extracting(MissionCalibrationResponse::getTier)
                    .containsExactlyInAnyOrder("unknown", "new", "casual", "moderate", "strong", "top", "elite");
        }

        @Test
        @DisplayName("boundary values land in the upper tier")
        void boundaryValuesLandUpward() {
            persistMission(m -> m.assignedSkillThreshold(600.0));
            persistMission(m -> m.assignedSkillThreshold(599.99));
            entityManager.flush();

            assertThat(missionStats.getByTier(playN.getId(), NO_FILTER))
                    .extracting(MissionCalibrationResponse::getTier)
                    .containsExactlyInAnyOrder("casual", "moderate");
        }
    }

    @Nested
    @DisplayName("The denominator")
    class Denominator {

        @Test
        @DisplayName("expired missions count against the rate but voided ones do not")
        void voidedMissionsStayOutOfTheDenominator() {
            persistMission(m -> m.status(MissionStatus.completed).completedAt(Instant.now()));
            persistMission(m -> m.status(MissionStatus.expired));
            persistMission(m -> m.status(MissionStatus.voided));
            persistMission(m -> m.status(MissionStatus.voided));
            entityManager.flush();

            MissionCalibrationResponse row = missionStats
                    .getCalibration(NO_FILTER, PageRequest.of(0, 20))
                    .getContent().get(0);

            assertThat(row.getAssigned()).isEqualTo(2);
            assertThat(row.getCompleted()).isEqualTo(1);
            assertThat(row.getExpired()).isEqualTo(1);
            assertThat(row.getCompletionRate()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("assigned equals completed plus expired plus still open")
        void assignedAddsUp() {
            persistMission(m -> m.status(MissionStatus.completed).completedAt(Instant.now()));
            persistMission(m -> m.status(MissionStatus.expired));
            persistMission(m -> m.status(MissionStatus.active));
            entityManager.flush();

            MissionCalibrationResponse row = missionStats
                    .getCalibration(NO_FILTER, PageRequest.of(0, 20))
                    .getContent().get(0);

            assertThat(row.getCompleted() + row.getExpired() + row.getStillOpen()).isEqualTo(row.getAssigned());
        }
    }

    @Nested
    @DisplayName("Filters")
    class Filters {

        @Test
        @DisplayName("a multi-select widens the result instead of narrowing it")
        void multiSelectWidens() {
            MissionTemplate weekly = persistTemplate("weekly_grind", MissionType.SCORES_N, MissionPool.weekly);
            persistMission(m -> m.pool(MissionPool.daily));
            persistMission(m -> m.template(weekly).pool(MissionPool.weekly));
            entityManager.flush();

            assertThat(totalAssigned(withPools(MissionPool.daily))).isEqualTo(1);
            assertThat(totalAssigned(withPools(MissionPool.weekly))).isEqualTo(1);
            assertThat(totalAssigned(withPools(MissionPool.daily, MissionPool.weekly))).isEqualTo(2);
        }

        @Test
        @DisplayName("the skill range cuts on the raw threshold, not the tier boundary")
        void skillRangeCutsOnRawThreshold() {
            persistMission(m -> m.assignedSkillThreshold(640.0));
            persistMission(m -> m.assignedSkillThreshold(700.0));
            persistMission(m -> m.assignedSkillThreshold(740.0));
            entityManager.flush();

            MissionStatsFilter midMoreate = new MissionStatsFilter(null, null, null, null, null, null,
                    660.0, 720.0, null, null, null, 1);

            assertThat(totalAssigned(MissionStatsFilter.none())).isEqualTo(3);
            assertThat(totalAssigned(midMoreate)).isEqualTo(1);
        }

        @Test
        @DisplayName("rows with no skill snapshot drop out once a range is set")
        void skillRangeExcludesUnknown() {
            persistMission(m -> m.assignedSkillThreshold(800.0));
            persistMission(m -> m);
            entityManager.flush();

            MissionStatsFilter ranged = new MissionStatsFilter(null, null, null, null, null, null,
                    0.0, 2000.0, null, null, null, 1);

            assertThat(totalAssigned(MissionStatsFilter.none())).isEqualTo(2);
            assertThat(totalAssigned(ranged)).isEqualTo(1);
        }

        @Test
        @DisplayName("country keeps only that country's players")
        void countryNarrowsToOnePlace() {
            persistMissionForCountry("ES");
            persistMissionForCountry("ES");
            persistMissionForCountry("GB");
            entityManager.flush();

            assertThat(totalAssigned(withCountry("ES"))).isEqualTo(2);
            assertThat(totalAssigned(withCountry("gb"))).isEqualTo(1);
            assertThat(totalAssigned(withCountry("  "))).isEqualTo(3);
        }

        @Test
        @DisplayName("an unknown campaign status is rejected rather than ignored")
        void unknownCampaignStatusIsRejected() {
            CampaignStatsFilter bogus = new CampaignStatsFilter(List.of("legendary"), null, 0);

            assertThatThrownBy(() -> campaignStats.getFunnel(bogus, PageRequest.of(0, 20)))
                    .isInstanceOf(ValidationException.class);
        }

        private long totalAssigned(MissionStatsFilter filter) {
            return missionStats.getCalibration(filter, PageRequest.of(0, 50))
                    .getContent().stream().mapToLong(MissionCalibrationResponse::getAssigned).sum();
        }

        private MissionStatsFilter withPools(MissionPool... pools) {
            return new MissionStatsFilter(List.of(pools), null, null, null, null, null, null, null, null,
                    null, null, 1);
        }

        private MissionStatsFilter withCountry(String country) {
            return new MissionStatsFilter(null, null, null, null, null, null, null, null, country, null, null, 1);
        }
    }

    @Nested
    @DisplayName("Shortfall")
    class Shortfall {

        @Test
        @DisplayName("measures banked progress against the target")
        void measuresBankedProgress() {
            persistMission(m -> m.status(MissionStatus.expired).targetCount(10).progressCount(7));
            persistMission(m -> m.status(MissionStatus.expired).targetCount(10).progressCount(3));
            entityManager.flush();

            List<MissionShortfallResponse> rows = shortfallStats.getShortfall(playN.getId(), NO_FILTER);

            assertThat(rows).hasSize(1);
            MissionShortfallResponse row = rows.get(0);
            assertThat(row.getFailed()).isEqualTo(2);
            assertThat(row.getMeasured()).isEqualTo(2);
            assertThat(row.getMedianReachedFraction()).isEqualTo(0.5);
            assertThat(row.getBuckets()).hasSize(10);
            assertThat(bucketCount(row, "30-40%")).isEqualTo(1);
            assertThat(bucketCount(row, "70-80%")).isEqualTo(1);
        }

        @Test
        @DisplayName("returns nothing for types that bank no measurable progress")
        void skipsUnmeasurableTypes() {
            MissionTemplate pb = persistTemplate("pb_specific", MissionType.PB_SPECIFIC_MAP, MissionPool.daily);
            entityManager.flush();

            assertThat(shortfallStats.getShortfall(pb.getId(), NO_FILTER)).isEmpty();
        }

        @Test
        @DisplayName("every mission type produces valid SQL")
        void scoreJoinedVariantsRun() {
            for (MissionType type : MissionType.values()) {
                MissionTemplate template = persistTemplate("t_" + type, type, poolFor(type));
                entityManager.flush();
                assertThat(shortfallStats.getShortfall(template.getId(), NO_FILTER)).isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("Event summary")
    class EventSummary {

        @Test
        @DisplayName("a repeatable mission counts players, not runs")
        void repeatableCountsPlayers() {
            Event event = persistEvent();
            MissionTemplate marathon = persistEventTemplate(event, "marathon", true);
            entityManager.flush();

            for (int i = 0; i < 3; i++) {
                User runner = persistUser();
                persistEventMission(marathon, runner, MissionStatus.completed, 200);
                persistEventMission(marathon, runner, MissionStatus.completed, 200);
                persistEventMission(marathon, runner, MissionStatus.expired, 200);
            }
            for (int i = 0; i < 5; i++) {
                persistEventMission(marathon, persistUser(), MissionStatus.expired, 200);
            }
            entityManager.flush();

            MissionCalibrationResponse row = eventStats.getSummary(event.getId(), null, null)
                    .getMissions().get(0);

            assertThat(row.isRepeatable()).isTrue();
            assertThat(row.getPlayers()).isEqualTo(8L);
            assertThat(row.getPlayersCompleted()).isEqualTo(3L);
            assertThat(row.getPlayersExpired()).isEqualTo(5L);
            assertThat(row.getCompleted()).isEqualTo(6);
            assertThat(row.getPlayerCompletionRate()).isEqualTo(3.0 / 8.0);
            assertThat(row.getMedianCompletionsPerPlayer()).isEqualTo(2.0);
            assertThat(row.getXpPaid()).isEqualTo(1200);
        }

        @Test
        @DisplayName("event totals add up and split XP by source")
        void totalsAddUp() {
            Event event = persistEvent();
            MissionTemplate once = persistEventTemplate(event, "warm_up", false);
            entityManager.flush();

            User finisher = persistUser();
            persistEventMission(once, finisher, MissionStatus.completed, 500);
            persistEventMission(once, persistUser(), MissionStatus.expired, 500);
            persistProfile(event, finisher, 1, true, 5000);
            persistProfile(event, persistUser(), 1, false, 0);
            entityManager.flush();

            EventSummaryResponse summary = eventStats.getSummary(event.getId(), null, null);

            assertThat(summary.getParticipants()).isEqualTo(2);
            assertThat(summary.getFinishers()).isEqualTo(1);
            assertThat(summary.getFinishRate()).isEqualTo(0.5);
            assertThat(summary.getMissionsAssigned()).isEqualTo(2);
            assertThat(summary.getMissionsCompleted()).isEqualTo(1);
            assertThat(summary.getMissionsExpired()).isEqualTo(1);
            assertThat(summary.getMissionCompletionRate()).isEqualTo(0.5);
            assertThat(summary.getBonusXpPaid()).isEqualTo(5000);
            assertThat(summary.getMissionXpPaid()).isEqualTo(500);
            assertThat(summary.getTotalXpPaid()).isEqualTo(5500);
            assertThat(summary.getAverageMissionsCompleted()).isEqualTo(0.5);
            assertThat(summary.getWeeks()).hasSize(1);
            assertThat(summary.getWeeks().get(0).getMissionsAssigned()).isEqualTo(2);
        }

        @Test
        @DisplayName("the mission leaderboard ranks by runs and shares ties")
        void missionLeaderboardRanksByRuns() {
            Event event = persistEvent();
            MissionTemplate marathon = persistEventTemplate(event, "marathon", true);
            entityManager.flush();

            User heavy = persistUser();
            persistEventMission(marathon, heavy, MissionStatus.completed, 200);
            persistEventMission(marathon, heavy, MissionStatus.completed, 200);
            persistEventMission(marathon, persistUser(), MissionStatus.completed, 200);
            persistEventMission(marathon, persistUser(), MissionStatus.completed, 200);
            entityManager.flush();

            List<EventMissionLeaderboardResponse> board = eventStats
                    .getMissionLeaderboard(event.getId(), marathon.getId(), null, PageRequest.of(0, 20))
                    .getContent();

            assertThat(board).hasSize(3);
            assertThat(board.get(0).getCompletions()).isEqualTo(2);
            assertThat(board.get(0).getXpEarned()).isEqualTo(400);
            assertThat(board).extracting(EventMissionLeaderboardResponse::getRank)
                    .containsExactly(1L, 2L, 2L);
        }
    }

    @Nested
    @DisplayName("Creator credit")
    class CreatorCredit {

        @Test
        @DisplayName("official campaigns are never credited to a person")
        void officialCampaignsAreUserless() {
            User author = persistUser();
            persistCampaign(author, false);
            persistCampaign(author, true);
            persistCampaign(author, true);
            entityManager.flush();

            List<CampaignCreatorResponse> creators = campaignStats
                    .getTopCreators(CampaignStatsFilter.none(), PageRequest.of(0, 20))
                    .getContent();

            assertThat(creators).hasSize(1);
            assertThat(creators.get(0).getCampaigns()).isEqualTo(1);
        }

        @Test
        @DisplayName("someone who only publishes official campaigns does not appear at all")
        void officialOnlyAuthorIsAbsent() {
            User frontman = persistUser();
            persistCampaign(frontman, true);
            entityManager.flush();

            assertThat(campaignStats.getTopCreators(CampaignStatsFilter.none(), PageRequest.of(0, 20)))
                    .isEmpty();
        }

        @Test
        @DisplayName("asking for official campaigns by status still credits nobody")
        void officialStatusFilterCreditsNobody() {
            User author = persistUser();
            persistCampaign(author, true);
            persistCampaign(author, false);
            entityManager.flush();

            CampaignStatsFilter officialOnly = new CampaignStatsFilter(List.of("official"), null, 0);

            assertThat(campaignStats.getTopCreators(officialOnly, PageRequest.of(0, 20))).isEmpty();
        }
    }

    @Nested
    @DisplayName("Every query runs")
    class Smoke {

        @Test
        @DisplayName("mission routes")
        void missionRoutes() {
            persistMission(m -> m.status(MissionStatus.completed).completedAt(Instant.now()).xpReward(120));
            entityManager.flush();

            assertThat(missionStats.getCalibration(NO_FILTER, PageRequest.of(0, 20))).isNotNull();
            assertThat(missionStats.getXpPayouts(NO_FILTER, PageRequest.of(0, 20))).isNotNull();
            assertThat(missionStats.getCompletionRateOverTime(30, "d", NO_FILTER)).isNotNull();
            assertThat(missionStats.getCompletionsPerDay(30, "d", NO_FILTER)).isNotNull();
            assertThat(missionStats.getCompletionsByType(NO_FILTER)).isNotNull();
            assertThat(missionStats.getMostCompleted(NO_FILTER, PageRequest.of(0, 20))).isNotNull();
            assertThat(missionStats.getMostMissionXp(null, PageRequest.of(0, 20))).isNotNull();
        }

        @Test
        @DisplayName("campaign routes")
        void campaignRoutes() {
            CampaignStatsFilter everything = new CampaignStatsFilter(
                    List.of("published", "editing", "curated", "loved", "official"), "GB", 3);

            assertThat(campaignStats.getFunnel(NO_CAMPAIGN_FILTER, PageRequest.of(0, 20))).isNotNull();
            assertThat(campaignStats.getFunnel(everything, PageRequest.of(0, 20))).isNotNull();
            assertThat(campaignStats.getNodeDifficulty(UUID.randomUUID(), null)).isEmpty();
            assertThat(campaignStats.getNodeDifficulty(UUID.randomUUID(), "ES")).isEmpty();
            assertThat(campaignStats.getStartsPerDay(30, "d", everything)).isNotNull();
            assertThat(campaignStats.getCompletionsPerDay(90, "d", everything)).isNotNull();
            assertThat(campaignStats.getMostCompleted(everything, PageRequest.of(0, 20))).isNotNull();
            assertThat(campaignStats.getTopCreators(everything, PageRequest.of(0, 20))).isNotNull();
        }

        @Test
        @DisplayName("event routes")
        void eventRoutes() {
            assertThat(eventStats.getParticipation(null, null, PageRequest.of(0, 20))).isNotNull();
            assertThat(eventStats.getParticipation(List.of(UUID.randomUUID()), "ES", PageRequest.of(0, 20)))
                    .isNotNull();
        }

        @Test
        @DisplayName("filters compose without breaking the SQL")
        void filtersCompose() {
            MissionStatsFilter everything = new MissionStatsFilter(
                    List.of(MissionPool.daily, MissionPool.weekly),
                    List.of(MissionType.PLAY_N_MAPS, MissionType.SCORES_N),
                    playN.getId(), null,
                    List.of(MissionBand.easy, MissionBand.medium),
                    List.of("strong", "top"),
                    600.0, 1100.0, "ES",
                    Instant.now().minus(30, ChronoUnit.DAYS), Instant.now(), 2);

            assertThat(missionStats.getCalibration(everything, PageRequest.of(0, 20))).isNotNull();
            assertThat(missionStats.getXpPayouts(everything, PageRequest.of(0, 20))).isNotNull();
            assertThat(missionStats.getByTier(playN.getId(), everything)).isNotNull();
            assertThat(shortfallStats.getShortfall(playN.getId(), everything)).isNotNull();
            assertThat(missionStats.getCompletionRateOverTime(30, "d", everything)).isNotNull();
            assertThat(missionStats.getCompletionsByType(everything)).isNotNull();
            assertThat(missionStats.getMostCompleted(everything, PageRequest.of(0, 20))).isNotNull();
        }
    }

    private long bucketCount(MissionShortfallResponse row, String label) {
        return row.getBuckets().stream()
                .filter(b -> b.getLabel().equals(label))
                .findFirst().orElseThrow().getCount();
    }

    private static MissionPool poolFor(MissionType type) {
        return switch (type) {
            case STREAK_SUM_N, SNIPE_RIVAL_ANY_MAP, AP_GAIN_OVERALL, BATCH_PLAY_N, PB_RANKED_BEFORE_N,
                    CAMPAIGN_COMPLETE_N, MISSIONS_COMPLETE_N ->
                MissionPool.event;
            default -> MissionPool.daily;
        };
    }

    private UserMission persistMissionForCountry(String country) {
        User user = User.builder().id(nextUserId++).name("Player " + nextUserId).country(country).build();
        entityManager.persist(user);
        UserMission mission = UserMission.builder()
                .user(user)
                .template(playN)
                .pool(MissionPool.daily)
                .band(MissionBand.medium)
                .status(MissionStatus.expired)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();
        entityManager.persist(mission);
        return mission;
    }

    private User persistUser() {
        User user = User.builder().id(nextUserId++).name("Player " + nextUserId).country("ES").build();
        entityManager.persist(user);
        return user;
    }

    private Campaign persistCampaign(User creator, boolean official) {
        Campaign campaign = Campaign.builder()
                .creator(creator)
                .name("Campaign " + UUID.randomUUID())
                .slug("campaign-" + UUID.randomUUID())
                .status(CampaignStatus.PUBLISHED)
                .official(official)
                .publishedAt(Instant.now())
                .build();
        entityManager.persist(campaign);
        return campaign;
    }

    private Event persistEvent() {
        Event event = Event.builder()
                .title("Test Event")
                .slug("test-event-" + UUID.randomUUID())
                .startsAt(Instant.now().minus(28, ChronoUnit.DAYS))
                .endsAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();
        entityManager.persist(event);
        return event;
    }

    private MissionTemplate persistEventTemplate(Event event, String code, boolean repeatable) {
        MissionTemplate template = MissionTemplate.builder()
                .code(code + "_" + UUID.randomUUID())
                .name("Template " + code)
                .description("Do the thing.")
                .type(MissionType.SCORES_N)
                .pool(MissionPool.event)
                .event(event)
                .repeatable(repeatable)
                .build();
        entityManager.persist(template);
        return template;
    }

    private void persistEventMission(MissionTemplate template, User user, MissionStatus status, int xp) {
        entityManager.persist(UserMission.builder()
                .user(user)
                .template(template)
                .pool(MissionPool.event)
                .band(MissionBand.medium)
                .status(status)
                .xpReward(xp)
                .completedAt(status == MissionStatus.completed ? Instant.now() : null)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build());
    }

    private void persistProfile(Event event, User user, int unlockedWeek, boolean finished, int bonusXp) {
        entityManager.persist(UserEventProfile.builder()
                .event(event)
                .user(user)
                .unlockedWeek(unlockedWeek)
                .completedAt(finished ? Instant.now() : null)
                .bonusXp(bonusXp)
                .bonusAwardedAt(bonusXp > 0 ? Instant.now() : null)
                .build());
    }

    private MissionTemplate persistTemplate(String code, MissionType type, MissionPool pool) {
        MissionTemplate template = MissionTemplate.builder()
                .code(code + "_" + UUID.randomUUID())
                .name("Template " + code)
                .description("Do the thing.")
                .type(type)
                .pool(pool)
                .build();
        entityManager.persist(template);
        return template;
    }

    private UserMission persistMission(Function<UserMission.UserMissionBuilder, UserMission.UserMissionBuilder> tweak) {
        User user = User.builder().id(nextUserId++).name("Player " + nextUserId).country("ES").build();
        entityManager.persist(user);

        UserMission mission = tweak.apply(UserMission.builder()
                .user(user)
                .template(playN)
                .pool(MissionPool.daily)
                .band(MissionBand.medium)
                .status(MissionStatus.expired)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)))
                .build();
        entityManager.persist(mission);
        return mission;
    }
}
