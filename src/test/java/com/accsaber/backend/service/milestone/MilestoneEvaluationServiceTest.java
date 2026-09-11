package com.accsaber.backend.service.milestone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.accsaber.backend.model.dto.MilestoneQuerySpec;
import com.accsaber.backend.model.dto.MilestoneQuerySpec.SelectSpec;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.milestone.Milestone;
import com.accsaber.backend.model.entity.milestone.MilestoneSet;
import com.accsaber.backend.model.entity.milestone.MilestoneSetItem;
import com.accsaber.backend.model.entity.milestone.MilestoneTier;
import com.accsaber.backend.model.entity.milestone.UserMilestoneLink;
import com.accsaber.backend.model.entity.milestone.UserMilestoneSetBonus;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.milestone.MilestoneItemRepository;
import com.accsaber.backend.repository.milestone.MilestoneRepository;
import com.accsaber.backend.repository.milestone.MilestoneSetItemRepository;
import com.accsaber.backend.repository.milestone.MilestoneSetRepository;
import com.accsaber.backend.repository.milestone.UserMilestoneLinkRepository;
import com.accsaber.backend.repository.milestone.UserMilestoneSetBonusRepository;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.item.ItemType;
import com.accsaber.backend.repository.item.UserItemLinkRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.item.ItemService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.accsaber.backend.service.item.LevelUpAwardService;
import com.accsaber.backend.service.milestone.MilestoneQueryBuilderService.Progress;

@ExtendWith(MockitoExtension.class)
class MilestoneEvaluationServiceTest {

        @Mock
        private MilestoneRepository milestoneRepository;
        @Mock
        private UserMilestoneLinkRepository userMilestoneLinkRepository;
        @Mock
        private UserMilestoneSetBonusRepository userMilestoneSetBonusRepository;
        @Mock
        private UserRepository userRepository;
        @Mock
        private MilestoneQueryBuilderService queryBuilderService;
        @Mock
        private ItemService itemService;
        @Mock
        private LevelUpAwardService levelUpAwardService;
        @Mock
        private MilestoneItemRepository milestoneItemRepository;
        @Mock
        private MilestoneSetItemRepository milestoneSetItemRepository;
        @Mock
        private MilestoneSetRepository milestoneSetRepository;
        @Mock
        private UserItemLinkRepository userItemLinkRepository;
        @Mock
        private ApplicationEventPublisher eventPublisher;

        @InjectMocks
        private MilestoneEvaluationService service;

        private static final Long USER_ID = 42L;

        private MilestoneSet milestoneSet;
        private MilestoneQuerySpec querySpec;
        private Category scoreCategory;
        private MapDifficulty scoreMapDifficulty;

        @BeforeEach
        void setUp() {
                milestoneSet = MilestoneSet.builder()
                                .id(UUID.randomUUID())
                                .title("Test Set")
                                .build();

                querySpec = new MilestoneQuerySpec(
                                new SelectSpec("MAX", "ap"),
                                "scores",
                                List.of(new MilestoneQuerySpec.FilterSpec("active", "=", true)));

                scoreCategory = Category.builder()
                                .id(UUID.randomUUID())
                                .name("True Acc")
                                .build();

                scoreMapDifficulty = MapDifficulty.builder()
                                .id(UUID.randomUUID())
                                .category(scoreCategory)
                                .build();
        }

        private Score buildScoreWithMapDifficulty() {
                return Score.builder()
                                .id(UUID.randomUUID())
                                .mapDifficulty(scoreMapDifficulty)
                                .build();
        }

        private Score buildScoreWithMapDifficulty(Long blScoreId) {
                return Score.builder()
                                .id(UUID.randomUUID())
                                .mapDifficulty(scoreMapDifficulty)
                                .blScoreId(blScoreId)
                                .build();
        }

        private Milestone buildMilestone(Double target, String comparison) {
                return buildMilestone(target, comparison, null);
        }

        private Milestone buildMilestone(Double target, String comparison, Category category) {
                return Milestone.builder()
                                .id(UUID.randomUUID())
                                .milestoneSet(milestoneSet)
                                .title("Test Milestone")
                                .type("milestone")
                                .tier(MilestoneTier.gold)
                                .xp((double) (300))
                                .querySpec(querySpec)
                                .targetValue(target)
                                .comparison(comparison)
                                .category(category)
                                .active(true)
                                .build();
        }

        private void mockScopedQuery(List<Milestone> milestones) {
                when(milestoneRepository.findActiveUncompletedForUserScoped(
                                eq(USER_ID), eq(scoreCategory.getId()), eq(scoreMapDifficulty.getId())))
                                .thenReturn(milestones);
        }

        private void mockBatchEval(List<Milestone> milestones, Double value) {
                Map<UUID, Double> results = new java.util.HashMap<>();
                for (Milestone m : milestones) {
                        results.put(m.getId(), value);
                }
                when(queryBuilderService.evaluateBatch(any(), eq(USER_ID))).thenReturn(results);
        }

        private void mockNoExistingLinks() {
                when(userMilestoneLinkRepository.findByUser_IdAndMilestone_IdIn(eq(USER_ID), any()))
                                .thenReturn(List.of());
        }

        private void mockExistingLinks(UserMilestoneLink... links) {
                when(userMilestoneLinkRepository.findByUser_IdAndMilestone_IdIn(eq(USER_ID), any()))
                                .thenReturn(List.of(links));
        }

        @SuppressWarnings("unchecked")
        private List<UserMilestoneLink> captureSavedLinks() {
                ArgumentCaptor<List<UserMilestoneLink>> captor = ArgumentCaptor.forClass(List.class);
                verify(userMilestoneLinkRepository).saveAll(captor.capture());
                return captor.getValue();
        }

        @Nested
        class EvaluateAfterScore {

