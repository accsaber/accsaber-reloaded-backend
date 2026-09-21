package com.accsaber.backend.service.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.accsaber.backend.model.dto.projection.ScoreModifierRow;
import com.accsaber.backend.model.entity.Modifier;
import com.accsaber.backend.model.entity.campaign.BarrierConditionType;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignBarrierAffectedDifficulty;
import com.accsaber.backend.model.entity.campaign.CampaignCompletionMode;
import com.accsaber.backend.model.entity.campaign.CampaignDifficulty;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyModifier;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyModifier.CampaignDifficultyModifierId;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyPath;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyTarget;
import com.accsaber.backend.model.entity.campaign.CampaignModifierRequirement;
import com.accsaber.backend.model.entity.campaign.CampaignPrerequisiteMode;
import com.accsaber.backend.model.entity.campaign.CampaignRequirementType;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.campaign.UserCampaign;
import com.accsaber.backend.model.entity.campaign.UserCampaignScore;
import com.accsaber.backend.model.entity.campaign.UserCampaignStatus;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.event.CampaignCompletedEvent;
import com.accsaber.backend.model.event.CampaignNodeCompletedEvent;
import com.accsaber.backend.repository.campaign.CampaignBarrierAffectedDifficultyRepository;
import com.accsaber.backend.repository.campaign.CampaignCompletionItemRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyItemRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyModifierRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyPathRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyTargetRepository;
import com.accsaber.backend.repository.campaign.UserCampaignRepository;
import com.accsaber.backend.repository.campaign.UserCampaignScoreRepository;
import com.accsaber.backend.repository.score.ScoreModifierLinkRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.service.item.ItemService;
import com.accsaber.backend.service.item.LevelUpAwardService;
import com.accsaber.backend.service.mission.MissionProgressService;

@ExtendWith(MockitoExtension.class)
class CampaignEvaluationServiceTest {

        private static final Instant STARTED = Instant.ofEpochSecond(1_000);
        private static final Instant EARLIER_PLAY = Instant.ofEpochSecond(1_500);
        private static final Instant PLAYED = Instant.ofEpochSecond(2_000);

        @Mock
        private UserCampaignRepository userCampaignRepository;
        @Mock
        private UserCampaignScoreRepository userCampaignScoreRepository;
        @Mock
        private CampaignDifficultyRepository campaignDifficultyRepository;
        @Mock
        private CampaignDifficultyPathRepository campaignDifficultyPathRepository;
        @Mock
        private CampaignBarrierAffectedDifficultyRepository barrierAffectedRepository;
        @Mock
        private CampaignDifficultyItemRepository campaignDifficultyItemRepository;
        @Mock
        private CampaignCompletionItemRepository campaignCompletionItemRepository;
        @Mock
        private CampaignDifficultyModifierRepository campaignDifficultyModifierRepository;
        @Mock
        private CampaignDifficultyTargetRepository campaignDifficultyTargetRepository;
        @Mock
        private ScoreRepository scoreRepository;
        @Mock
        private ScoreModifierLinkRepository scoreModifierLinkRepository;
        @Mock
        private LevelUpAwardService levelUpAwardService;
        @Mock
        private ItemService itemService;
        @Mock
        private MissionProgressService missionProgressService;
        @Mock
        private ApplicationEventPublisher eventPublisher;

        @InjectMocks
        private CampaignEvaluationService service;

        private static final UUID FASTER_SONG = UUID.randomUUID();
        private static final UUID SLOWER_SONG = UUID.randomUUID();
        private static final UUID GHOST_NOTES = UUID.randomUUID();

        private Campaign campaign;
        private CampaignDifficulty a;
        private CampaignDifficulty b;
        private User user;

        @BeforeEach
        void setUp() {
                campaign = Campaign.builder()
                                .id(UUID.randomUUID())
                                .completionMode(CampaignCompletionMode.TERMINAL)
                                .build();
                a = CampaignDifficulty.builder()
                                .id(UUID.randomUUID()).campaign(campaign).active(true).build();
                b = CampaignDifficulty.builder()
                                .id(UUID.randomUUID()).campaign(campaign).active(true).build();
                user = User.builder().id(50L).build();
        }

        @Test
        void agnosticEliminatesOnlyChangedNode() {
                campaign.setProgressionAgnostic(true);
                UserCampaignScore progressA = progress(a);
                UserCampaignScore progressB = progress(b);
                stubGraphAndProgress(List.of(progressA, progressB));

                service.recomputeAfterRequirementChange(campaign, Set.of(a.getId()));

                assertThat(progressA.isActive()).isFalse();
                assertThat(progressB.isActive()).isTrue();
        }

        @Test
        void strictEliminatesChangedNodeAndDescendants() {
                campaign.setProgressionAgnostic(false);
                UserCampaignScore progressA = progress(a);
                UserCampaignScore progressB = progress(b);
                stubGraphAndProgress(List.of(progressA, progressB));

                service.recomputeAfterRequirementChange(campaign, Set.of(a.getId()));

                assertThat(progressA.isActive()).isFalse();
                assertThat(progressB.isActive()).isFalse();
        }

