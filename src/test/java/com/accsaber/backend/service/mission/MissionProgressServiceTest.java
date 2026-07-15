package com.accsaber.backend.service.mission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.map.Batch;
import com.accsaber.backend.model.entity.map.BatchStatus;
import com.accsaber.backend.model.entity.mission.MissionPool;
import com.accsaber.backend.model.entity.mission.MissionStatus;
import com.accsaber.backend.model.entity.mission.MissionTemplate;
import com.accsaber.backend.model.entity.mission.MissionType;
import com.accsaber.backend.model.entity.mission.UserMission;
import com.accsaber.backend.model.entity.user.UserRelationType;
import com.accsaber.backend.model.event.CampaignCompletedEvent;
import com.accsaber.backend.model.event.ScoreSubmittedEvent;
import com.accsaber.backend.repository.map.BatchRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.mission.UserMissionRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;
import com.accsaber.backend.repository.user.UserRelationRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.item.ItemService;
import com.accsaber.backend.service.item.LevelUpAwardService;

@ExtendWith(MockitoExtension.class)
class MissionProgressServiceTest {

        private static final Long USER_ID = 76561198000000001L;
        private static final String OVERALL = "overall";

        @Mock
        private UserMissionRepository userMissionRepository;
        @Mock
        private ScoreRepository scoreRepository;
        @Mock
        private LevelUpAwardService levelUpAwardService;
        @Mock
        private ItemService itemService;
        @Mock
        private UserRepository userRepository;
        @Mock
        private ApplicationEventPublisher eventPublisher;
        @Mock
        private EventMissionService eventMissionService;
        @Mock
        private UserCategoryStatisticsRepository statisticsRepository;
        @Mock
        private BatchRepository batchRepository;
        @Mock
        private MapDifficultyRepository mapDifficultyRepository;
        @Mock
        private UserRelationRepository userRelationRepository;

        @InjectMocks
        private MissionProgressService service;

        private UUID mapDifficultyId;

        @BeforeEach
        void setUp() {
                ReflectionTestUtils.setField(service, "missionsEnabled", true);
                mapDifficultyId = UUID.randomUUID();
                lenient().when(userRepository.findById(anyLong())).thenReturn(Optional.empty());
        }

        private UserMission mission(MissionType type) {
                return UserMission.builder()
                                .id(UUID.randomUUID())
                                .template(MissionTemplate.builder().type(type).name(type.name()).build())
                                .pool(MissionPool.event)
                                .status(MissionStatus.active)
                                .progressCount(0)
                                .progressAp(BigDecimal.ZERO)
                                .xpReward(0)
                                .build();
        }

        private ScoreResponse score(boolean active) {
                return ScoreResponse.builder()
                                .id(UUID.randomUUID())
                                .userId(String.valueOf(USER_ID))
                                .mapDifficultyId(mapDifficultyId)
                                .categoryId(UUID.randomUUID())
                                .score(1_000_000)
                                .active(active)
                                .timeSet(Instant.now())
                                .build();
        }

        private void givenMissions(UserMission... missions) {
                when(userMissionRepository.findAllActiveByUser(USER_ID)).thenReturn(List.of(missions));
        }

        @Nested
        class ApGainOverall {

                @Test
                void accumulatesGainAndCompletesAtTarget() {
                        UserMission m = mission(MissionType.AP_GAIN_OVERALL);
                        m.setTargetAp(new BigDecimal("5"));
                        givenMissions(m);
                        when(statisticsRepository.findActiveApGainOverPrevious(USER_ID, OVERALL))
                                        .thenReturn(Optional.of(new BigDecimal("3.5")));

                        service.onScoreSubmitted(new ScoreSubmittedEvent(score(true)));

                        assertThat(m.getProgressAp()).isEqualByComparingTo("3.5");
                        assertThat(m.getStatus()).isEqualTo(MissionStatus.active);

                        service.onScoreSubmitted(new ScoreSubmittedEvent(score(true)));

                        assertThat(m.getProgressAp()).isEqualByComparingTo("7.0");
                        assertThat(m.getStatus()).isEqualTo(MissionStatus.completed);
                }