                @Test
                void currentValueMeetsGteTarget_marksCompleted() {
                        Milestone milestone = buildMilestone((double) (900), "GTE");
                        Score newScore = buildScoreWithMapDifficulty();
                        User user = User.builder().id(USER_ID).build();

                        mockScopedQuery(List.of(milestone));
                        mockBatchEval(List.of(milestone), (double) (950));
                        mockNoExistingLinks();
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
                        when(userMilestoneSetBonusRepository.existsByUser_IdAndMilestoneSet_Id(USER_ID,
                                        milestoneSet.getId())).thenReturn(false);
                        when(milestoneRepository.countActiveBySetId(milestoneSet.getId())).thenReturn(1L);
                        when(userMilestoneLinkRepository.countCompletedByUserAndSet(USER_ID, milestoneSet.getId()))
                                        .thenReturn(0L);

                        var result = service.evaluateAfterScore(USER_ID, newScore);

                        assertThat(result.completedMilestones()).containsExactly(milestone);

                        List<UserMilestoneLink> saved = captureSavedLinks();
                        assertThat(saved).hasSize(1);
                        assertThat(saved.get(0).isCompleted()).isTrue();
                        assertThat(saved.get(0).getCompletedAt()).isNotNull();
                        assertThat(saved.get(0).getAchievedWithScore()).isEqualTo(newScore);
                        assertThat(saved.get(0).getProgress()).isEqualByComparingTo((double) (950));
                }

                @Test
                void currentValueBelowGteTarget_notCompleted() {
                        Milestone milestone = buildMilestone((double) (900), "GTE");
                        Score newScore = buildScoreWithMapDifficulty();
                        User user = User.builder().id(USER_ID).build();

                        mockScopedQuery(List.of(milestone));
                        mockBatchEval(List.of(milestone), (double) (750));
                        mockNoExistingLinks();
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

                        var result = service.evaluateAfterScore(USER_ID, newScore);

                        assertThat(result.completedMilestones()).isEmpty();

                        List<UserMilestoneLink> saved = captureSavedLinks();
                        assertThat(saved.get(0).isCompleted()).isFalse();
                }

                @Test
                void lteComparison_completedWhenValueBelowTarget() {
                        Milestone milestone = buildMilestone((double) (10), "LTE");
                        Score newScore = buildScoreWithMapDifficulty();
                        User user = User.builder().id(USER_ID).build();

                        mockScopedQuery(List.of(milestone));
                        mockBatchEval(List.of(milestone), (double) (5));
                        mockNoExistingLinks();
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
                        when(userMilestoneSetBonusRepository.existsByUser_IdAndMilestoneSet_Id(USER_ID,
                                        milestoneSet.getId())).thenReturn(false);
                        when(milestoneRepository.countActiveBySetId(milestoneSet.getId())).thenReturn(1L);
                        when(userMilestoneLinkRepository.countCompletedByUserAndSet(USER_ID, milestoneSet.getId()))
                                        .thenReturn(0L);

                        var result = service.evaluateAfterScore(USER_ID, newScore);

                        assertThat(result.completedMilestones()).containsExactly(milestone);
                }

                @Test
                void lteComparison_notCompletedWhenValueAboveTarget() {
                        Milestone milestone = buildMilestone((double) (10), "LTE");
                        Score newScore = buildScoreWithMapDifficulty();
                        User user = User.builder().id(USER_ID).build();

                        mockScopedQuery(List.of(milestone));
                        mockBatchEval(List.of(milestone), (double) (15));
                        mockNoExistingLinks();
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

                        var result = service.evaluateAfterScore(USER_ID, newScore);

                        assertThat(result.completedMilestones()).isEmpty();
                }

                @Test
                void categoryIdPassedToQueryBuilder_whenMilestoneHasCategory() {
                        UUID milestoneCategoryId = UUID.randomUUID();
                        Category milestoneCategory = Category.builder().id(milestoneCategoryId).name("True Acc")
                                        .build();
                        Milestone milestone = buildMilestone((double) (500), "GTE", milestoneCategory);
                        Score newScore = buildScoreWithMapDifficulty();
                        User user = User.builder().id(USER_ID).build();

                        mockScopedQuery(List.of(milestone));
                        mockBatchEval(List.of(milestone), (double) (300));
                        mockNoExistingLinks();
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

                        service.evaluateAfterScore(USER_ID, newScore);

                        verify(queryBuilderService).evaluateBatch(any(), eq(USER_ID));
                }

                @Test
                void scoreSaberScore_evaluatesEveryMilestone() {
                        Milestone milestone = buildMilestone((double) (10), "GTE");
                        Score ssScore = buildScoreWithMapDifficulty(null);
                        User user = User.builder().id(USER_ID).build();

                        mockScopedQuery(List.of(milestone));
                        mockBatchEval(List.of(milestone), (double) (5));
                        mockNoExistingLinks();
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

                        service.evaluateAfterScore(USER_ID, ssScore);

                        verify(queryBuilderService).evaluateBatch(any(), eq(USER_ID));
                }

                @Test
                void noUncompletedMilestones_returnsEmptyResult() {
                        Score newScore = buildScoreWithMapDifficulty();

                        mockScopedQuery(List.of());

                        var result = service.evaluateAfterScore(USER_ID, newScore);

                        assertThat(result.completedMilestones()).isEmpty();
                        assertThat(result.completedSets()).isEmpty();
                        verify(queryBuilderService, never()).evaluateBatch(any(), any());
                }

