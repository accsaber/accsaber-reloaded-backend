package com.accsaber.backend.service.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignCompletionMode;
import com.accsaber.backend.model.entity.campaign.CampaignDifficulty;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyPath;
import com.accsaber.backend.model.entity.campaign.UserCampaign;
import com.accsaber.backend.model.entity.campaign.UserCampaignScore;
import com.accsaber.backend.model.entity.campaign.UserCampaignStatus;
import com.accsaber.backend.model.entity.user.User;
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
}