                @Test
                void ignoresInactiveScoreSoStaleGainIsNotRecounted() {
                        UserMission m = mission(MissionType.AP_GAIN_OVERALL);
                        m.setTargetAp(new BigDecimal("5"));
                        givenMissions(m);

                        service.onScoreSubmitted(new ScoreSubmittedEvent(score(false)));

                        assertThat(m.getProgressAp()).isEqualByComparingTo("0");
                        verify(statisticsRepository, never()).findActiveApGainOverPrevious(anyLong(), anyString());
                }

                @Test
                void ignoresNonPositiveGain() {
                        UserMission m = mission(MissionType.AP_GAIN_OVERALL);
                        m.setTargetAp(new BigDecimal("5"));
                        givenMissions(m);
                        when(statisticsRepository.findActiveApGainOverPrevious(USER_ID, OVERALL))
                                        .thenReturn(Optional.of(BigDecimal.ZERO));

                        service.onScoreSubmitted(new ScoreSubmittedEvent(score(true)));

                        assertThat(m.getProgressAp()).isEqualByComparingTo("0");
                        assertThat(m.getStatus()).isEqualTo(MissionStatus.active);
                }
        }

        @Nested
        class CampaignComplete {

                private CampaignCompletedEvent event(CampaignStatus status) {
                        return new CampaignCompletedEvent(USER_ID, UUID.randomUUID(), status, Instant.now());
                }

                @Test
                void curatedOnlyIgnoresNonCuratedCampaign() {
                        UserMission m = mission(MissionType.CAMPAIGN_COMPLETE_N);
                        m.setTargetCount(1);
                        m.setTargetCuratedOnly(true);
                        givenMissions(m);

                        service.onCampaignCompleted(event(CampaignStatus.PUBLISHED));

                        assertThat(m.getProgressCount()).isZero();
                        assertThat(m.getStatus()).isEqualTo(MissionStatus.active);
                }

                @Test
                void curatedOnlyCountsCuratedCampaign() {
                        UserMission m = mission(MissionType.CAMPAIGN_COMPLETE_N);
                        m.setTargetCount(1);
                        m.setTargetCuratedOnly(true);
                        givenMissions(m);

                        service.onCampaignCompleted(event(CampaignStatus.CURATED));

                        assertThat(m.getProgressCount()).isEqualTo(1);
                        assertThat(m.getStatus()).isEqualTo(MissionStatus.completed);
                }

                @Test
                void withoutCuratedOnlyCountsAnyCampaign() {
                        UserMission m = mission(MissionType.CAMPAIGN_COMPLETE_N);
                        m.setTargetCount(1);
                        givenMissions(m);

                        service.onCampaignCompleted(event(CampaignStatus.PUBLISHED));

                        assertThat(m.getProgressCount()).isEqualTo(1);
                        assertThat(m.getStatus()).isEqualTo(MissionStatus.completed);
                }

                @Test
                void campaignMissionIsNotReachedByScoreEvaluation() {
                        UserMission m = mission(MissionType.CAMPAIGN_COMPLETE_N);
                        m.setTargetCount(1);
                        givenMissions(m);

                        service.onScoreSubmitted(new ScoreSubmittedEvent(score(true)));

                        assertThat(m.getProgressCount()).isZero();
                }

                @Test
                void scoreMissionIsNotReachedByCampaignEvaluation() {
                        UserMission m = mission(MissionType.SCORES_N);
                        m.setTargetCount(1);
                        givenMissions(m);

                        service.onCampaignCompleted(event(CampaignStatus.CURATED));

                        assertThat(m.getProgressCount()).isZero();
                }
        }

        @Nested
        class SnipeRivalAnyMap {

                @Test
                void noRivalsMeansNoProgress() {
                        UserMission m = mission(MissionType.SNIPE_RIVAL_ANY_MAP);
                        m.setTargetCount(1);
                        givenMissions(m);
                        when(userRelationRepository.findActiveTargetUserIdsByTypes(USER_ID,
                                        List.of(UserRelationType.rival))).thenReturn(List.of());

                        service.onScoreSubmitted(new ScoreSubmittedEvent(score(true)));

                        assertThat(m.getProgressCount()).isZero();
                        verify(scoreRepository, never()).existsRivalScoreBelow(any(), any(), any());
                }