                @Test
                void existingLink_progressUpdated_notDoubleCompleted() {
                        Milestone milestone = buildMilestone((double) (900), "GTE");
                        Score newScore = buildScoreWithMapDifficulty();
                        UserMilestoneLink existingLink = UserMilestoneLink.builder()
                                        .milestone(milestone)
                                        .progress((double) (850))
                                        .completed(false)
                                        .build();

                        mockScopedQuery(List.of(milestone));
                        mockBatchEval(List.of(milestone), (double) (950));
                        mockExistingLinks(existingLink);
                        when(userMilestoneLinkRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
                        when(userMilestoneSetBonusRepository.existsByUser_IdAndMilestoneSet_Id(USER_ID,
                                        milestoneSet.getId())).thenReturn(false);
                        when(milestoneRepository.countActiveBySetId(milestoneSet.getId())).thenReturn(1L);
                        when(userMilestoneLinkRepository.countCompletedByUserAndSet(USER_ID, milestoneSet.getId()))
                                        .thenReturn(0L);

                        service.evaluateAfterScore(USER_ID, newScore);

                        assertThat(existingLink.isCompleted()).isTrue();
                        assertThat(existingLink.getProgress()).isEqualByComparingTo((double) (950));
                }

                @Test
                void scopedQuery_usesMapDifficultyAndCategoryFromScore() {
                        Milestone milestone = buildMilestone((double) (100), "GTE");
                        Score newScore = buildScoreWithMapDifficulty();
                        User user = User.builder().id(USER_ID).build();

                        mockScopedQuery(List.of(milestone));
                        mockBatchEval(List.of(milestone), (double) (50));
                        mockNoExistingLinks();
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

                        service.evaluateAfterScore(USER_ID, newScore);

                        verify(milestoneRepository).findActiveUncompletedForUserScoped(
                                        USER_ID, scoreCategory.getId(), scoreMapDifficulty.getId());
                        verify(milestoneRepository, never()).findActiveUncompletedForUser(any());
                }

                @Test
                void completedAt_usesScoreTimeSet() {
                        Milestone milestone = buildMilestone((double) (100), "GTE");
                        Instant scoreTime = Instant.parse("2025-06-15T12:00:00Z");
                        Score newScore = Score.builder()
                                        .id(UUID.randomUUID())
                                        .mapDifficulty(scoreMapDifficulty)
                                        .timeSet(scoreTime)
                                        .build();
                        User user = User.builder().id(USER_ID).build();

                        mockScopedQuery(List.of(milestone));
                        mockBatchEval(List.of(milestone), (double) (200));
                        mockNoExistingLinks();
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
                        when(userMilestoneSetBonusRepository.existsByUser_IdAndMilestoneSet_Id(USER_ID,
                                        milestoneSet.getId())).thenReturn(false);
                        when(milestoneRepository.countActiveBySetId(milestoneSet.getId())).thenReturn(1L);
                        when(userMilestoneLinkRepository.countCompletedByUserAndSet(USER_ID, milestoneSet.getId()))
                                        .thenReturn(0L);

                        service.evaluateAfterScore(USER_ID, newScore);

                        List<UserMilestoneLink> saved = captureSavedLinks();
                        assertThat(saved.get(0).getCompletedAt()).isEqualTo(scoreTime);
                }
        }

        @Nested
        class EvaluateSingleMilestoneForUser {

                @Test
                void evaluatesExactlyOneMilestone() {
                        Milestone milestone = buildMilestone((double) (100), "GTE");
                        User user = User.builder().id(USER_ID).totalXp(0.0).build();

                        when(queryBuilderService.evaluateProgress(querySpec, USER_ID, null))
                                        .thenReturn(new Progress((double) (120), null));
                        when(userMilestoneLinkRepository.findByUser_IdAndMilestone_Id(USER_ID, milestone.getId()))
                                        .thenReturn(Optional.empty());
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.save(any())).thenAnswer(i -> i.getArgument(0));
                        when(userMilestoneSetBonusRepository.existsByUser_IdAndMilestoneSet_Id(USER_ID,
                                        milestoneSet.getId())).thenReturn(false);
                        when(milestoneRepository.countActiveBySetId(milestoneSet.getId())).thenReturn(1L);
                        when(userMilestoneLinkRepository.countCompletedByUserAndSet(USER_ID, milestoneSet.getId()))
                                        .thenReturn(0L);
                        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

                        service.evaluateSingleMilestoneForUser(USER_ID, milestone);

                        verify(queryBuilderService).evaluateProgress(querySpec, USER_ID, null);
                        verify(milestoneRepository, never()).findActiveUncompletedForUser(any());
                }

                @Test
                void completedWhenTargetMet() {
                        Milestone milestone = buildMilestone((double) (5), "GTE");
                        User user = User.builder().id(USER_ID).totalXp(0.0).build();

                        when(queryBuilderService.evaluateProgress(querySpec, USER_ID, null))
                                        .thenReturn(new Progress((double) (10), null));
                        when(userMilestoneLinkRepository.findByUser_IdAndMilestone_Id(USER_ID, milestone.getId()))
                                        .thenReturn(Optional.empty());
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.save(any())).thenAnswer(i -> i.getArgument(0));
                        when(userMilestoneSetBonusRepository.existsByUser_IdAndMilestoneSet_Id(USER_ID,
                                        milestoneSet.getId())).thenReturn(false);
                        when(milestoneRepository.countActiveBySetId(milestoneSet.getId())).thenReturn(1L);
                        when(userMilestoneLinkRepository.countCompletedByUserAndSet(USER_ID, milestoneSet.getId()))
                                        .thenReturn(0L);
                        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

                        service.evaluateSingleMilestoneForUser(USER_ID, milestone);

                        ArgumentCaptor<UserMilestoneLink> captor = ArgumentCaptor.forClass(UserMilestoneLink.class);
                        verify(userMilestoneLinkRepository).save(captor.capture());
                        assertThat(captor.getValue().isCompleted()).isTrue();
                }

                @Test
                void notCompletedWhenTargetNotMet() {
                        Milestone milestone = buildMilestone((double) (500), "GTE");
                        User user = User.builder().id(USER_ID).build();

                        when(queryBuilderService.evaluateProgress(querySpec, USER_ID, null))
                                        .thenReturn(new Progress((double) (200), null));
                        when(userMilestoneLinkRepository.findByUser_IdAndMilestone_Id(USER_ID, milestone.getId()))
                                        .thenReturn(Optional.empty());
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.save(any())).thenAnswer(i -> i.getArgument(0));

