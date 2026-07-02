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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.entity.campaign.BarrierConditionType;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignBarrierAffectedDifficulty;
import com.accsaber.backend.model.entity.campaign.CampaignCompletionMode;
import com.accsaber.backend.model.entity.campaign.CampaignDifficulty;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyPath;
import com.accsaber.backend.model.entity.campaign.CampaignPrerequisiteMode;
import com.accsaber.backend.model.entity.campaign.CampaignRequirementType;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.campaign.UserCampaign;
import com.accsaber.backend.model.entity.campaign.UserCampaignScore;
import com.accsaber.backend.model.entity.campaign.UserCampaignStatus;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.campaign.CampaignBarrierAffectedDifficultyRepository;
import com.accsaber.backend.repository.campaign.CampaignCompletionItemRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyItemRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyPathRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyRepository;
import com.accsaber.backend.repository.campaign.UserCampaignRepository;
import com.accsaber.backend.repository.campaign.UserCampaignScoreRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.service.item.ItemService;
import com.accsaber.backend.service.item.LevelUpAwardService;

@ExtendWith(MockitoExtension.class)
class CampaignEvaluationServiceTest {

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
        private ScoreRepository scoreRepository;
        @Mock
        private LevelUpAwardService levelUpAwardService;
        @Mock
        private ItemService itemService;

        @InjectMocks
        private CampaignEvaluationService service;

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

        @Test
        void barrierRecordedWhenConditionMet() {
                campaign.setStatus(CampaignStatus.PUBLISHED);
                MapDifficulty mdA = mapDifficulty(1_000_000);
                a.setMapDifficulty(mdA);
                a.setRequirementType(CampaignRequirementType.ACC);
                a.setRequirementValue(new BigDecimal("0.80"));
                CampaignDifficulty bar = CampaignDifficulty.builder()
                                .id(UUID.randomUUID()).campaign(campaign).active(true).barrier(true)
                                .barrierConditionType(BarrierConditionType.AVERAGE_ACC)
                                .barrierConditionValue(new BigDecimal("0.90"))
                                .prerequisiteMode(CampaignPrerequisiteMode.OR).build();
                Score score = Score.builder().id(UUID.randomUUID()).user(user).mapDifficulty(mdA)
                                .score(950000).scoreNoMods(950000).build();
                UserCampaign uc = UserCampaign.builder().id(UUID.randomUUID()).user(user).campaign(campaign)
                                .status(UserCampaignStatus.IN_PROGRESS).build();

                when(userCampaignRepository.findByUser_IdAndStatusAndActiveTrue(user.getId(),
                                UserCampaignStatus.IN_PROGRESS)).thenReturn(List.of(uc));
                when(campaignDifficultyRepository.findByCampaign_IdAndMapDifficulty_IdAndActiveTrue(campaign.getId(),
                                mdA.getId())).thenReturn(Optional.of(a));
                when(campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(a, bar));
                when(campaignDifficultyPathRepository
                                .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(edge(a, bar)));
                when(userCampaignScoreRepository.findByUser_IdAndCampaign_IdAndActiveTrue(user.getId(),
                                campaign.getId())).thenReturn(List.of());
                when(userCampaignScoreRepository.findByUser_IdAndCampaignDifficulty_IdAndActiveTrue(anyLong(), any()))
                                .thenReturn(Optional.empty());
                when(barrierAffectedRepository.findByBarrier_IdIn(anyList()))
                                .thenReturn(List.of(affected(bar, a)));
                when(scoreRepository.findByUser_IdAndMapDifficulty_IdInAndActiveTrue(eq(user.getId()), anyList()))
                                .thenReturn(List.of(score));

                service.evaluateAfterScore(user.getId(), score);

                ArgumentCaptor<UserCampaignScore> captor = ArgumentCaptor.forClass(UserCampaignScore.class);
                verify(userCampaignScoreRepository, atLeastOnce()).save(captor.capture());
                assertThat(captor.getAllValues())
                                .anyMatch(u -> u.getCampaignDifficulty().getId().equals(bar.getId())
                                                && u.getScore() == null);
        }