                @Test
                void beatingARivalProgresses() {
                        UserMission m = mission(MissionType.SNIPE_RIVAL_ANY_MAP);
                        m.setTargetCount(1);
                        givenMissions(m);
                        when(userRelationRepository.findActiveTargetUserIdsByTypes(USER_ID,
                                        List.of(UserRelationType.rival))).thenReturn(List.of(2L));
                        when(scoreRepository.existsRivalScoreBelow(eq(mapDifficultyId), eq(List.of(2L)),
                                        eq(1_000_000))).thenReturn(true);

                        service.onScoreSubmitted(new ScoreSubmittedEvent(score(true)));

                        assertThat(m.getProgressCount()).isEqualTo(1);
                        assertThat(m.getStatus()).isEqualTo(MissionStatus.completed);
                }

                @Test
                void notBeatingAnyRivalDoesNotProgress() {
                        UserMission m = mission(MissionType.SNIPE_RIVAL_ANY_MAP);
                        m.setTargetCount(1);
                        givenMissions(m);
                        when(userRelationRepository.findActiveTargetUserIdsByTypes(USER_ID,
                                        List.of(UserRelationType.rival))).thenReturn(List.of(2L));
                        when(scoreRepository.existsRivalScoreBelow(any(), any(), any())).thenReturn(false);

                        service.onScoreSubmitted(new ScoreSubmittedEvent(score(true)));

                        assertThat(m.getProgressCount()).isZero();
                }

                @Test
                void rivalsAreResolvedOncePerScoreAcrossMissions() {
                        UserMission a = mission(MissionType.SNIPE_RIVAL_ANY_MAP);
                        a.setTargetCount(5);
                        UserMission b = mission(MissionType.SNIPE_RIVAL_ANY_MAP);
                        b.setTargetCount(5);
                        givenMissions(a, b);
                        when(userRelationRepository.findActiveTargetUserIdsByTypes(USER_ID,
                                        List.of(UserRelationType.rival))).thenReturn(List.of(2L));
                        when(scoreRepository.existsRivalScoreBelow(any(), any(), any())).thenReturn(true);

                        service.onScoreSubmitted(new ScoreSubmittedEvent(score(true)));

                        verify(userRelationRepository, org.mockito.Mockito.times(1))
                                        .findActiveTargetUserIdsByTypes(anyLong(), any());
                        assertThat(a.getProgressCount()).isEqualTo(1);
                        assertThat(b.getProgressCount()).isEqualTo(1);
                }
        }

        @Nested
        class BatchPlayN {

                private Batch releasedBatch(UUID id) {
                        return Batch.builder().id(id).status(BatchStatus.RELEASED).build();
                }

                @Test
                void countsMapsInLatestReleasedBatch() {
                        UUID batchId = UUID.randomUUID();
                        UserMission m = mission(MissionType.BATCH_PLAY_N);
                        m.setTargetCount(1);
                        givenMissions(m);
                        when(batchRepository.findFirstByStatusOrderByReleasedAtDesc(BatchStatus.RELEASED))
                                        .thenReturn(Optional.of(releasedBatch(batchId)));
                        when(mapDifficultyRepository.existsByIdAndBatch_Id(mapDifficultyId, batchId))
                                        .thenReturn(true);

                        service.onScoreSubmitted(new ScoreSubmittedEvent(score(true)));

                        assertThat(m.getProgressCount()).isEqualTo(1);
                        assertThat(m.getStatus()).isEqualTo(MissionStatus.completed);
                }

                @Test
                void ignoresMapsOutsideLatestBatch() {
                        UUID batchId = UUID.randomUUID();
                        UserMission m = mission(MissionType.BATCH_PLAY_N);
                        m.setTargetCount(1);
                        givenMissions(m);
                        when(batchRepository.findFirstByStatusOrderByReleasedAtDesc(BatchStatus.RELEASED))
                                        .thenReturn(Optional.of(releasedBatch(batchId)));
                        when(mapDifficultyRepository.existsByIdAndBatch_Id(mapDifficultyId, batchId))
                                        .thenReturn(false);

                        service.onScoreSubmitted(new ScoreSubmittedEvent(score(true)));

                        assertThat(m.getProgressCount()).isZero();
                }