                        service.evaluateSingleMilestoneForUser(USER_ID, milestone);

                        ArgumentCaptor<UserMilestoneLink> captor = ArgumentCaptor.forClass(UserMilestoneLink.class);
                        verify(userMilestoneLinkRepository).save(captor.capture());
                        assertThat(captor.getValue().isCompleted()).isFalse();
                }

                @Test
                void categoryIdPassedCorrectly() {
                        UUID catId = UUID.randomUUID();
                        Category category = Category.builder().id(catId).build();
                        Milestone milestone = buildMilestone((double) (100), "GTE", category);
                        User user = User.builder().id(USER_ID).build();

                        when(queryBuilderService.evaluateProgress(querySpec, USER_ID, catId))
                                        .thenReturn(new Progress((double) (50), null));
                        when(userMilestoneLinkRepository.findByUser_IdAndMilestone_Id(USER_ID, milestone.getId()))
                                        .thenReturn(Optional.empty());
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.save(any())).thenAnswer(i -> i.getArgument(0));

                        service.evaluateSingleMilestoneForUser(USER_ID, milestone);

                        verify(queryBuilderService).evaluateProgress(querySpec, USER_ID, catId);
                }

                @Test
                void alreadyCompletedLink_skippedEntirely() {
                        Milestone milestone = buildMilestone((double) (100), "GTE");
                        UserMilestoneLink existingLink = UserMilestoneLink.builder()
                                        .milestone(milestone)
                                        .completed(true)
                                        .build();

                        when(userMilestoneLinkRepository.findByUser_IdAndMilestone_Id(USER_ID, milestone.getId()))
                                        .thenReturn(Optional.of(existingLink));

                        service.evaluateSingleMilestoneForUser(USER_ID, milestone);

                        verify(queryBuilderService, never()).evaluateProgress(any(), any(), any());
                        verify(userMilestoneLinkRepository, never()).save(any());
                        verify(userRepository, never()).findById(any());
                }

                @Test
                void xpAwardedWhenCompleted() {
                        Milestone milestone = buildMilestone((double) (100), "GTE");
                        User user = User.builder().id(USER_ID).totalXp((double) (500)).build();

                        when(userMilestoneLinkRepository.findByUser_IdAndMilestone_Id(USER_ID, milestone.getId()))
                                        .thenReturn(Optional.empty());
                        when(queryBuilderService.evaluateProgress(querySpec, USER_ID, null))
                                        .thenReturn(new Progress((double) (150), null));
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.save(any())).thenAnswer(i -> i.getArgument(0));
                        when(userMilestoneSetBonusRepository.existsByUser_IdAndMilestoneSet_Id(USER_ID,
                                        milestoneSet.getId())).thenReturn(false);
                        when(milestoneRepository.countActiveBySetId(milestoneSet.getId())).thenReturn(1L);
                        when(userMilestoneLinkRepository.countCompletedByUserAndSet(USER_ID, milestoneSet.getId()))
                                        .thenReturn(0L);
                        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

                        service.evaluateSingleMilestoneForUser(USER_ID, milestone);

                        verify(levelUpAwardService).addXp(USER_ID, (double) (300));
                        verify(userRepository, never()).save(any(User.class));
                }

                @Test
                void xpNotAwardedWhenNotCompleted() {
                        Milestone milestone = buildMilestone((double) (500), "GTE");
                        User user = User.builder().id(USER_ID).build();

                        when(userMilestoneLinkRepository.findByUser_IdAndMilestone_Id(USER_ID, milestone.getId()))
                                        .thenReturn(Optional.empty());
                        when(queryBuilderService.evaluateProgress(querySpec, USER_ID, null))
                                        .thenReturn(new Progress((double) (100), null));
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.save(any())).thenAnswer(i -> i.getArgument(0));

                        service.evaluateSingleMilestoneForUser(USER_ID, milestone);

                        verify(userRepository, never()).findById(any());
                }

                @Test
                void xpIncludesSetBonusWhenSetComplete() {
                        MilestoneSet bonusSet = MilestoneSet.builder()
                                        .id(UUID.randomUUID())
                                        .title("Bonus Set")
                                        .setBonusXp((double) (200))
                                        .build();
                        Milestone milestone = Milestone.builder()
                                        .id(UUID.randomUUID())
                                        .milestoneSet(bonusSet)
                                        .xp((double) (100))
                                        .querySpec(querySpec)
                                        .targetValue((double) (50))
                                        .comparison("GTE")
                                        .active(true)
                                        .build();
                        User user = User.builder().id(USER_ID).totalXp(0.0).build();

                        when(userMilestoneLinkRepository.findByUser_IdAndMilestone_Id(USER_ID, milestone.getId()))
                                        .thenReturn(Optional.empty());
                        when(queryBuilderService.evaluateProgress(querySpec, USER_ID, null))
                                        .thenReturn(new Progress((double) (100), null));
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.save(any())).thenAnswer(i -> i.getArgument(0));
                        when(userMilestoneSetBonusRepository.existsByUser_IdAndMilestoneSet_Id(USER_ID,
                                        bonusSet.getId())).thenReturn(false);
                        when(milestoneRepository.countActiveBySetId(bonusSet.getId())).thenReturn(1L);
                        when(userMilestoneLinkRepository.countCompletedByUserAndSet(USER_ID, bonusSet.getId()))
                                        .thenReturn(1L);
                        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

                        service.evaluateSingleMilestoneForUser(USER_ID, milestone);

                        verify(levelUpAwardService).addXp(USER_ID, (double) (300));
                }

                @Test
                void backfill_usesQualifyingScoreTimeSet() {
                        Milestone milestone = buildMilestone((double) (100), "GTE");
                        Instant scoreTime = Instant.parse("2025-03-10T08:30:00Z");
                        Score qualifying = Score.builder()
                                        .id(UUID.randomUUID())
                                        .mapDifficulty(scoreMapDifficulty)
                                        .timeSet(scoreTime)
                                        .build();
                        User user = User.builder().id(USER_ID).totalXp(0.0).build();

                        when(queryBuilderService.evaluateProgress(querySpec, USER_ID, null))
                                        .thenReturn(new Progress((double) (200), null));
                        when(queryBuilderService.findQualifyingScore(querySpec, USER_ID, null,
                                        (double) (100), "GTE")).thenReturn(qualifying);
                        when(userMilestoneLinkRepository.findByUser_IdAndMilestone_Id(USER_ID, milestone.getId()))
                                        .thenReturn(Optional.empty());
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.save(any())).thenAnswer(i -> i.getArgument(0));
                        when(userMilestoneSetBonusRepository.existsByUser_IdAndMilestoneSet_Id(USER_ID,
                                        milestoneSet.getId())).thenReturn(false);
                        when(milestoneRepository.countActiveBySetId(milestoneSet.getId())).thenReturn(1L);
                        when(userMilestoneLinkRepository.countCompletedByUserAndSet(USER_ID, milestoneSet.getId()))
                                        .thenReturn(0L);
                        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

                        service.evaluateSingleMilestoneForUser(USER_ID, milestone);

                        var captor = org.mockito.ArgumentCaptor.forClass(UserMilestoneLink.class);
                        verify(userMilestoneLinkRepository).save(captor.capture());
                        assertThat(captor.getValue().getCompletedAt()).isEqualTo(scoreTime);
                        assertThat(captor.getValue().getAchievedWithScore()).isEqualTo(qualifying);
                }

                @Test
                void backfill_noQualifyingScore_fallsBackToNow() {
                        MilestoneQuerySpec statsSpec = new MilestoneQuerySpec(
                                        new MilestoneQuerySpec.SelectSpec("MIN", "ranking"),
                                        "user_category_statistics", List.of());
                        Milestone milestone = buildMilestone((double) (10), "LTE");
                        milestone.setQuerySpec(statsSpec);
                        User user = User.builder().id(USER_ID).totalXp(0.0).build();

                        when(queryBuilderService.evaluateProgress(statsSpec, USER_ID, null))
                                        .thenReturn(new Progress(1.0, null));
                        when(queryBuilderService.findQualifyingScore(statsSpec, USER_ID, null,
                                        (double) (10), "LTE")).thenReturn(null);
                        when(userMilestoneLinkRepository.findByUser_IdAndMilestone_Id(USER_ID, milestone.getId()))
                                        .thenReturn(Optional.empty());
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.save(any())).thenAnswer(i -> i.getArgument(0));
                        when(userMilestoneSetBonusRepository.existsByUser_IdAndMilestoneSet_Id(USER_ID,
                                        milestoneSet.getId())).thenReturn(false);
                        when(milestoneRepository.countActiveBySetId(milestoneSet.getId())).thenReturn(1L);
                        when(userMilestoneLinkRepository.countCompletedByUserAndSet(USER_ID, milestoneSet.getId()))
                                        .thenReturn(0L);
                        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

                        Instant before = Instant.now();
                        service.evaluateSingleMilestoneForUser(USER_ID, milestone);

                        var captor = org.mockito.ArgumentCaptor.forClass(UserMilestoneLink.class);
                        verify(userMilestoneLinkRepository).save(captor.capture());
                        assertThat(captor.getValue().getCompletedAt()).isAfterOrEqualTo(before);
                        assertThat(captor.getValue().getAchievedWithScore()).isNull();
                }
        }

        @Nested
        class SetBonusClaiming {

                @Test
                void setBonusAwarded_whenAllMilestonesInSetCompleted() {
                        Milestone milestone = buildMilestone((double) (100), "GTE");
                        Score newScore = buildScoreWithMapDifficulty();
                        User user = User.builder().id(USER_ID).build();

                        mockScopedQuery(List.of(milestone));
                        mockBatchEval(List.of(milestone), (double) (150));
                        mockNoExistingLinks();
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
                        when(userMilestoneSetBonusRepository.existsByUser_IdAndMilestoneSet_Id(USER_ID,
                                        milestoneSet.getId())).thenReturn(false);
                        when(milestoneRepository.countActiveBySetId(milestoneSet.getId())).thenReturn(1L);
                        when(userMilestoneLinkRepository.countCompletedByUserAndSet(USER_ID, milestoneSet.getId()))
                                        .thenReturn(1L);

                        var result = service.evaluateAfterScore(USER_ID, newScore);

                        assertThat(result.completedSets()).containsExactly(milestoneSet);
                        verify(userMilestoneSetBonusRepository).save(any(UserMilestoneSetBonus.class));
                }

                @Test
                void setBonusNotAwarded_whenAlreadyClaimed() {
                        Milestone milestone = buildMilestone((double) (100), "GTE");
                        Score newScore = buildScoreWithMapDifficulty();
                        User user = User.builder().id(USER_ID).build();

                        mockScopedQuery(List.of(milestone));
                        mockBatchEval(List.of(milestone), (double) (150));
                        mockNoExistingLinks();
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
                        when(userMilestoneSetBonusRepository.existsByUser_IdAndMilestoneSet_Id(USER_ID,
                                        milestoneSet.getId())).thenReturn(true);

                        var result = service.evaluateAfterScore(USER_ID, newScore);

                        assertThat(result.completedSets()).isEmpty();
                        verify(userMilestoneSetBonusRepository, never()).save(any());
                }

                @Test
                void setBonusNotAwarded_whenSetIncomplete() {
                        Milestone milestone = buildMilestone((double) (100), "GTE");
                        Score newScore = buildScoreWithMapDifficulty();
                        User user = User.builder().id(USER_ID).build();

                        mockScopedQuery(List.of(milestone));
                        mockBatchEval(List.of(milestone), (double) (150));
                        mockNoExistingLinks();
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
                        when(userMilestoneSetBonusRepository.existsByUser_IdAndMilestoneSet_Id(USER_ID,
                                        milestoneSet.getId())).thenReturn(false);
                        when(milestoneRepository.countActiveBySetId(milestoneSet.getId())).thenReturn(3L);
                        when(userMilestoneLinkRepository.countCompletedByUserAndSet(USER_ID, milestoneSet.getId()))
                                        .thenReturn(1L);

                        var result = service.evaluateAfterScore(USER_ID, newScore);

                        assertThat(result.completedSets()).isEmpty();
                        verify(userMilestoneSetBonusRepository, never()).save(any());
                }

                @Test
                void sameSetCompletedByMultipleMilestones_bonusClaimedOnce() {
                        Milestone m1 = buildMilestone((double) (100), "GTE");
                        Milestone m2 = buildMilestone((double) (200), "GTE");
                        Score newScore = buildScoreWithMapDifficulty();
                        User user = User.builder().id(USER_ID).build();

                        mockScopedQuery(List.of(m1, m2));
                        mockBatchEval(List.of(m1, m2), (double) (300));
                        mockNoExistingLinks();
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
                        when(userMilestoneSetBonusRepository.existsByUser_IdAndMilestoneSet_Id(USER_ID,
                                        milestoneSet.getId())).thenReturn(false);
                        when(milestoneRepository.countActiveBySetId(milestoneSet.getId())).thenReturn(2L);
                        when(userMilestoneLinkRepository.countCompletedByUserAndSet(USER_ID, milestoneSet.getId()))
                                        .thenReturn(2L);

                        var result = service.evaluateAfterScore(USER_ID, newScore);

                        assertThat(result.completedSets()).containsExactly(milestoneSet);
                        verify(userMilestoneSetBonusRepository).existsByUser_IdAndMilestoneSet_Id(USER_ID,
                                        milestoneSet.getId());
                        verify(userMilestoneSetBonusRepository).save(any(UserMilestoneSetBonus.class));
                }

                @Test
                void itemAwarded_whenSetCompletedAndHasItem() {
                        UUID itemId = UUID.randomUUID();
                        ItemType badgeType = ItemType.builder().id(UUID.randomUUID()).key("badge").name("Badge")
                                        .build();
                        Item item = Item.builder().id(itemId).type(badgeType).name("Set Complete Badge").build();
                        MilestoneSet setWithItem = MilestoneSet.builder()
                                        .id(UUID.randomUUID())
                                        .title("Item Set")
                                        .build();
                        Milestone milestone = Milestone.builder()
                                        .id(UUID.randomUUID())
                                        .milestoneSet(setWithItem)
                                        .title("Test")
                                        .type("milestone")
                                        .tier(MilestoneTier.gold)
                                        .xp((double) (100))
                                        .querySpec(querySpec)
                                        .targetValue((double) (50))
                                        .comparison("GTE")
                                        .active(true)
                                        .build();
                        Score newScore = buildScoreWithMapDifficulty();
                        User user = User.builder().id(USER_ID).build();

                        mockScopedQuery(List.of(milestone));
                        mockBatchEval(List.of(milestone), (double) (100));
                        mockNoExistingLinks();
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
                        when(userMilestoneSetBonusRepository.existsByUser_IdAndMilestoneSet_Id(USER_ID,
                                        setWithItem.getId())).thenReturn(false);
                        when(milestoneRepository.countActiveBySetId(setWithItem.getId())).thenReturn(1L);
                        when(userMilestoneLinkRepository.countCompletedByUserAndSet(USER_ID, setWithItem.getId()))
                                        .thenReturn(1L);
                        when(milestoneSetItemRepository.findBySetIds(List.of(setWithItem.getId())))
                                        .thenReturn(List.of(MilestoneSetItem.builder()
                                                        .milestoneSet(setWithItem).item(item).quantity(3).build()));

                        service.evaluateAfterScore(USER_ID, newScore);

                        verify(itemService).awardSystem(eq(USER_ID), eq(itemId), eq(ItemSource.milestone_set),
                                        eq(setWithItem.getId().toString()), eq("Completed milestone set: Item Set"),
                                        eq(3));
                }

                @Test
                void itemNotAwarded_whenSetCompletedButNoItem() {
                        Milestone milestone = buildMilestone((double) (50), "GTE");
                        Score newScore = buildScoreWithMapDifficulty();
                        User user = User.builder().id(USER_ID).build();

                        mockScopedQuery(List.of(milestone));
                        mockBatchEval(List.of(milestone), (double) (100));
                        mockNoExistingLinks();
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
                        when(userMilestoneSetBonusRepository.existsByUser_IdAndMilestoneSet_Id(USER_ID,
                                        milestoneSet.getId())).thenReturn(false);
                        when(milestoneRepository.countActiveBySetId(milestoneSet.getId())).thenReturn(1L);
                        when(userMilestoneLinkRepository.countCompletedByUserAndSet(USER_ID, milestoneSet.getId()))
                                        .thenReturn(1L);

                        service.evaluateAfterScore(USER_ID, newScore);

                        verify(itemService, never()).awardSystem(any(), any(), eq(ItemSource.milestone_set), any(),
                                        any(), anyInt());
                }

                @Test
                void itemNotAwarded_whenSetIncomplete() {
                        MilestoneSet setWithItem = MilestoneSet.builder()
                                        .id(UUID.randomUUID())
                                        .title("Incomplete Set")
                                        .build();
                        Milestone milestone = Milestone.builder()
                                        .id(UUID.randomUUID())
                                        .milestoneSet(setWithItem)
                                        .title("Hard")
                                        .type("milestone")
                                        .tier(MilestoneTier.gold)
                                        .xp((double) (100))
                                        .querySpec(querySpec)
                                        .targetValue((double) (50))
                                        .comparison("GTE")
                                        .active(true)
                                        .build();
                        Score newScore = buildScoreWithMapDifficulty();
                        User user = User.builder().id(USER_ID).build();

                        mockScopedQuery(List.of(milestone));
                        mockBatchEval(List.of(milestone), (double) (100));
                        mockNoExistingLinks();
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
                        when(userMilestoneSetBonusRepository.existsByUser_IdAndMilestoneSet_Id(USER_ID,
                                        setWithItem.getId())).thenReturn(false);
                        when(milestoneRepository.countActiveBySetId(setWithItem.getId())).thenReturn(3L);
                        when(userMilestoneLinkRepository.countCompletedByUserAndSet(USER_ID, setWithItem.getId()))
                                        .thenReturn(1L);

                        service.evaluateAfterScore(USER_ID, newScore);

                        verify(itemService, never()).awardSystem(any(), any(), eq(ItemSource.milestone_set), any(),
                                        any(), anyInt());
                }
        }

        @Nested
        class EvaluateAllForUser {

                @Test
                void evaluatesAllUncompletedMilestones() {
                        Milestone m1 = buildMilestone((double) (900), "GTE");
                        Milestone m2 = buildMilestone((double) (100), "GTE");
                        User user = User.builder().id(USER_ID).build();

                        when(milestoneRepository.findActiveUncompletedForUser(USER_ID))
                                        .thenReturn(List.of(m1, m2));
                        mockBatchEval(List.of(m1, m2), (double) (50));
                        mockNoExistingLinks();
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

                        service.evaluateAllForUser(USER_ID);

                        verify(queryBuilderService).evaluateBatch(any(), eq(USER_ID));
                }

                @Test
                void completedMilestone_isUpdated() {
                        Milestone milestone = buildMilestone((double) (50), "GTE");
                        User user = User.builder().id(USER_ID).build();

                        when(milestoneRepository.findActiveUncompletedForUser(USER_ID))
                                        .thenReturn(List.of(milestone));
                        mockBatchEval(List.of(milestone), (double) (75));
                        mockNoExistingLinks();
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
                        when(userMilestoneSetBonusRepository.existsByUser_IdAndMilestoneSet_Id(USER_ID,
                                        milestoneSet.getId())).thenReturn(false);
                        when(milestoneRepository.countActiveBySetId(milestoneSet.getId())).thenReturn(1L);
                        when(userMilestoneLinkRepository.countCompletedByUserAndSet(USER_ID, milestoneSet.getId()))
                                        .thenReturn(0L);

                        service.evaluateAllForUser(USER_ID);

                        List<UserMilestoneLink> saved = captureSavedLinks();
                        assertThat(saved.get(0).isCompleted()).isTrue();
                        assertThat(saved.get(0).getAchievedWithScore()).isNull();
                }
        }

        @Nested
        class SettleSetRewards {

                private final Instant earnedAt = Instant.parse("2025-01-05T10:15:30Z");

                private Item badge(UUID itemId) {
                        return Item.builder()
                                        .id(itemId)
                                        .type(ItemType.builder().id(UUID.randomUUID()).key("badge").name("Badge")
                                                        .build())
                                        .name("Set Complete Badge")
                                        .build();
                }

                @Test
                void grantsItemMissingFromAnAlreadyClaimedBonus() {
                        UUID itemId = UUID.randomUUID();
                        when(milestoneSetRepository.findById(milestoneSet.getId()))
                                        .thenReturn(Optional.of(milestoneSet));
                        when(userMilestoneSetBonusRepository.existsByUser_IdAndMilestoneSet_Id(USER_ID,
                                        milestoneSet.getId())).thenReturn(true);
                        when(milestoneSetItemRepository.findBySetIds(List.of(milestoneSet.getId())))
                                        .thenReturn(List.of(MilestoneSetItem.builder()
                                                        .milestoneSet(milestoneSet).item(badge(itemId)).quantity(1)
                                                        .build()));
                        when(userItemLinkRepository.countByUser_IdAndItem_IdAndSourceAndSourceId(USER_ID, itemId,
                                        ItemSource.milestone_set, milestoneSet.getId().toString())).thenReturn(0L);

                        int granted = service.settleSetRewards(USER_ID, milestoneSet.getId(), earnedAt);

                        assertThat(granted).isEqualTo(1);
                        verify(itemService).awardSystem(eq(USER_ID), eq(itemId), eq(ItemSource.milestone_set),
                                        eq(milestoneSet.getId().toString()),
                                        eq("Completed milestone set: Test Set"), eq(1));
                        verify(userMilestoneSetBonusRepository, never()).save(any(UserMilestoneSetBonus.class));
                        verify(levelUpAwardService, never()).addXp(any(), any(Double.class));
                }

                @Test
                void countsARandomActiveCrateGrantByTypeSoTheSweepDoesNotRefillIt() {
                        UUID sentinelId = UUID.randomUUID();
                        ItemType crateType = ItemType.builder().id(UUID.randomUUID()).key("crate").name("Crate")
                                        .build();
                        Item sentinel = Item.builder()
                                        .id(sentinelId)
                                        .type(crateType)
                                        .name("Random Active Crate")
                                        .value(new ObjectMapper().createObjectNode().put("grant", "active_crate"))
                                        .build();
                        when(milestoneSetRepository.findById(milestoneSet.getId()))
                                        .thenReturn(Optional.of(milestoneSet));
                        when(userMilestoneSetBonusRepository.existsByUser_IdAndMilestoneSet_Id(USER_ID,
                                        milestoneSet.getId())).thenReturn(true);
                        when(milestoneSetItemRepository.findBySetIds(List.of(milestoneSet.getId())))
                                        .thenReturn(List.of(MilestoneSetItem.builder()
                                                        .milestoneSet(milestoneSet).item(sentinel).quantity(1)
                                                        .build()));
                        when(userItemLinkRepository.countByUser_IdAndItem_Type_KeyAndSourceAndSourceId(USER_ID,
                                        "crate", ItemSource.milestone_set, milestoneSet.getId().toString()))
                                                        .thenReturn(1L);

                        assertThat(service.settleSetRewards(USER_ID, milestoneSet.getId(), earnedAt)).isZero();

                        verify(itemService, never()).awardSystem(any(), any(), any(), any(), any(), anyInt());
                }

                @Test
                void grantsARandomActiveCrateWhenTheSetNeverPaidOneOut() {
                        UUID sentinelId = UUID.randomUUID();
                        ItemType crateType = ItemType.builder().id(UUID.randomUUID()).key("crate").name("Crate")
                                        .build();
                        Item sentinel = Item.builder()
                                        .id(sentinelId)
                                        .type(crateType)
                                        .name("Random Active Crate")
                                        .value(new ObjectMapper().createObjectNode().put("grant", "active_crate"))
                                        .build();
                        when(milestoneSetRepository.findById(milestoneSet.getId()))
                                        .thenReturn(Optional.of(milestoneSet));
                        when(userMilestoneSetBonusRepository.existsByUser_IdAndMilestoneSet_Id(USER_ID,
                                        milestoneSet.getId())).thenReturn(true);
                        when(milestoneSetItemRepository.findBySetIds(List.of(milestoneSet.getId())))
                                        .thenReturn(List.of(MilestoneSetItem.builder()
                                                        .milestoneSet(milestoneSet).item(sentinel).quantity(1)
                                                        .build()));
                        when(userItemLinkRepository.countByUser_IdAndItem_Type_KeyAndSourceAndSourceId(USER_ID,
                                        "crate", ItemSource.milestone_set, milestoneSet.getId().toString()))
                                                        .thenReturn(0L);

                        assertThat(service.settleSetRewards(USER_ID, milestoneSet.getId(), earnedAt)).isEqualTo(1);

                        verify(itemService).awardSystem(eq(USER_ID), eq(sentinelId), eq(ItemSource.milestone_set),
                                        eq(milestoneSet.getId().toString()),
                                        eq("Completed milestone set: Test Set"), eq(1));
                }

                @Test
                void skipsItemThePlayerAlreadyHasFromThatSet() {
                        UUID itemId = UUID.randomUUID();
                        when(milestoneSetRepository.findById(milestoneSet.getId()))
                                        .thenReturn(Optional.of(milestoneSet));
                        when(userMilestoneSetBonusRepository.existsByUser_IdAndMilestoneSet_Id(USER_ID,
                                        milestoneSet.getId())).thenReturn(true);
                        when(milestoneSetItemRepository.findBySetIds(List.of(milestoneSet.getId())))
                                        .thenReturn(List.of(MilestoneSetItem.builder()
                                                        .milestoneSet(milestoneSet).item(badge(itemId)).quantity(1)
                                                        .build()));
                        when(userItemLinkRepository.countByUser_IdAndItem_IdAndSourceAndSourceId(USER_ID, itemId,
                                        ItemSource.milestone_set, milestoneSet.getId().toString())).thenReturn(1L);

                        int granted = service.settleSetRewards(USER_ID, milestoneSet.getId(), earnedAt);

                        assertThat(granted).isZero();
                        verify(itemService, never()).awardSystem(any(), any(), any(), any(), any(), anyInt());
                }

                @Test
                void claimsBonusAtCompletionTimeWhenNoBonusRowExists() {
                        milestoneSet.setSetBonusXp(250.0);
                        when(milestoneSetRepository.findById(milestoneSet.getId()))
                                        .thenReturn(Optional.of(milestoneSet));
                        when(userMilestoneSetBonusRepository.existsByUser_IdAndMilestoneSet_Id(USER_ID,
                                        milestoneSet.getId())).thenReturn(false);
                        when(milestoneRepository.countActiveBySetId(milestoneSet.getId())).thenReturn(3L);
                        when(userMilestoneLinkRepository.countCompletedByUserAndSet(USER_ID, milestoneSet.getId()))
                                        .thenReturn(3L);
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(User.builder().id(USER_ID).build());
                        when(milestoneSetItemRepository.findBySetIds(List.of(milestoneSet.getId())))
                                        .thenReturn(List.of());

                        service.settleSetRewards(USER_ID, milestoneSet.getId(), earnedAt);

                        ArgumentCaptor<UserMilestoneSetBonus> captor = ArgumentCaptor
                                        .forClass(UserMilestoneSetBonus.class);
                        verify(userMilestoneSetBonusRepository).save(captor.capture());
                        assertThat(captor.getValue().getClaimedAt()).isEqualTo(earnedAt);
                        verify(levelUpAwardService).addXp(USER_ID, 250.0);
                }

                @Test
                void doesNothingWhenTheSetIsStillIncomplete() {
                        when(milestoneSetRepository.findById(milestoneSet.getId()))
                                        .thenReturn(Optional.of(milestoneSet));
                        when(userMilestoneSetBonusRepository.existsByUser_IdAndMilestoneSet_Id(USER_ID,
                                        milestoneSet.getId())).thenReturn(false);
                        when(milestoneRepository.countActiveBySetId(milestoneSet.getId())).thenReturn(3L);
                        when(userMilestoneLinkRepository.countCompletedByUserAndSet(USER_ID, milestoneSet.getId()))
                                        .thenReturn(2L);
                        when(milestoneSetItemRepository.findBySetIds(List.of(milestoneSet.getId())))
                                        .thenReturn(List.of());

                        service.settleSetRewards(USER_ID, milestoneSet.getId(), earnedAt);

                        verify(userMilestoneSetBonusRepository, never()).save(any(UserMilestoneSetBonus.class));
                        verify(levelUpAwardService, never()).addXp(any(), any(Double.class));
                }
        }
}