        private void stubGraphAndProgress(List<UserCampaignScore> scores) {
                when(userCampaignScoreRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(scores);
                when(campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(a, b));
                when(campaignDifficultyPathRepository
                                .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(edge(a, b)));
                when(userCampaignRepository.findByUser_IdAndCampaign_IdAndActiveTrue(user.getId(), campaign.getId()))
                                .thenReturn(Optional.of(UserCampaign.builder()
                                                .id(UUID.randomUUID()).user(user).campaign(campaign)
                                                .status(UserCampaignStatus.IN_PROGRESS).build()));
        }

        private UserCampaignScore progress(CampaignDifficulty node) {
                return UserCampaignScore.builder()
                                .id(UUID.randomUUID()).user(user).campaign(campaign)
                                .campaignDifficulty(node).active(true).build();
        }

        private CampaignDifficultyPath edge(CampaignDifficulty from, CampaignDifficulty to) {
                return CampaignDifficultyPath.builder()
                                .id(UUID.randomUUID()).campaignDifficulty(to)
                                .comesFromCampaignDifficulty(from).active(true).build();
        }

        private MapDifficulty mapDifficulty(int maxScore) {
                return MapDifficulty.builder().id(UUID.randomUUID()).maxScore(maxScore).build();
        }

        private CampaignBarrierAffectedDifficulty affected(CampaignDifficulty barrier, CampaignDifficulty node) {
                return CampaignBarrierAffectedDifficulty.builder().barrier(barrier).affectedDifficulty(node).build();
        }

        private UserCampaign inProgressCampaign() {
                return UserCampaign.builder().id(UUID.randomUUID()).user(user).campaign(campaign)
                                .status(UserCampaignStatus.IN_PROGRESS).startedAt(STARTED).build();
        }

        private UserCampaign completedCampaign() {
                return UserCampaign.builder().id(UUID.randomUUID()).user(user).campaign(campaign)
                                .status(UserCampaignStatus.COMPLETED).startedAt(STARTED).build();
        }

        private Score row(MapDifficulty md, int scoreNoMods, Instant timeSet) {
                return Score.builder().id(UUID.randomUUID()).user(user).mapDifficulty(md)
                                .score(scoreNoMods).scoreNoMods(scoreNoMods).timeSet(timeSet).build();
        }

        private void stubEmptyProgress() {
                when(userCampaignScoreRepository.findWithScoreByUser_IdAndCampaign_IdInAndActiveTrue(user.getId(),
                                List.of(campaign.getId()))).thenReturn(List.of());
        }

        @Test
        void barrierRecordedWhenConditionMet() {
                campaign.setStatus(CampaignStatus.PUBLISHED);
                MapDifficulty mdA = mapDifficulty(1_000_000);
                a.setMapDifficulty(mdA);
                a.setRequirementType(CampaignRequirementType.ACC);
                a.setRequirementValue(0.80);
                CampaignDifficulty bar = CampaignDifficulty.builder()
                                .id(UUID.randomUUID()).campaign(campaign).active(true).barrier(true)
                                .barrierConditionType(BarrierConditionType.AVERAGE_ACC)
                                .barrierConditionValue(0.90)
                                .prerequisiteMode(CampaignPrerequisiteMode.OR).build();
                Score score = row(mdA, 950000, PLAYED);
                UserCampaign uc = inProgressCampaign();

                when(userCampaignRepository.findByUser_IdAndStatusInAndActiveTrue(user.getId(),
                                UserCampaignStatus.PARTICIPATING)).thenReturn(List.of(uc));
                when(campaignDifficultyRepository
                                .findByCampaign_IdInAndMapDifficulty_IdAndBarrierFalseAndActiveTrue(List.of(campaign.getId()),
                                mdA.getId())).thenReturn(List.of(a));
                when(campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(a, bar));
                when(campaignDifficultyPathRepository
                                .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(edge(a, bar)));
                stubEmptyProgress();
                when(userCampaignScoreRepository.findByUser_IdAndCampaignDifficulty_IdAndActiveTrue(anyLong(), any()))
                                .thenReturn(Optional.empty());
                when(barrierAffectedRepository.findByBarrier_IdIn(anyList()))
                                .thenReturn(List.of(affected(bar, a)));
                when(scoreRepository.findEligibleCampaignRows(eq(user.getId()), any(), any()))
                                .thenReturn(List.of(row(mdA, 950000, PLAYED)));

                service.evaluateAfterScore(user.getId(), score);

                ArgumentCaptor<UserCampaignScore> captor = ArgumentCaptor.forClass(UserCampaignScore.class);
                verify(userCampaignScoreRepository, atLeastOnce()).save(captor.capture());
                assertThat(captor.getAllValues())
                                .anyMatch(u -> u.getCampaignDifficulty().getId().equals(bar.getId())
                                                && u.getScore() == null);
        }

        @Test
        void orBarrierBreaksWhenAnyAffectedNodeQualifies() {
                campaign.setStatus(CampaignStatus.PUBLISHED);
                MapDifficulty mdA = mapDifficulty(1_000_000);
                MapDifficulty mdC = mapDifficulty(1_000_000);
                a.setMapDifficulty(mdA);
                a.setRequirementType(CampaignRequirementType.ACC);
                a.setRequirementValue(0.80);
                CampaignDifficulty c = CampaignDifficulty.builder()
                                .id(UUID.randomUUID()).campaign(campaign).active(true).mapDifficulty(mdC)
                                .requirementType(CampaignRequirementType.ACC)
                                .requirementValue(0.80).build();
                CampaignDifficulty bar = CampaignDifficulty.builder()
                                .id(UUID.randomUUID()).campaign(campaign).active(true).barrier(true)
                                .barrierConditionType(BarrierConditionType.AVERAGE_ACC)
                                .barrierConditionValue(0.90)
                                .prerequisiteMode(CampaignPrerequisiteMode.OR).build();
                Score score = row(mdA, 950000, PLAYED);
                UserCampaign uc = inProgressCampaign();

                when(userCampaignRepository.findByUser_IdAndStatusInAndActiveTrue(user.getId(),
                                UserCampaignStatus.PARTICIPATING)).thenReturn(List.of(uc));
                when(campaignDifficultyRepository
                                .findByCampaign_IdInAndMapDifficulty_IdAndBarrierFalseAndActiveTrue(List.of(campaign.getId()),
                                mdA.getId())).thenReturn(List.of(a));
                when(campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(a, c, bar));
                when(campaignDifficultyPathRepository
                                .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(edge(a, bar), edge(c, bar)));
                stubEmptyProgress();
                when(userCampaignScoreRepository.findByUser_IdAndCampaignDifficulty_IdAndActiveTrue(anyLong(), any()))
                                .thenReturn(Optional.empty());
                when(barrierAffectedRepository.findByBarrier_IdIn(anyList()))
                                .thenReturn(List.of(affected(bar, a), affected(bar, c)));
                when(scoreRepository.findEligibleCampaignRows(eq(user.getId()), any(), any()))
                                .thenReturn(List.of(row(mdA, 950000, PLAYED)));

                service.evaluateAfterScore(user.getId(), score);

                ArgumentCaptor<UserCampaignScore> captor = ArgumentCaptor.forClass(UserCampaignScore.class);
                verify(userCampaignScoreRepository, atLeastOnce()).save(captor.capture());
                assertThat(captor.getAllValues())
                                .anyMatch(u -> u.getCampaignDifficulty().getId().equals(bar.getId())
                                                && u.getScore() == null);
        }

        @Test
        void completionCountBarrierBreaksWhenEnoughAffectedNodesComplete() {
                CampaignDifficulty bar = stubCompletionCountBarrier(2.0);

                ArgumentCaptor<UserCampaignScore> captor = ArgumentCaptor.forClass(UserCampaignScore.class);
                verify(userCampaignScoreRepository, atLeastOnce()).save(captor.capture());
                assertThat(captor.getAllValues())
                                .anyMatch(u -> u.getCampaignDifficulty().getId().equals(bar.getId())
                                                && u.getScore() == null);
        }

        @Test
        void completionCountBarrierNotBrokenBelowTarget() {
                CampaignDifficulty bar = stubCompletionCountBarrier(3.0);

                ArgumentCaptor<UserCampaignScore> captor = ArgumentCaptor.forClass(UserCampaignScore.class);
                verify(userCampaignScoreRepository, atLeastOnce()).save(captor.capture());
                assertThat(captor.getAllValues())
                                .noneMatch(u -> u.getCampaignDifficulty().getId().equals(bar.getId()));
        }

        private CampaignDifficulty stubCompletionCountBarrier(Double target) {
                campaign.setStatus(CampaignStatus.PUBLISHED);
                MapDifficulty mdA = mapDifficulty(1_000_000);
                a.setMapDifficulty(mdA);
                a.setRequirementType(CampaignRequirementType.ACC);
                a.setRequirementValue(0.80);
                CampaignDifficulty c = CampaignDifficulty.builder()
                                .id(UUID.randomUUID()).campaign(campaign).active(true)
                                .mapDifficulty(mapDifficulty(1_000_000))
                                .requirementType(CampaignRequirementType.ACC)
                                .requirementValue(0.80).build();
                CampaignDifficulty d = CampaignDifficulty.builder()
                                .id(UUID.randomUUID()).campaign(campaign).active(true)
                                .mapDifficulty(mapDifficulty(1_000_000))
                                .requirementType(CampaignRequirementType.ACC)
                                .requirementValue(0.80).build();
                CampaignDifficulty bar = CampaignDifficulty.builder()
                                .id(UUID.randomUUID()).campaign(campaign).active(true).barrier(true)
                                .barrierConditionType(BarrierConditionType.COMPLETION_COUNT)
                                .barrierConditionValue(target)
                                .prerequisiteMode(CampaignPrerequisiteMode.AND).build();
                Score score = row(mdA, 950000, PLAYED);
                UserCampaign uc = inProgressCampaign();

                when(userCampaignRepository.findByUser_IdAndStatusInAndActiveTrue(user.getId(),
                                UserCampaignStatus.PARTICIPATING)).thenReturn(List.of(uc));
                when(campaignDifficultyRepository
                                .findByCampaign_IdInAndMapDifficulty_IdAndBarrierFalseAndActiveTrue(List.of(campaign.getId()),
                                mdA.getId())).thenReturn(List.of(a));
                when(campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(a, c, d, bar));
                when(campaignDifficultyPathRepository
                                .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(edge(a, bar), edge(c, bar), edge(d, bar)));
                when(userCampaignScoreRepository.findWithScoreByUser_IdAndCampaign_IdInAndActiveTrue(user.getId(),
                                List.of(campaign.getId()))).thenReturn(List.of(progress(c)));
                when(userCampaignScoreRepository.findByUser_IdAndCampaignDifficulty_IdAndActiveTrue(anyLong(), any()))
                                .thenReturn(Optional.empty());
                when(barrierAffectedRepository.findByBarrier_IdIn(anyList()))
                                .thenReturn(List.of(affected(bar, a), affected(bar, c), affected(bar, d)));

                service.evaluateAfterScore(user.getId(), score);
                return bar;
        }

        @Test
        void andBarrierNotBrokenUntilAllAffectedComplete() {
                campaign.setStatus(CampaignStatus.PUBLISHED);
                MapDifficulty mdA = mapDifficulty(1_000_000);
                MapDifficulty mdC = mapDifficulty(1_000_000);
                a.setMapDifficulty(mdA);
                a.setRequirementType(CampaignRequirementType.ACC);
                a.setRequirementValue(0.80);
                CampaignDifficulty c = CampaignDifficulty.builder()
                                .id(UUID.randomUUID()).campaign(campaign).active(true).mapDifficulty(mdC)
                                .requirementType(CampaignRequirementType.ACC)
                                .requirementValue(0.80).build();
                CampaignDifficulty bar = CampaignDifficulty.builder()
                                .id(UUID.randomUUID()).campaign(campaign).active(true).barrier(true)
                                .barrierConditionType(BarrierConditionType.AVERAGE_ACC)
                                .barrierConditionValue(0.90)
                                .prerequisiteMode(CampaignPrerequisiteMode.AND).build();
                Score score = row(mdA, 950000, PLAYED);
                UserCampaign uc = inProgressCampaign();

                when(userCampaignRepository.findByUser_IdAndStatusInAndActiveTrue(user.getId(),
                                UserCampaignStatus.PARTICIPATING)).thenReturn(List.of(uc));
                when(campaignDifficultyRepository
                                .findByCampaign_IdInAndMapDifficulty_IdAndBarrierFalseAndActiveTrue(List.of(campaign.getId()),
                                mdA.getId())).thenReturn(List.of(a));
                when(campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(a, c, bar));
                when(campaignDifficultyPathRepository
                                .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(edge(a, bar), edge(c, bar)));
                stubEmptyProgress();
                when(userCampaignScoreRepository.findByUser_IdAndCampaignDifficulty_IdAndActiveTrue(anyLong(), any()))
                                .thenReturn(Optional.empty());
                when(barrierAffectedRepository.findByBarrier_IdIn(anyList()))
                                .thenReturn(List.of(affected(bar, a), affected(bar, c)));
                when(scoreRepository.findEligibleCampaignRows(eq(user.getId()), any(), any()))
                                .thenReturn(List.of(row(mdA, 950000, PLAYED)));

                service.evaluateAfterScore(user.getId(), score);

                ArgumentCaptor<UserCampaignScore> captor = ArgumentCaptor.forClass(UserCampaignScore.class);
                verify(userCampaignScoreRepository, atLeastOnce()).save(captor.capture());
                assertThat(captor.getAllValues())
                                .noneMatch(u -> u.getCampaignDifficulty().getId().equals(bar.getId()));
        }

        @Test
        void strictBarrierWaitsForItsOwnPrerequisite() {
                campaign.setStatus(CampaignStatus.PUBLISHED);
                MapDifficulty mdA = mapDifficulty(1_000_000);
                MapDifficulty mdB = mapDifficulty(1_000_000);
                a.setMapDifficulty(mdA);
                a.setRequirementType(CampaignRequirementType.ACC);
                a.setRequirementValue(0.80);
                b.setMapDifficulty(mdB);
                b.setRequirementType(CampaignRequirementType.ACC);
                b.setRequirementValue(0.80);
                CampaignDifficulty bar = CampaignDifficulty.builder()
                                .id(UUID.randomUUID()).campaign(campaign).active(true).barrier(true)
                                .barrierConditionType(BarrierConditionType.AVERAGE_ACC)
                                .barrierConditionValue(0.85)
                                .prerequisiteMode(CampaignPrerequisiteMode.OR).build();
                Score score = row(mdA, 950000, PLAYED);
                UserCampaign uc = inProgressCampaign();

                when(userCampaignRepository.findByUser_IdAndStatusInAndActiveTrue(user.getId(),
                                UserCampaignStatus.PARTICIPATING)).thenReturn(List.of(uc));
                when(campaignDifficultyRepository
                                .findByCampaign_IdInAndMapDifficulty_IdAndBarrierFalseAndActiveTrue(List.of(campaign.getId()),
                                mdA.getId())).thenReturn(List.of(a));
                when(campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(a, b, bar));
                when(campaignDifficultyPathRepository
                                .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(edge(a, b), edge(b, bar)));
                stubEmptyProgress();
                when(userCampaignScoreRepository.findByUser_IdAndCampaignDifficulty_IdAndActiveTrue(anyLong(), any()))
                                .thenReturn(Optional.empty());
                when(barrierAffectedRepository.findByBarrier_IdIn(anyList()))
                                .thenReturn(List.of(affected(bar, a), affected(bar, b)));
                when(scoreRepository.findEligibleCampaignRows(eq(user.getId()), any(), any()))
                                .thenReturn(List.of(row(mdA, 950000, PLAYED)));

                service.evaluateAfterScore(user.getId(), score);

                ArgumentCaptor<UserCampaignScore> captor = ArgumentCaptor.forClass(UserCampaignScore.class);
                verify(userCampaignScoreRepository, atLeastOnce()).save(captor.capture());
                assertThat(captor.getAllValues())
                                .noneMatch(u -> u.getCampaignDifficulty().getId().equals(bar.getId()));
        }

        @Test
        void chainedBarriersCascadeInOneEvaluation() {
                campaign.setStatus(CampaignStatus.PUBLISHED);
                MapDifficulty mdA = mapDifficulty(1_000_000);
                a.setMapDifficulty(mdA);
                a.setRequirementType(CampaignRequirementType.ACC);
                a.setRequirementValue(0.80);
                CampaignDifficulty gate1 = CampaignDifficulty.builder()
                                .id(UUID.randomUUID()).campaign(campaign).active(true).barrier(true)
                                .barrierConditionType(BarrierConditionType.AVERAGE_ACC)
                                .barrierConditionValue(0.85)
                                .prerequisiteMode(CampaignPrerequisiteMode.OR).build();
                CampaignDifficulty gate2 = CampaignDifficulty.builder()
                                .id(UUID.randomUUID()).campaign(campaign).active(true).barrier(true)
                                .barrierConditionType(BarrierConditionType.AVERAGE_ACC)
                                .barrierConditionValue(0.90)
                                .prerequisiteMode(CampaignPrerequisiteMode.OR).build();
                Score score = row(mdA, 950000, PLAYED);
                UserCampaign uc = inProgressCampaign();

                when(userCampaignRepository.findByUser_IdAndStatusInAndActiveTrue(user.getId(),
                                UserCampaignStatus.PARTICIPATING)).thenReturn(List.of(uc));
                when(campaignDifficultyRepository
                                .findByCampaign_IdInAndMapDifficulty_IdAndBarrierFalseAndActiveTrue(List.of(campaign.getId()),
                                mdA.getId())).thenReturn(List.of(a));
                when(campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(a, gate1, gate2));
                when(campaignDifficultyPathRepository
                                .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(edge(a, gate1), edge(gate1, gate2)));
                stubEmptyProgress();
                when(userCampaignScoreRepository.findByUser_IdAndCampaignDifficulty_IdAndActiveTrue(anyLong(), any()))
                                .thenReturn(Optional.empty());
                when(barrierAffectedRepository.findByBarrier_IdIn(anyList()))
                                .thenReturn(List.of(affected(gate1, a), affected(gate2, a)));
                when(scoreRepository.findEligibleCampaignRows(eq(user.getId()), any(), any()))
                                .thenReturn(List.of(row(mdA, 950000, PLAYED)));

                service.evaluateAfterScore(user.getId(), score);

                ArgumentCaptor<UserCampaignScore> captor = ArgumentCaptor.forClass(UserCampaignScore.class);
                verify(userCampaignScoreRepository, atLeastOnce()).save(captor.capture());
                assertThat(captor.getAllValues())
                                .anyMatch(u -> u.getCampaignDifficulty().getId().equals(gate1.getId()))
                                .anyMatch(u -> u.getCampaignDifficulty().getId().equals(gate2.getId()));
        }

        @Test
        void qualifyingScoreCreditsCampaignXpToWindowMissions() {
                campaign.setStatus(CampaignStatus.CURATED);
                MapDifficulty mdA = mapDifficulty(1_000_000);
                a.setMapDifficulty(mdA);
                a.setRequirementType(CampaignRequirementType.ACC);
                a.setRequirementValue(0.80);
                a.setXp(250);
                Score score = row(mdA, 950000, PLAYED);
                UserCampaign uc = inProgressCampaign();

                when(userCampaignRepository.findByUser_IdAndStatusInAndActiveTrue(user.getId(),
                                UserCampaignStatus.PARTICIPATING)).thenReturn(List.of(uc));
                when(campaignDifficultyRepository
                                .findByCampaign_IdInAndMapDifficulty_IdAndBarrierFalseAndActiveTrue(List.of(campaign.getId()),
                                mdA.getId())).thenReturn(List.of(a));
                when(campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(a));
                when(campaignDifficultyPathRepository
                                .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of());
                stubEmptyProgress();
                when(userCampaignScoreRepository.findByUser_IdAndCampaignDifficulty_IdAndActiveTrue(anyLong(), any()))
                                .thenReturn(Optional.empty());
                when(userCampaignScoreRepository.findByUser_IdAndCampaign_IdAndActiveTrueAndRewardsPaidFalse(
                                user.getId(), campaign.getId())).thenReturn(List.of());

                service.evaluateAfterScore(user.getId(), score);

                verify(levelUpAwardService).addCampaignXp(user.getId(), 250.0);
                verify(missionProgressService).creditXp(user.getId(), 250.0);
        }

        @Test
        void barrierNotRecordedWhenConditionUnmet() {
                campaign.setStatus(CampaignStatus.PUBLISHED);
                MapDifficulty mdA = mapDifficulty(1_000_000);
                a.setMapDifficulty(mdA);
                a.setRequirementType(CampaignRequirementType.ACC);
                a.setRequirementValue(0.80);
                CampaignDifficulty bar = CampaignDifficulty.builder()
                                .id(UUID.randomUUID()).campaign(campaign).active(true).barrier(true)
                                .barrierConditionType(BarrierConditionType.AVERAGE_ACC)
                                .barrierConditionValue(0.99)
                                .prerequisiteMode(CampaignPrerequisiteMode.OR).build();
                Score score = row(mdA, 950000, PLAYED);
                UserCampaign uc = inProgressCampaign();

                when(userCampaignRepository.findByUser_IdAndStatusInAndActiveTrue(user.getId(),
                                UserCampaignStatus.PARTICIPATING)).thenReturn(List.of(uc));
                when(campaignDifficultyRepository
                                .findByCampaign_IdInAndMapDifficulty_IdAndBarrierFalseAndActiveTrue(List.of(campaign.getId()),
                                mdA.getId())).thenReturn(List.of(a));
                when(campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(a, bar));
                when(campaignDifficultyPathRepository
                                .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(edge(a, bar)));
                stubEmptyProgress();
                when(userCampaignScoreRepository.findByUser_IdAndCampaignDifficulty_IdAndActiveTrue(anyLong(), any()))
                                .thenReturn(Optional.empty());
                when(barrierAffectedRepository.findByBarrier_IdIn(anyList()))
                                .thenReturn(List.of(affected(bar, a)));
                when(scoreRepository.findEligibleCampaignRows(eq(user.getId()), any(), any()))
                                .thenReturn(List.of(row(mdA, 950000, PLAYED)));

                service.evaluateAfterScore(user.getId(), score);

                ArgumentCaptor<UserCampaignScore> captor = ArgumentCaptor.forClass(UserCampaignScore.class);
                verify(userCampaignScoreRepository, atLeastOnce()).save(captor.capture());
                assertThat(captor.getAllValues())
                                .noneMatch(u -> u.getCampaignDifficulty().getId().equals(bar.getId()));
        }

        @Test
        void rankRequirementCompletesWhenRankLowEnough() {
                campaign.setStatus(CampaignStatus.PUBLISHED);
                MapDifficulty mdA = mapDifficulty(1_000_000);
                a.setMapDifficulty(mdA);
                a.setRequirementType(CampaignRequirementType.RANK);
                a.setRequirementValue(100.0);
                Score score = Score.builder().id(UUID.randomUUID()).user(user).mapDifficulty(mdA)
                                .score(900000).scoreNoMods(900000).rank(50).timeSet(PLAYED).build();
                UserCampaign uc = inProgressCampaign();

                when(userCampaignRepository.findByUser_IdAndStatusInAndActiveTrue(user.getId(),
                                UserCampaignStatus.PARTICIPATING)).thenReturn(List.of(uc));
                when(campaignDifficultyRepository
                                .findByCampaign_IdInAndMapDifficulty_IdAndBarrierFalseAndActiveTrue(List.of(campaign.getId()),
                                mdA.getId())).thenReturn(List.of(a));
                when(campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(a));
                when(campaignDifficultyPathRepository
                                .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of());
                stubEmptyProgress();
                when(userCampaignScoreRepository.findByUser_IdAndCampaignDifficulty_IdAndActiveTrue(anyLong(), any()))
                                .thenReturn(Optional.empty());

                service.evaluateAfterScore(user.getId(), score);

                ArgumentCaptor<UserCampaignScore> captor = ArgumentCaptor.forClass(UserCampaignScore.class);
                verify(userCampaignScoreRepository, atLeastOnce()).save(captor.capture());
                assertThat(captor.getAllValues())
                                .anyMatch(u -> u.getCampaignDifficulty().getId().equals(a.getId()));
        }

        @Test
        void rankRequirementFailsWhenRankTooHigh() {
                campaign.setStatus(CampaignStatus.PUBLISHED);
                MapDifficulty mdA = mapDifficulty(1_000_000);
                a.setMapDifficulty(mdA);
                a.setRequirementType(CampaignRequirementType.RANK);
                a.setRequirementValue(100.0);
                Score score = Score.builder().id(UUID.randomUUID()).user(user).mapDifficulty(mdA)
                                .score(900000).scoreNoMods(900000).rank(200).timeSet(PLAYED).build();
                UserCampaign uc = inProgressCampaign();

                when(userCampaignRepository.findByUser_IdAndStatusInAndActiveTrue(user.getId(),
                                UserCampaignStatus.PARTICIPATING)).thenReturn(List.of(uc));
                when(campaignDifficultyRepository
                                .findByCampaign_IdInAndMapDifficulty_IdAndBarrierFalseAndActiveTrue(List.of(campaign.getId()),
                                mdA.getId())).thenReturn(List.of(a));
                when(campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(a));
                when(campaignDifficultyPathRepository
                                .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of());
                stubEmptyProgress();

                service.evaluateAfterScore(user.getId(), score);

                verify(userCampaignScoreRepository, never()).save(any());
        }

        @Test
        void requiredModifierMissingBlocksCompletion() {
                MapDifficulty mdA = stubModifierGatedNode();
                Score score = row(mdA, 960000, PLAYED);
                stubNodeModifiers(nodeModifier(a, FASTER_SONG, "FS", CampaignModifierRequirement.REQUIRED));
                when(scoreModifierLinkRepository.findModifierRows(List.of(score.getId()))).thenReturn(List.of());

                service.evaluateAfterScore(user.getId(), score);

                verify(userCampaignScoreRepository, never()).save(any());
        }

        @Test
        void forbiddenModifierBlocksCompletion() {
                MapDifficulty mdA = stubModifierGatedNode();
                Score score = row(mdA, 960000, PLAYED);
                stubNodeModifiers(nodeModifier(a, SLOWER_SONG, "SS", CampaignModifierRequirement.FORBIDDEN));
                when(scoreModifierLinkRepository.findModifierRows(List.of(score.getId())))
                                .thenReturn(List.of(new ScoreModifierRow(score.getId(), SLOWER_SONG, "SS")));

                service.evaluateAfterScore(user.getId(), score);

                verify(userCampaignScoreRepository, never()).save(any());
        }

        @Test
        void requiredModifierPresentCompletesNodeDespiteExtraModifiers() {
                MapDifficulty mdA = stubModifierGatedNode();
                Score score = row(mdA, 960000, PLAYED);
                stubNodeModifiers(nodeModifier(a, FASTER_SONG, "FS", CampaignModifierRequirement.REQUIRED));
                when(scoreModifierLinkRepository.findModifierRows(List.of(score.getId())))
                                .thenReturn(List.of(new ScoreModifierRow(score.getId(), FASTER_SONG, "FS"),
                                                new ScoreModifierRow(score.getId(), GHOST_NOTES, "GN")));
                when(userCampaignScoreRepository.findByUser_IdAndCampaignDifficulty_IdAndActiveTrue(anyLong(), any()))
                                .thenReturn(Optional.empty());

                service.evaluateAfterScore(user.getId(), score);

                ArgumentCaptor<UserCampaignScore> captor = ArgumentCaptor.forClass(UserCampaignScore.class);
                verify(userCampaignScoreRepository, atLeastOnce()).save(captor.capture());
                assertThat(captor.getAllValues())
                                .anyMatch(u -> u.getCampaignDifficulty().getId().equals(a.getId()));
        }

        @Test
        void accRangeRejectsScoreAboveTheUpperBound() {
                MapDifficulty mdA = stubBoundedNode(CampaignRequirementType.ACC,
                                0.90, 0.95);

                service.evaluateAfterScore(user.getId(), row(mdA, 970000, PLAYED));

                verify(userCampaignScoreRepository, never()).save(any());
        }

        @Test
        void accRangeAcceptsScoreInsideBothBounds() {
                MapDifficulty mdA = stubBoundedNode(CampaignRequirementType.ACC,
                                0.90, 0.95);
                stubNodeRecordable();

                service.evaluateAfterScore(user.getId(), row(mdA, 930000, PLAYED));

                verifyNodeRecorded();
        }

        @Test
        void maxBombHitsAcceptsCleanRunAndRejectsBombHeavyOne() {
                MapDifficulty mdA = stubBoundedNode(CampaignRequirementType.BOMB_HITS, null, 3.0);
                stubNodeRecordable();

                service.evaluateAfterScore(user.getId(), bombScore(mdA, 2));

                verifyNodeRecorded();
        }

        @Test
        void maxBombHitsRejectsWhenOverTheLimit() {
                MapDifficulty mdA = stubBoundedNode(CampaignRequirementType.BOMB_HITS, null, 3.0);

                service.evaluateAfterScore(user.getId(), bombScore(mdA, 7));

                verify(userCampaignScoreRepository, never()).save(any());
        }

        @Test
        void bombHitsRequirementRejectsScoreSaberScoreWithNoBombData() {
                MapDifficulty mdA = stubBoundedNode(CampaignRequirementType.BOMB_HITS, null, 3.0);

                service.evaluateAfterScore(user.getId(), bombScore(mdA, null));

                verify(userCampaignScoreRepository, never()).save(any());
        }

        @Test
        void minComboAcceptsAtTheBoundary() {
                MapDifficulty mdA = stubBoundedNode(CampaignRequirementType.COMBO, 500.0, null);
                stubNodeRecordable();

                Score score = Score.builder().id(UUID.randomUUID()).user(user).mapDifficulty(mdA)
                                .score(900000).scoreNoMods(900000).maxCombo(500).timeSet(PLAYED).build();
                service.evaluateAfterScore(user.getId(), score);

                verifyNodeRecorded();
        }

        @Test
        void minComboRejectsBelowTheBoundary() {
                MapDifficulty mdA = stubBoundedNode(CampaignRequirementType.COMBO, 500.0, null);

                Score score = Score.builder().id(UUID.randomUUID()).user(user).mapDifficulty(mdA)
                                .score(900000).scoreNoMods(900000).maxCombo(499).timeSet(PLAYED).build();
                service.evaluateAfterScore(user.getId(), score);

                verify(userCampaignScoreRepository, never()).save(any());
        }

        @Test
        void maxMistakesCountsBadCutsAndMissesTogether() {
                MapDifficulty mdA = stubBoundedNode(CampaignRequirementType.MISTAKES, null, 3.0);
                stubNodeRecordable();

                service.evaluateAfterScore(user.getId(), mistakeScore(mdA, 2, 1));

                verifyNodeRecorded();
        }

        @Test
        void maxMistakesRejectsWhenTheSumExceedsTheLimit() {
                MapDifficulty mdA = stubBoundedNode(CampaignRequirementType.MISTAKES, null, 3.0);

                service.evaluateAfterScore(user.getId(), mistakeScore(mdA, 2, 2));

                verify(userCampaignScoreRepository, never()).save(any());
        }

        @Test
        void mistakesRangeRejectsBelowTheLowerBound() {
                MapDifficulty mdA = stubBoundedNode(CampaignRequirementType.MISTAKES,
                                2.0, 5.0);

                service.evaluateAfterScore(user.getId(), mistakeScore(mdA, 1, 0));

                verify(userCampaignScoreRepository, never()).save(any());
        }

        @Test
        void mistakesRequirementRejectsScoreWithNoMissData() {
                MapDifficulty mdA = stubBoundedNode(CampaignRequirementType.MISTAKES, null, 3.0);

                service.evaluateAfterScore(user.getId(), mistakeScore(mdA, 0, null));

                verify(userCampaignScoreRepository, never()).save(any());
        }

        @Test
        void maxPausesAcceptsAPauselessRun() {
                MapDifficulty mdA = stubBoundedNode(CampaignRequirementType.PAUSES, null, 0.0);
                stubNodeRecordable();

                service.evaluateAfterScore(user.getId(), pauseScore(mdA, 0));

                verifyNodeRecorded();
        }

        @Test
        void maxPausesRejectsWhenOverTheLimit() {
                MapDifficulty mdA = stubBoundedNode(CampaignRequirementType.PAUSES, null, 2.0);

                service.evaluateAfterScore(user.getId(), pauseScore(mdA, 3));

                verify(userCampaignScoreRepository, never()).save(any());
        }

        @Test
        void pausesRangeRejectsBelowTheLowerBound() {
                MapDifficulty mdA = stubBoundedNode(CampaignRequirementType.PAUSES, 1.0, 3.0);

                service.evaluateAfterScore(user.getId(), pauseScore(mdA, 0));

                verify(userCampaignScoreRepository, never()).save(any());
        }

        @Test
        void pausesRequirementRejectsScoreSaberScoreWithNoPauseData() {
                MapDifficulty mdA = stubBoundedNode(CampaignRequirementType.PAUSES, null, 2.0);

                service.evaluateAfterScore(user.getId(), pauseScore(mdA, null));

                verify(userCampaignScoreRepository, never()).save(any());
        }

        private Score pauseScore(MapDifficulty mdA, Integer pauses) {
                return Score.builder().id(UUID.randomUUID()).user(user).mapDifficulty(mdA)
                                .score(900000).scoreNoMods(900000).pauses(pauses).timeSet(PLAYED).build();
        }

        private Score bombScore(MapDifficulty mdA, Integer bombHits) {
                return Score.builder().id(UUID.randomUUID()).user(user).mapDifficulty(mdA)
                                .score(900000).scoreNoMods(900000).bombHits(bombHits).timeSet(PLAYED).build();
        }

        private Score mistakeScore(MapDifficulty mdA, Integer badCuts, Integer misses) {
                return Score.builder().id(UUID.randomUUID()).user(user).mapDifficulty(mdA)
                                .score(900000).scoreNoMods(900000).badCuts(badCuts).misses(misses).timeSet(PLAYED)
                                .build();
        }

        @Test
        void andModeRequiresEveryTarget() {
                MapDifficulty mdA = stubMultiTargetNode(CampaignPrerequisiteMode.AND,
                                target(CampaignRequirementType.ACC, 0.90, null),
                                target(CampaignRequirementType.BOMB_HITS, null, 0.0));

                service.evaluateAfterScore(user.getId(), bombScore(mdA, 4));

                verify(userCampaignScoreRepository, never()).save(any());
        }

        @Test
        void andModeCompletesWhenEveryTargetIsMet() {
                MapDifficulty mdA = stubMultiTargetNode(CampaignPrerequisiteMode.AND,
                                target(CampaignRequirementType.ACC, 0.90, null),
                                target(CampaignRequirementType.BOMB_HITS, null, 0.0));
                stubNodeRecordable();

                Score score = Score.builder().id(UUID.randomUUID()).user(user).mapDifficulty(mdA)
                                .score(950000).scoreNoMods(950000).bombHits(0).timeSet(PLAYED).build();
                service.evaluateAfterScore(user.getId(), score);

                verifyNodeRecorded();
        }

        @Test
        void orModeCompletesOnASingleMetTarget() {
                MapDifficulty mdA = stubMultiTargetNode(CampaignPrerequisiteMode.OR,
                                target(CampaignRequirementType.ACC, 0.99, null),
                                target(CampaignRequirementType.RANK, null, 100.0));
                stubNodeRecordable();

                Score score = Score.builder().id(UUID.randomUUID()).user(user).mapDifficulty(mdA)
                                .score(910000).scoreNoMods(910000).rank(40).rankWhenSet(40).active(true).timeSet(PLAYED)
                                .build();
                service.evaluateAfterScore(user.getId(), score);

                verifyNodeRecorded();
        }

        @Test
        void orModeRejectsWhenNoTargetIsMet() {
                MapDifficulty mdA = stubMultiTargetNode(CampaignPrerequisiteMode.OR,
                                target(CampaignRequirementType.ACC, 0.99, null),
                                target(CampaignRequirementType.RANK, null, 100.0));

                Score score = Score.builder().id(UUID.randomUUID()).user(user).mapDifficulty(mdA)
                                .score(910000).scoreNoMods(910000).rank(400).rankWhenSet(400).active(true)
                                .timeSet(PLAYED).build();
                service.evaluateAfterScore(user.getId(), score);

                verify(userCampaignScoreRepository, never()).save(any());
        }

        private CampaignDifficultyTarget target(CampaignRequirementType type, Double value, Double valueMax) {
                return CampaignDifficultyTarget.builder()
                                .id(UUID.randomUUID()).campaignDifficulty(a)
                                .requirementType(type).requirementValue(value).requirementValueMax(valueMax)
                                .build();
        }

        private MapDifficulty stubMultiTargetNode(CampaignPrerequisiteMode mode,
                        CampaignDifficultyTarget... targets) {
                MapDifficulty mdA = stubBoundedNode(CampaignRequirementType.ACC, 0.90, null);
                a.setTargetMode(mode);
                when(campaignDifficultyTargetRepository.findByCampaignDifficultyIds(List.of(a.getId())))
                                .thenReturn(List.of(targets));
                return mdA;
        }

        private MapDifficulty stubBoundedNode(CampaignRequirementType type, Double min, Double max) {
                return stubBoundedNode(type, min, max, inProgressCampaign());
        }

        private MapDifficulty stubBoundedNode(CampaignRequirementType type, Double min, Double max,
                        UserCampaign uc) {
                MapDifficulty mdA = mapDifficulty(1_000_000);
                a.setRequirementType(type);
                a.setRequirementValue(min);
                a.setRequirementValueMax(max);
                stubNodeQueries(mdA, uc);
                stubEmptyProgress();
                return mdA;
        }

        private void stubNodeQueries(MapDifficulty mdA, UserCampaign uc) {
                campaign.setStatus(CampaignStatus.PUBLISHED);
                a.setMapDifficulty(mdA);
                when(userCampaignRepository.findByUser_IdAndStatusInAndActiveTrue(user.getId(),
                                UserCampaignStatus.PARTICIPATING)).thenReturn(List.of(uc));
                when(campaignDifficultyRepository
                                .findByCampaign_IdInAndMapDifficulty_IdAndBarrierFalseAndActiveTrue(
                                                List.of(campaign.getId()), mdA.getId()))
                                .thenReturn(List.of(a));
                when(campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(a));
                when(campaignDifficultyPathRepository
                                .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of());
        }

        private UserCampaignScore stubNodeWithExistingPointer(MapDifficulty mdA, Score stored, UserCampaign uc) {
                a.setRequirementType(CampaignRequirementType.ACC);
                a.setRequirementValue(0.80);
                stubNodeQueries(mdA, uc);
                UserCampaignScore existing = UserCampaignScore.builder().id(UUID.randomUUID())
                                .user(user).campaign(campaign).campaignDifficulty(a).score(stored).active(true)
                                .build();
                when(userCampaignScoreRepository.findWithScoreByUser_IdAndCampaign_IdInAndActiveTrue(user.getId(),
                                List.of(campaign.getId()))).thenReturn(List.of(existing));
                when(userCampaignScoreRepository.findByUser_IdAndCampaignDifficulty_IdAndActiveTrue(user.getId(),
                                a.getId())).thenReturn(Optional.of(existing));
                return existing;
        }

        private Score apScore(MapDifficulty md, int scoreNoMods, double ap, Instant timeSet) {
                return Score.builder().id(UUID.randomUUID()).user(user).mapDifficulty(md)
                                .score(scoreNoMods).scoreNoMods(scoreNoMods).ap(ap).timeSet(timeSet).build();
        }

        private void stubNodeRecordable() {
                when(userCampaignScoreRepository.findByUser_IdAndCampaignDifficulty_IdAndActiveTrue(anyLong(), any()))
                                .thenReturn(Optional.empty());
        }

        private void verifyNodeRecorded() {
                ArgumentCaptor<UserCampaignScore> captor = ArgumentCaptor.forClass(UserCampaignScore.class);
                verify(userCampaignScoreRepository, atLeastOnce()).save(captor.capture());
                assertThat(captor.getAllValues())
                                .anyMatch(u -> u.getCampaignDifficulty().getId().equals(a.getId()));
        }

        private MapDifficulty stubModifierGatedNode() {
                return stubBoundedNode(CampaignRequirementType.ACC, 0.80, null);
        }

        private void stubNodeModifiers(CampaignDifficultyModifier... links) {
                when(campaignDifficultyModifierRepository.findByCampaignDifficulty_IdIn(List.of(a.getId())))
                                .thenReturn(List.of(links));
        }

        private CampaignDifficultyModifier nodeModifier(CampaignDifficulty node, UUID modifierId, String code,
                        CampaignModifierRequirement requirement) {
                return CampaignDifficultyModifier.builder()
                                .id(new CampaignDifficultyModifierId(node.getId(), modifierId))
                                .campaignDifficulty(node)
                                .modifier(Modifier.builder().id(modifierId).code(code).name(code).build())
                                .requirement(requirement)
                                .build();
        }

        @Test
        void improvedScoreUpdatesCampaignScorePointer() {
                MapDifficulty mdA = mapDifficulty(1_000_000);
                Score oldScore = apScore(mdA, 850000, 5.0, EARLIER_PLAY);
                Score betterScore = apScore(mdA, 960000, 8.0, PLAYED);
                UserCampaignScore existing = stubNodeWithExistingPointer(mdA, oldScore, inProgressCampaign());

                service.evaluateAfterScore(user.getId(), betterScore);

                assertThat(existing.getScore()).isEqualTo(betterScore);
                verify(userCampaignScoreRepository).save(existing);
        }

        @Test
        void worseScoreDoesNotUpdateCampaignScorePointer() {
                MapDifficulty mdA = mapDifficulty(1_000_000);
                Score goodScore = apScore(mdA, 980000, 8.0, EARLIER_PLAY);
                Score worseScore = apScore(mdA, 850000, 5.0, PLAYED);
                UserCampaignScore existing = stubNodeWithExistingPointer(mdA, goodScore, inProgressCampaign());

                service.evaluateAfterScore(user.getId(), worseScore);

                assertThat(existing.getScore()).isEqualTo(goodScore);
                verify(userCampaignScoreRepository, never()).save(any());
        }

        @Test
        void completedCampaignStillRecordsUnfinishedNode() {
                MapDifficulty mdA = stubBoundedNode(CampaignRequirementType.ACC, 0.80, null, completedCampaign());
                stubNodeRecordable();

                service.evaluateAfterScore(user.getId(), row(mdA, 950000, PLAYED));

                verifyNodeRecorded();
        }

        @Test
        void completedCampaignDoesNotUpdateCampaignScorePointer() {
                MapDifficulty mdA = mapDifficulty(1_000_000);
                Score oldScore = apScore(mdA, 850000, 5.0, EARLIER_PLAY);
                Score betterScore = apScore(mdA, 960000, 8.0, PLAYED);
                UserCampaignScore existing = stubNodeWithExistingPointer(mdA, oldScore, completedCampaign());

                service.evaluateAfterScore(user.getId(), betterScore);

                assertThat(existing.getScore()).isEqualTo(oldScore);
                verify(userCampaignScoreRepository, never()).save(any());
        }

        @Test
        void improvementSettlesDependentBarrierSameIntake() {
                campaign.setStatus(CampaignStatus.PUBLISHED);
                MapDifficulty mdA = mapDifficulty(1_000_000);
                a.setMapDifficulty(mdA);
                a.setRequirementType(CampaignRequirementType.ACC);
                a.setRequirementValue(0.80);
                CampaignDifficulty bar = CampaignDifficulty.builder()
                                .id(UUID.randomUUID()).campaign(campaign).active(true).barrier(true)
                                .barrierConditionType(BarrierConditionType.AVERAGE_ACC)
                                .barrierConditionValue(0.95)
                                .prerequisiteMode(CampaignPrerequisiteMode.OR).build();
                Score mediocre = Score.builder().id(UUID.randomUUID()).user(user).mapDifficulty(mdA)
                                .score(850000).scoreNoMods(850000).ap(5.0)
                                .timeSet(EARLIER_PLAY).build();
                Score improved = Score.builder().id(UUID.randomUUID()).user(user).mapDifficulty(mdA)
                                .score(960000).scoreNoMods(960000).ap(8.0)
                                .timeSet(PLAYED).build();
                UserCampaign uc = inProgressCampaign();
                UserCampaignScore existing = UserCampaignScore.builder().id(UUID.randomUUID())
                                .user(user).campaign(campaign).campaignDifficulty(a).score(mediocre).active(true)
                                .build();

                when(userCampaignRepository.findByUser_IdAndStatusInAndActiveTrue(user.getId(),
                                UserCampaignStatus.PARTICIPATING)).thenReturn(List.of(uc));
                when(campaignDifficultyRepository
                                .findByCampaign_IdInAndMapDifficulty_IdAndBarrierFalseAndActiveTrue(List.of(campaign.getId()),
                                mdA.getId())).thenReturn(List.of(a));
                when(campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(a, bar));
                when(campaignDifficultyPathRepository
                                .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(edge(a, bar)));
                when(userCampaignScoreRepository.findWithScoreByUser_IdAndCampaign_IdInAndActiveTrue(user.getId(),
                                List.of(campaign.getId()))).thenReturn(List.of(existing));
                when(userCampaignScoreRepository.findByUser_IdAndCampaignDifficulty_IdAndActiveTrue(user.getId(),
                                a.getId())).thenReturn(Optional.of(existing));
                when(userCampaignScoreRepository.findByUser_IdAndCampaignDifficulty_IdAndActiveTrue(user.getId(),
                                bar.getId())).thenReturn(Optional.empty());
                when(barrierAffectedRepository.findByBarrier_IdIn(anyList()))
                                .thenReturn(List.of(affected(bar, a)));
                when(scoreRepository.findEligibleCampaignRows(eq(user.getId()), any(), any()))
                                .thenReturn(List.of(improved));

                service.evaluateAfterScore(user.getId(), improved);

                assertThat(existing.getScore()).isEqualTo(improved);
                ArgumentCaptor<UserCampaignScore> captor = ArgumentCaptor.forClass(UserCampaignScore.class);
                verify(userCampaignScoreRepository, atLeastOnce()).save(captor.capture());
                assertThat(captor.getAllValues())
                                .anyMatch(u -> u.getCampaignDifficulty().getId().equals(bar.getId())
                                                && u.getScore() == null);
        }

        @Test
        void evaluateParticipatingForUserRecordsCampaignScoreFromCurrentScores() {
                campaign.setStatus(CampaignStatus.PUBLISHED);
                MapDifficulty mdA = mapDifficulty(1_000_000);
                a.setMapDifficulty(mdA);
                a.setRequirementType(CampaignRequirementType.ACC);
                a.setRequirementValue(0.80);
                Score active = Score.builder().id(UUID.randomUUID()).user(user).mapDifficulty(mdA)
                                .score(950000).scoreNoMods(950000).ap(8.0)
                                .timeSet(PLAYED).build();
                UserCampaign uc = inProgressCampaign();

                when(userCampaignRepository.findByUser_IdAndStatusInAndActiveTrue(user.getId(),
                                UserCampaignStatus.PARTICIPATING)).thenReturn(List.of(uc));
                when(campaignDifficultyRepository.findActiveWithMapByCampaignId(campaign.getId()))
                                .thenReturn(List.of(a));
                when(scoreRepository.findEligibleCampaignRows(eq(user.getId()), any(), any()))
                                .thenReturn(List.of(active));
                when(campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(a));
                when(campaignDifficultyPathRepository
                                .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of());
                stubEmptyProgress();
                when(userCampaignScoreRepository.findByUser_IdAndCampaignDifficulty_IdAndActiveTrue(user.getId(),
                                a.getId())).thenReturn(Optional.empty());

                service.evaluateParticipatingForUser(user.getId());

                ArgumentCaptor<UserCampaignScore> captor = ArgumentCaptor.forClass(UserCampaignScore.class);
                verify(userCampaignScoreRepository, atLeastOnce()).save(captor.capture());
                assertThat(captor.getAllValues())
                                .anyMatch(u -> u.getCampaignDifficulty().getId().equals(a.getId())
                                                && u.getScore() == active);
        }

        @Test
        void settleIgnoresScoresSetBeforeNodeUnlock() {
                campaign.setStatus(CampaignStatus.PUBLISHED);
                MapDifficulty mdA = mapDifficulty(1_000_000);
                MapDifficulty mdB = mapDifficulty(1_000_000);
                a.setMapDifficulty(mdA);
                a.setRequirementType(CampaignRequirementType.ACC);
                a.setRequirementValue(0.80);
                b.setMapDifficulty(mdB);
                b.setRequirementType(CampaignRequirementType.ACC);
                b.setRequirementValue(0.80);
                Score playedBeforeUnlock = row(mdB, 990000, EARLIER_PLAY);
                Score unlockingScore = row(mdA, 950000, PLAYED);
                UserCampaign uc = inProgressCampaign();

                when(userCampaignRepository.findByUser_IdAndStatusInAndActiveTrue(user.getId(),
                                UserCampaignStatus.PARTICIPATING)).thenReturn(List.of(uc));
                when(campaignDifficultyRepository.findActiveWithMapByCampaignId(campaign.getId()))
                                .thenReturn(List.of(a, b));
                when(scoreRepository.findEligibleCampaignRows(eq(user.getId()), any(), any()))
                                .thenReturn(List.of(playedBeforeUnlock, unlockingScore));
                when(campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(a, b));
                when(campaignDifficultyPathRepository
                                .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(edge(a, b)));
                stubEmptyProgress();
                when(userCampaignScoreRepository.findByUser_IdAndCampaignDifficulty_IdAndActiveTrue(anyLong(), any()))
                                .thenReturn(Optional.empty());

                service.evaluateParticipatingForUser(user.getId());

                ArgumentCaptor<UserCampaignScore> captor = ArgumentCaptor.forClass(UserCampaignScore.class);
                verify(userCampaignScoreRepository, atLeastOnce()).save(captor.capture());
                assertThat(captor.getAllValues())
                                .anyMatch(u -> u.getCampaignDifficulty().getId().equals(a.getId()))
                                .noneMatch(u -> u.getCampaignDifficulty().getId().equals(b.getId()));
        }

        @Test
        void settleCompletesOnlyThroughAFlaggedTerminalNode() {
                campaign.setStatus(CampaignStatus.PUBLISHED);
                MapDifficulty mdA = mapDifficulty(1_000_000);
                MapDifficulty mdSide = mapDifficulty(1_000_000);
                MapDifficulty mdTerminal = mapDifficulty(1_000_000);
                a.setMapDifficulty(mdA);
                a.setRequirementType(CampaignRequirementType.ACC);
                a.setRequirementValue(0.80);
                CampaignDifficulty side = CampaignDifficulty.builder()
                                .id(UUID.randomUUID()).campaign(campaign).active(true).mapDifficulty(mdSide)
                                .requirementType(CampaignRequirementType.ACC)
                                .requirementValue(0.80).build();
                CampaignDifficulty terminal = CampaignDifficulty.builder()
                                .id(UUID.randomUUID()).campaign(campaign).active(true).mapDifficulty(mdTerminal)
                                .terminal(true)
                                .requirementType(CampaignRequirementType.ACC)
                                .requirementValue(0.80).build();
                CampaignDifficulty bar = CampaignDifficulty.builder()
                                .id(UUID.randomUUID()).campaign(campaign).active(true).barrier(true)
                                .barrierConditionType(BarrierConditionType.AVERAGE_ACC)
                                .barrierConditionValue(0.99)
                                .prerequisiteMode(CampaignPrerequisiteMode.AND).build();
                UserCampaign uc = inProgressCampaign();

                when(userCampaignRepository.findByUser_IdAndStatusInAndActiveTrue(user.getId(),
                                UserCampaignStatus.PARTICIPATING)).thenReturn(List.of(uc));
                when(campaignDifficultyRepository.findActiveWithMapByCampaignId(campaign.getId()))
                                .thenReturn(List.of(a, side, terminal));
                when(campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(a, side, terminal, bar));
                when(campaignDifficultyPathRepository
                                .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(edge(a, side), edge(a, bar), edge(a, terminal)));
                when(barrierAffectedRepository.findByBarrier_IdIn(anyList()))
                                .thenReturn(List.of(affected(bar, side)));
                when(scoreRepository.findEligibleCampaignRows(eq(user.getId()), any(), any()))
                                .thenReturn(List.of(row(mdA, 900_000, EARLIER_PLAY),
                                                row(mdTerminal, 900_000, PLAYED)));
                stubEmptyProgress();
                when(userCampaignScoreRepository.findByUser_IdAndCampaignDifficulty_IdAndActiveTrue(anyLong(), any()))
                                .thenReturn(Optional.empty());

                service.evaluateParticipatingForUser(user.getId());

                assertThat(uc.getStatus()).isEqualTo(UserCampaignStatus.COMPLETED);
        }

        private UserCampaign singleNodeCompletingFixture(UserCampaignStatus initialStatus) {
                campaign.setStatus(CampaignStatus.PUBLISHED);
                MapDifficulty mdA = mapDifficulty(1_000_000);
                a.setMapDifficulty(mdA);
                a.setTerminal(true);
                a.setRequirementType(CampaignRequirementType.RANK);
                a.setRequirementValue(100.0);
                UserCampaign uc = UserCampaign.builder().id(UUID.randomUUID()).user(user).campaign(campaign)
                                .status(initialStatus).completionRewardsPaid(false).startedAt(STARTED).build();

                when(userCampaignRepository.findByUser_IdAndStatusInAndActiveTrue(user.getId(),
                                UserCampaignStatus.PARTICIPATING)).thenReturn(List.of(uc));
                when(campaignDifficultyRepository
                                .findByCampaign_IdInAndMapDifficulty_IdAndBarrierFalseAndActiveTrue(List.of(campaign.getId()),
                                mdA.getId())).thenReturn(List.of(a));
                when(campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(a));
                when(campaignDifficultyPathRepository
                                .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of());
                stubEmptyProgress();
                when(userCampaignScoreRepository.findByUser_IdAndCampaignDifficulty_IdAndActiveTrue(anyLong(), any()))
                                .thenReturn(Optional.empty());
                return uc;
        }

        private Score rankQualifyingScore() {
                return Score.builder().id(UUID.randomUUID()).user(user).mapDifficulty(a.getMapDifficulty())
                                .score(900000).scoreNoMods(900000).rank(50).timeSet(PLAYED).build();
        }

        @Test
        void completingCampaignPublishesCampaignCompletedEventOnce() {
                UserCampaign uc = singleNodeCompletingFixture(UserCampaignStatus.IN_PROGRESS);

                service.evaluateAfterScore(user.getId(), rankQualifyingScore());

                assertThat(uc.getStatus()).isEqualTo(UserCampaignStatus.COMPLETED);
                ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
                verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
                List<CampaignCompletedEvent> completed = captor.getAllValues().stream()
                                .filter(CampaignCompletedEvent.class::isInstance)
                                .map(CampaignCompletedEvent.class::cast)
                                .toList();
                assertThat(completed).singleElement().satisfies(e -> {
                        assertThat(e.userId()).isEqualTo(user.getId());
                        assertThat(e.campaignId()).isEqualTo(campaign.getId());
                        assertThat(e.campaignStatus()).isEqualTo(CampaignStatus.PUBLISHED);
                        assertThat(e.completedAt()).isNotNull();
                });
        }

        @Test
        void completingNodePublishesNodeCompletedEvent() {
                singleNodeCompletingFixture(UserCampaignStatus.IN_PROGRESS);

                service.evaluateAfterScore(user.getId(), rankQualifyingScore());

                ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
                verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
                List<CampaignNodeCompletedEvent> nodes = captor.getAllValues().stream()
                                .filter(CampaignNodeCompletedEvent.class::isInstance)
                                .map(CampaignNodeCompletedEvent.class::cast)
                                .toList();
                assertThat(nodes).singleElement().satisfies(e -> {
                        assertThat(e.userId()).isEqualTo(user.getId());
                        assertThat(e.campaignId()).isEqualTo(campaign.getId());
                        assertThat(e.nodeId()).isEqualTo(a.getId());
                        assertThat(e.completedAt()).isNotNull();
                        assertThat(e.silent()).isFalse();
                });
        }

        @Test
        void legacyStartBackfillPublishesSilentEvents() {
                campaign.setStatus(CampaignStatus.PUBLISHED);
                campaign.setLegacy(true);
                MapDifficulty mdA = mapDifficulty(1_000_000);
                a.setMapDifficulty(mdA);
                a.setTerminal(true);
                a.setRequirementType(CampaignRequirementType.ACC);
                a.setRequirementValue(0.80);

                when(userCampaignRepository.findByUser_IdAndCampaign_IdAndActiveTrue(user.getId(), campaign.getId()))
                                .thenReturn(Optional.of(inProgressCampaign()));
                when(campaignDifficultyRepository.findActiveWithMapByCampaignId(campaign.getId()))
                                .thenReturn(List.of(a));
                when(scoreRepository.findEligibleCampaignRows(eq(user.getId()), any(), any()))
                                .thenReturn(List.of(row(mdA, 900_000, PLAYED)));
                when(campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(a));
                when(campaignDifficultyPathRepository
                                .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of());
                stubEmptyProgress();
                when(userCampaignScoreRepository.findByUser_IdAndCampaignDifficulty_IdAndActiveTrue(anyLong(), any()))
                                .thenReturn(Optional.empty());

                service.importLegacyScores(user.getId(), campaign.getId());

                ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
                verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
                assertThat(captor.getAllValues())
                                .filteredOn(CampaignNodeCompletedEvent.class::isInstance)
                                .isNotEmpty()
                                .allSatisfy(e -> assertThat(((CampaignNodeCompletedEvent) e).silent()).isTrue());
                assertThat(captor.getAllValues())
                                .filteredOn(CampaignCompletedEvent.class::isInstance)
                                .isNotEmpty()
                                .allSatisfy(e -> assertThat(((CampaignCompletedEvent) e).silent()).isTrue());
        }

        @Test
        void anyFlaggedTerminalCompletesTheCampaign() {
                UserCampaign uc = twoTerminalFixture(true);

                service.evaluateParticipatingForUser(user.getId());

                assertThat(uc.getStatus()).isEqualTo(UserCampaignStatus.COMPLETED);
        }

        @Test
        void clearingAnUnflaggedSinkDoesNotCompleteTheCampaign() {
                UserCampaign uc = twoTerminalFixture(false);

                service.evaluateParticipatingForUser(user.getId());

                assertThat(uc.getStatus()).isEqualTo(UserCampaignStatus.IN_PROGRESS);
        }

        private UserCampaign twoTerminalFixture(boolean playedBranchIsTerminal) {
                campaign.setStatus(CampaignStatus.PUBLISHED);
                campaign.setProgressionAgnostic(true);
                MapDifficulty mdA = mapDifficulty(1_000_000);
                MapDifficulty mdB = mapDifficulty(1_000_000);
                a.setMapDifficulty(mdA);
                a.setRequirementType(CampaignRequirementType.ACC);
                a.setRequirementValue(0.80);
                a.setTerminal(playedBranchIsTerminal);
                b.setMapDifficulty(mdB);
                b.setRequirementType(CampaignRequirementType.ACC);
                b.setRequirementValue(0.80);
                b.setTerminal(true);
                UserCampaign uc = inProgressCampaign();

                when(userCampaignRepository.findByUser_IdAndStatusInAndActiveTrue(user.getId(),
                                UserCampaignStatus.PARTICIPATING)).thenReturn(List.of(uc));
                when(campaignDifficultyRepository.findActiveWithMapByCampaignId(campaign.getId()))
                                .thenReturn(List.of(a, b));
                when(campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(a, b));
                when(campaignDifficultyPathRepository
                                .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of());
                when(scoreRepository.findEligibleCampaignRows(eq(user.getId()), any(), any()))
                                .thenReturn(List.of(row(mdA, 900_000, PLAYED)));
                stubEmptyProgress();
                when(userCampaignScoreRepository.findByUser_IdAndCampaignDifficulty_IdAndActiveTrue(anyLong(), any()))
                                .thenReturn(Optional.empty());
                return uc;
        }

        @Test
        void clearingTheTerminalAtTheEndOfAChainCompletesTheCampaign() {
                UserCampaign uc = chainedTerminalFixture(false, true);

                service.evaluateParticipatingForUser(user.getId());

                assertThat(uc.getStatus()).isEqualTo(UserCampaignStatus.COMPLETED);
        }

        @Test
        void clearingTheTerminalWithoutItsChainLeavesTheCampaignInProgress() {
                UserCampaign uc = chainedTerminalFixture(true, false);

                service.evaluateParticipatingForUser(user.getId());

                assertThat(uc.getStatus()).isEqualTo(UserCampaignStatus.IN_PROGRESS);
        }

        private UserCampaign chainedTerminalFixture(boolean agnostic, boolean clearThePrerequisite) {
                campaign.setStatus(CampaignStatus.PUBLISHED);
                campaign.setProgressionAgnostic(agnostic);
                MapDifficulty mdA = mapDifficulty(1_000_000);
                MapDifficulty mdB = mapDifficulty(1_000_000);
                a.setMapDifficulty(mdA);
                a.setRequirementType(CampaignRequirementType.ACC);
                a.setRequirementValue(0.80);
                b.setMapDifficulty(mdB);
                b.setRequirementType(CampaignRequirementType.ACC);
                b.setRequirementValue(0.80);
                b.setTerminal(true);
                UserCampaign uc = inProgressCampaign();

                List<Score> rows = clearThePrerequisite
                                ? List.of(row(mdA, 900_000, EARLIER_PLAY), row(mdB, 900_000, PLAYED))
                                : List.of(row(mdB, 900_000, PLAYED));

                when(userCampaignRepository.findByUser_IdAndStatusInAndActiveTrue(user.getId(),
                                UserCampaignStatus.PARTICIPATING)).thenReturn(List.of(uc));
                when(campaignDifficultyRepository.findActiveWithMapByCampaignId(campaign.getId()))
                                .thenReturn(List.of(a, b));
                when(campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(a, b));
                when(campaignDifficultyPathRepository
                                .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(edge(a, b)));
                when(scoreRepository.findEligibleCampaignRows(eq(user.getId()), any(), any())).thenReturn(rows);
                stubEmptyProgress();
                when(userCampaignScoreRepository.findByUser_IdAndCampaignDifficulty_IdAndActiveTrue(anyLong(), any()))
                                .thenReturn(Optional.empty());
                return uc;
        }

        @Test
        void clearingEitherDisconnectedBranchCompletesTheCampaign() {
                campaign.setStatus(CampaignStatus.PUBLISHED);
                MapDifficulty mdC = mapDifficulty(1_000_000);
                MapDifficulty mdD = mapDifficulty(1_000_000);
                a.setMapDifficulty(mapDifficulty(1_000_000));
                a.setRequirementType(CampaignRequirementType.ACC);
                a.setRequirementValue(0.80);
                b.setMapDifficulty(mapDifficulty(1_000_000));
                b.setRequirementType(CampaignRequirementType.ACC);
                b.setRequirementValue(0.80);
                b.setTerminal(true);
                CampaignDifficulty c = CampaignDifficulty.builder()
                                .id(UUID.randomUUID()).campaign(campaign).active(true).mapDifficulty(mdC)
                                .requirementType(CampaignRequirementType.ACC)
                                .requirementValue(0.80).build();
                CampaignDifficulty d = CampaignDifficulty.builder()
                                .id(UUID.randomUUID()).campaign(campaign).active(true).mapDifficulty(mdD)
                                .terminal(true)
                                .requirementType(CampaignRequirementType.ACC)
                                .requirementValue(0.80).build();
                UserCampaign uc = inProgressCampaign();

                when(userCampaignRepository.findByUser_IdAndStatusInAndActiveTrue(user.getId(),
                                UserCampaignStatus.PARTICIPATING)).thenReturn(List.of(uc));
                when(campaignDifficultyRepository.findActiveWithMapByCampaignId(campaign.getId()))
                                .thenReturn(List.of(a, b, c, d));
                when(campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(a, b, c, d));
                when(campaignDifficultyPathRepository
                                .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(edge(a, b), edge(c, d)));
                when(scoreRepository.findEligibleCampaignRows(eq(user.getId()), any(), any()))
                                .thenReturn(List.of(row(mdC, 900_000, EARLIER_PLAY), row(mdD, 900_000, PLAYED)));
                stubEmptyProgress();
                when(userCampaignScoreRepository.findByUser_IdAndCampaignDifficulty_IdAndActiveTrue(anyLong(), any()))
                                .thenReturn(Optional.empty());

                service.evaluateParticipatingForUser(user.getId());

                assertThat(uc.getStatus()).isEqualTo(UserCampaignStatus.COMPLETED);
        }

        @Test
        void reEvaluatingAnAlreadyCompletedCampaignDoesNotRepublish() {
                singleNodeCompletingFixture(UserCampaignStatus.COMPLETED);

                service.evaluateAfterScore(user.getId(), rankQualifyingScore());

                verify(eventPublisher, never()).publishEvent(any(CampaignCompletedEvent.class));
        }
}