                @Test
                void countsPartialAttemptsLikePlayNMaps() {
                        UUID batchId = UUID.randomUUID();
                        UserMission m = mission(MissionType.BATCH_PLAY_N);
                        m.setTargetCount(2);
                        givenMissions(m);
                        when(batchRepository.findFirstByStatusOrderByReleasedAtDesc(BatchStatus.RELEASED))
                                        .thenReturn(Optional.of(releasedBatch(batchId)));
                        when(mapDifficultyRepository.existsByIdAndBatch_Id(mapDifficultyId, batchId))
                                        .thenReturn(true);

                        ScoreResponse partial = score(false).toBuilder().partial(true).build();
                        service.onScoreSubmitted(new ScoreSubmittedEvent(partial));

                        assertThat(m.getProgressCount()).isEqualTo(1);
                }

                @Test
                void latestBatchIsResolvedOncePerScoreAcrossMissions() {
                        UUID batchId = UUID.randomUUID();
                        UserMission a = mission(MissionType.BATCH_PLAY_N);
                        a.setTargetCount(5);
                        UserMission b = mission(MissionType.BATCH_PLAY_N);
                        b.setTargetCount(5);
                        givenMissions(a, b);
                        when(batchRepository.findFirstByStatusOrderByReleasedAtDesc(BatchStatus.RELEASED))
                                        .thenReturn(Optional.of(releasedBatch(batchId)));
                        when(mapDifficultyRepository.existsByIdAndBatch_Id(mapDifficultyId, batchId))
                                        .thenReturn(true);

                        service.onScoreSubmitted(new ScoreSubmittedEvent(score(true)));

                        verify(batchRepository, times(1)).findFirstByStatusOrderByReleasedAtDesc(any());
                        assertThat(a.getProgressCount()).isEqualTo(1);
                        assertThat(b.getProgressCount()).isEqualTo(1);
                }

                @Test
                void noReleasedBatchMeansNoProgress() {
                        UserMission m = mission(MissionType.BATCH_PLAY_N);
                        m.setTargetCount(1);
                        givenMissions(m);
                        when(batchRepository.findFirstByStatusOrderByReleasedAtDesc(BatchStatus.RELEASED))
                                        .thenReturn(Optional.empty());

                        service.onScoreSubmitted(new ScoreSubmittedEvent(score(true)));

                        assertThat(m.getProgressCount()).isZero();
                }
        }

        @Nested
        class PbRankedBefore {

                @Test
                void countsMapsRankedBeforeTheCutoff() {
                        Instant cutoff = Instant.parse("2023-01-01T00:00:00Z");
                        UserMission m = mission(MissionType.PB_RANKED_BEFORE_N);
                        m.setTargetCount(1);
                        m.setTargetRankedBefore(cutoff);
                        givenMissions(m);
                        when(mapDifficultyRepository.existsByIdAndRankedAtBefore(mapDifficultyId, cutoff))
                                        .thenReturn(true);

                        service.onScoreSubmitted(new ScoreSubmittedEvent(score(true)));

                        assertThat(m.getProgressCount()).isEqualTo(1);
                        assertThat(m.getStatus()).isEqualTo(MissionStatus.completed);
                }

                @Test
                void ignoresMapsRankedAfterTheCutoff() {
                        Instant cutoff = Instant.parse("2023-01-01T00:00:00Z");
                        UserMission m = mission(MissionType.PB_RANKED_BEFORE_N);
                        m.setTargetCount(1);
                        m.setTargetRankedBefore(cutoff);
                        givenMissions(m);
                        when(mapDifficultyRepository.existsByIdAndRankedAtBefore(mapDifficultyId, cutoff))
                                        .thenReturn(false);

                        service.onScoreSubmitted(new ScoreSubmittedEvent(score(true)));

                        assertThat(m.getProgressCount()).isZero();
                }