        @Test
        void barrierNotRecordedWhenConditionUnmet() {
                campaign.setStatus(CampaignStatus.PUBLISHED);
                MapDifficulty mdA = mapDifficulty(1_000_000);
                a.setMapDifficulty(mdA);
                a.setRequirementType(CampaignRequirementType.ACC);
                a.setRequirementValue(new BigDecimal("0.80"));
                CampaignDifficulty bar = CampaignDifficulty.builder()
                                .id(UUID.randomUUID()).campaign(campaign).active(true).barrier(true)
                                .barrierConditionType(BarrierConditionType.AVERAGE_ACC)
                                .barrierConditionValue(new BigDecimal("0.99"))
                                .prerequisiteMode(CampaignPrerequisiteMode.OR).build();
                Score score = Score.builder().id(UUID.randomUUID()).user(user).mapDifficulty(mdA)
                                .score(950000).scoreNoMods(950000).build();
                UserCampaign uc = UserCampaign.builder().id(UUID.randomUUID()).user(user).campaign(campaign)
                                .status(UserCampaignStatus.IN_PROGRESS).build();

                when(userCampaignRepository.findByUser_IdAndStatusAndActiveTrue(user.getId(),
                                UserCampaignStatus.IN_PROGRESS)).thenReturn(List.of(uc));
                when(campaignDifficultyRepository.findByCampaign_IdAndMapDifficulty_IdAndActiveTrue(campaign.getId(),
                                mdA.getId())).thenReturn(Optional.of(a));
                when(campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(a, bar));
                when(campaignDifficultyPathRepository
                                .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(edge(a, bar)));
                when(userCampaignScoreRepository.findByUser_IdAndCampaign_IdAndActiveTrue(user.getId(),
                                campaign.getId())).thenReturn(List.of());
                when(userCampaignScoreRepository.findByUser_IdAndCampaignDifficulty_IdAndActiveTrue(anyLong(), any()))
                                .thenReturn(Optional.empty());
                when(barrierAffectedRepository.findByBarrier_IdIn(anyList()))
                                .thenReturn(List.of(affected(bar, a)));
                when(scoreRepository.findByUser_IdAndMapDifficulty_IdInAndActiveTrue(eq(user.getId()), anyList()))
                                .thenReturn(List.of(score));

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
                a.setRequirementValue(new BigDecimal("100"));
                Score score = Score.builder().id(UUID.randomUUID()).user(user).mapDifficulty(mdA)
                                .score(900000).scoreNoMods(900000).rank(50).build();
                UserCampaign uc = UserCampaign.builder().id(UUID.randomUUID()).user(user).campaign(campaign)
                                .status(UserCampaignStatus.IN_PROGRESS).build();

                when(userCampaignRepository.findByUser_IdAndStatusAndActiveTrue(user.getId(),
                                UserCampaignStatus.IN_PROGRESS)).thenReturn(List.of(uc));
                when(campaignDifficultyRepository.findByCampaign_IdAndMapDifficulty_IdAndActiveTrue(campaign.getId(),
                                mdA.getId())).thenReturn(Optional.of(a));
                when(campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(a));
                when(campaignDifficultyPathRepository
                                .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of());
                when(userCampaignScoreRepository.findByUser_IdAndCampaign_IdAndActiveTrue(user.getId(),
                                campaign.getId())).thenReturn(List.of());
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
                a.setRequirementValue(new BigDecimal("100"));
                Score score = Score.builder().id(UUID.randomUUID()).user(user).mapDifficulty(mdA)
                                .score(900000).scoreNoMods(900000).rank(200).build();
                UserCampaign uc = UserCampaign.builder().id(UUID.randomUUID()).user(user).campaign(campaign)
                                .status(UserCampaignStatus.IN_PROGRESS).build();

                when(userCampaignRepository.findByUser_IdAndStatusAndActiveTrue(user.getId(),
                                UserCampaignStatus.IN_PROGRESS)).thenReturn(List.of(uc));
                when(campaignDifficultyRepository.findByCampaign_IdAndMapDifficulty_IdAndActiveTrue(campaign.getId(),
                                mdA.getId())).thenReturn(Optional.of(a));
                when(campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of(a));
                when(campaignDifficultyPathRepository
                                .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaign.getId()))
                                .thenReturn(List.of());
                when(userCampaignScoreRepository.findByUser_IdAndCampaign_IdAndActiveTrue(user.getId(),
                                campaign.getId())).thenReturn(List.of());

                service.evaluateAfterScore(user.getId(), score);

                verify(userCampaignScoreRepository, never()).save(any());
        }
}