                @Test
                void ignoresInactiveScore() {
                        UserMission m = mission(MissionType.PB_RANKED_BEFORE_N);
                        m.setTargetCount(1);
                        m.setTargetRankedBefore(Instant.parse("2023-01-01T00:00:00Z"));
                        givenMissions(m);

                        service.onScoreSubmitted(new ScoreSubmittedEvent(score(false)));

                        assertThat(m.getProgressCount()).isZero();
                        verify(mapDifficultyRepository, never()).existsByIdAndRankedAtBefore(any(), any());
                }
        }

        @Nested
        class StreakSum {

                @Test
                void sumsEveryAttemptIncludingPartials() {
                        UserMission m = mission(MissionType.STREAK_SUM_N);
                        m.setTargetCount(100);
                        givenMissions(m);

                        ScoreResponse partial = score(false).toBuilder().streak115(60).partial(true).build();
                        service.onScoreSubmitted(new ScoreSubmittedEvent(partial));
                        assertThat(m.getProgressCount()).isEqualTo(60);

                        ScoreResponse full = score(true).toBuilder().streak115(40).build();
                        service.onScoreSubmitted(new ScoreSubmittedEvent(full));
                        assertThat(m.getProgressCount()).isEqualTo(100);
                        assertThat(m.getStatus()).isEqualTo(MissionStatus.completed);
                }

                @Test
                void ignoresScoresWithNoStreak() {
                        UserMission m = mission(MissionType.STREAK_SUM_N);
                        m.setTargetCount(100);
                        givenMissions(m);

                        service.onScoreSubmitted(new ScoreSubmittedEvent(score(true)));

                        assertThat(m.getProgressCount()).isZero();
                }
        }

        @Nested
        class NoDoubleCompletion {

                @Test
                void missionCompletedByXpCreditIsNotCompletedAgainByTheOuterLoop() {
                        UserMission rewarding = mission(MissionType.SCORES_N);
                        rewarding.setTargetCount(1);
                        rewarding.setXpReward(200);
                        UserMission window = mission(MissionType.XP_IN_WINDOW);
                        window.setTargetXp(100);
                        window.setXpReward(100);
                        givenMissions(rewarding, window);

                        ScoreResponse scored = score(true).toBuilder().xpGained(new BigDecimal("50")).build();
                        service.onScoreSubmitted(new ScoreSubmittedEvent(scored));

                        assertThat(rewarding.getStatus()).isEqualTo(MissionStatus.completed);
                        assertThat(window.getStatus()).isEqualTo(MissionStatus.completed);
                        verify(levelUpAwardService, times(1))
                                        .addMissionXp(eq(USER_ID), eq(BigDecimal.valueOf(100)));
                        verify(eventMissionService, times(1)).onEventMissionCompleted(eq(window), eq(USER_ID));
                }
        }

        @Nested
        class Gating {

                @Test
                void expiredMissionIsNotEvaluated() {
                        UserMission m = mission(MissionType.SCORES_N);
                        m.setTargetCount(1);
                        m.setExpiresAt(Instant.now().minusSeconds(60));
                        givenMissions(m);

                        service.onScoreSubmitted(new ScoreSubmittedEvent(score(true)));

                        assertThat(m.getProgressCount()).isZero();
                }

                @Test
                void disabledMissionsShortCircuit() {
                        ReflectionTestUtils.setField(service, "missionsEnabled", false);

                        service.onScoreSubmitted(new ScoreSubmittedEvent(score(true)));
                        service.onCampaignCompleted(new CampaignCompletedEvent(USER_ID, UUID.randomUUID(),
                                        CampaignStatus.CURATED, Instant.now()));

                        verify(userMissionRepository, never()).findAllActiveByUser(anyLong());
                }
        }

        @Nested
        class CategoryScope {

                @Test
                void categoryScopedMissionIgnoresOtherCategories() {
                        UserMission m = mission(MissionType.BATCH_PLAY_N);
                        m.setTargetCount(1);
                        m.setCategory(Category.builder().id(UUID.randomUUID()).code("tech_acc").build());
                        givenMissions(m);

                        service.onScoreSubmitted(new ScoreSubmittedEvent(score(true)));

                        assertThat(m.getProgressCount()).isZero();
                        verify(batchRepository, never()).findFirstByStatusOrderByReleasedAtDesc(any());
                }
        }
}
