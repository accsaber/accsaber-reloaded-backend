package com.accsaber.backend.service.milestone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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

import com.accsaber.backend.model.dto.MilestoneQuerySpec;
import com.accsaber.backend.model.dto.MilestoneQuerySpec.SelectSpec;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.milestone.Milestone;
import com.accsaber.backend.model.entity.milestone.MilestoneSet;
import com.accsaber.backend.model.entity.milestone.MilestoneTier;
import com.accsaber.backend.model.entity.milestone.UserMilestoneLink;
import com.accsaber.backend.model.entity.milestone.UserMilestoneSetBonus;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.milestone.MilestoneRepository;
import com.accsaber.backend.repository.milestone.UserMilestoneLinkRepository;
import com.accsaber.backend.repository.milestone.UserMilestoneSetBonusRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.badge.BadgeService;

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
        private BadgeService badgeService;

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

        private Milestone buildMilestone(BigDecimal target, String comparison) {
                return buildMilestone(target, comparison, null);
        }

        private Milestone buildMilestone(BigDecimal target, String comparison, Category category) {
                return Milestone.builder()
                                .id(UUID.randomUUID())
                                .milestoneSet(milestoneSet)
                                .title("Test Milestone")
                                .type("milestone")
                                .tier(MilestoneTier.gold)
                                .xp(BigDecimal.valueOf(300))
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

        private void mockBatchEval(List<Milestone> milestones, BigDecimal value) {
                Map<UUID, BigDecimal> results = new java.util.HashMap<>();
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
                        Milestone milestone = buildMilestone(BigDecimal.valueOf(900), "GTE");
                        Score newScore = buildScoreWithMapDifficulty();
                        User user = User.builder().id(USER_ID).build();

                        mockScopedQuery(List.of(milestone));
                        mockBatchEval(List.of(milestone), BigDecimal.valueOf(950));
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
                        assertThat(saved.get(0).getProgress()).isEqualByComparingTo(BigDecimal.valueOf(950));
                }

                @Test
                void currentValueBelowGteTarget_notCompleted() {
                        Milestone milestone = buildMilestone(BigDecimal.valueOf(900), "GTE");
                        Score newScore = buildScoreWithMapDifficulty();
                        User user = User.builder().id(USER_ID).build();

                        mockScopedQuery(List.of(milestone));
                        mockBatchEval(List.of(milestone), BigDecimal.valueOf(750));
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
                        Milestone milestone = buildMilestone(BigDecimal.valueOf(10), "LTE");
                        Score newScore = buildScoreWithMapDifficulty();
                        User user = User.builder().id(USER_ID).build();

                        mockScopedQuery(List.of(milestone));
                        mockBatchEval(List.of(milestone), BigDecimal.valueOf(5));
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
                        Milestone milestone = buildMilestone(BigDecimal.valueOf(10), "LTE");
                        Score newScore = buildScoreWithMapDifficulty();
                        User user = User.builder().id(USER_ID).build();

                        mockScopedQuery(List.of(milestone));
                        mockBatchEval(List.of(milestone), BigDecimal.valueOf(15));
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
                        Milestone milestone = buildMilestone(BigDecimal.valueOf(500), "GTE", milestoneCategory);
                        Score newScore = buildScoreWithMapDifficulty();
                        User user = User.builder().id(USER_ID).build();

                        mockScopedQuery(List.of(milestone));
                        mockBatchEval(List.of(milestone), BigDecimal.valueOf(300));
                        mockNoExistingLinks();
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

                        service.evaluateAfterScore(USER_ID, newScore);

                        verify(queryBuilderService).evaluateBatch(any(), eq(USER_ID));
                }

                @Test
                void blExclusiveMilestone_skippedForScoreSaberScore() {
                        Milestone milestone = Milestone.builder()
                                        .id(UUID.randomUUID())
                                        .milestoneSet(milestoneSet)
                                        .title("BL Only")
                                        .type("achievement")
                                        .tier(MilestoneTier.gold)
                                        .xp(BigDecimal.valueOf(200))
                                        .querySpec(querySpec)
                                        .targetValue(BigDecimal.valueOf(10))
                                        .comparison("GTE")
                                        .blExclusive(true)
                                        .active(true)
                                        .build();
                        Score ssScore = buildScoreWithMapDifficulty(null);

                        mockScopedQuery(List.of(milestone));

                        var result = service.evaluateAfterScore(USER_ID, ssScore);

                        assertThat(result.completedMilestones()).isEmpty();
                        verify(queryBuilderService, never()).evaluateBatch(any(), any());
                }

                @Test
                void blExclusiveMilestone_evaluatedForBeatLeaderScore() {
                        Milestone milestone = Milestone.builder()
                                        .id(UUID.randomUUID())
                                        .milestoneSet(milestoneSet)
                                        .title("BL Only")
                                        .type("achievement")
                                        .tier(MilestoneTier.gold)
                                        .xp(BigDecimal.valueOf(200))
                                        .querySpec(querySpec)
                                        .targetValue(BigDecimal.valueOf(10))
                                        .comparison("GTE")
                                        .blExclusive(true)
                                        .active(true)
                                        .build();
                        Score blScore = buildScoreWithMapDifficulty(99999L);
                        User user = User.builder().id(USER_ID).build();

                        mockScopedQuery(List.of(milestone));
                        mockBatchEval(List.of(milestone), BigDecimal.valueOf(5));
                        mockNoExistingLinks();
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

                        service.evaluateAfterScore(USER_ID, blScore);

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
                        Milestone milestone = buildMilestone(BigDecimal.valueOf(900), "GTE");
                        Score newScore = buildScoreWithMapDifficulty();
                        UserMilestoneLink existingLink = UserMilestoneLink.builder()
                                        .milestone(milestone)
                                        .progress(BigDecimal.valueOf(850))
                                        .completed(false)
                                        .build();

                        mockScopedQuery(List.of(milestone));
                        mockBatchEval(List.of(milestone), BigDecimal.valueOf(950));
                        mockExistingLinks(existingLink);
                        when(userMilestoneLinkRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
                        when(userMilestoneSetBonusRepository.existsByUser_IdAndMilestoneSet_Id(USER_ID,
                                        milestoneSet.getId())).thenReturn(false);
                        when(milestoneRepository.countActiveBySetId(milestoneSet.getId())).thenReturn(1L);
                        when(userMilestoneLinkRepository.countCompletedByUserAndSet(USER_ID, milestoneSet.getId()))
                                        .thenReturn(0L);

                        service.evaluateAfterScore(USER_ID, newScore);

                        assertThat(existingLink.isCompleted()).isTrue();
                        assertThat(existingLink.getProgress()).isEqualByComparingTo(BigDecimal.valueOf(950));
                }

                @Test
                void scopedQuery_usesMapDifficultyAndCategoryFromScore() {
                        Milestone milestone = buildMilestone(BigDecimal.valueOf(100), "GTE");
                        Score newScore = buildScoreWithMapDifficulty();
                        User user = User.builder().id(USER_ID).build();

                        mockScopedQuery(List.of(milestone));
                        mockBatchEval(List.of(milestone), BigDecimal.valueOf(50));
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
                        Milestone milestone = buildMilestone(BigDecimal.valueOf(100), "GTE");
                        Instant scoreTime = Instant.parse("2025-06-15T12:00:00Z");
                        Score newScore = Score.builder()
                                        .id(UUID.randomUUID())
                                        .mapDifficulty(scoreMapDifficulty)
                                        .timeSet(scoreTime)
                                        .build();
                        User user = User.builder().id(USER_ID).build();

                        mockScopedQuery(List.of(milestone));
                        mockBatchEval(List.of(milestone), BigDecimal.valueOf(200));
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
                        Milestone milestone = buildMilestone(BigDecimal.valueOf(100), "GTE");
                        User user = User.builder().id(USER_ID).totalXp(BigDecimal.ZERO).build();

                        when(queryBuilderService.evaluate(querySpec, USER_ID, null))
                                        .thenReturn(BigDecimal.valueOf(120));
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

                        verify(queryBuilderService).evaluate(querySpec, USER_ID, null);
                        verify(milestoneRepository, never()).findActiveUncompletedForUser(any());
                }

                @Test
                void completedWhenTargetMet() {
                        Milestone milestone = buildMilestone(BigDecimal.valueOf(5), "GTE");
                        User user = User.builder().id(USER_ID).totalXp(BigDecimal.ZERO).build();

                        when(queryBuilderService.evaluate(querySpec, USER_ID, null))
                                        .thenReturn(BigDecimal.valueOf(10));
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
                        Milestone milestone = buildMilestone(BigDecimal.valueOf(500), "GTE");
                        User user = User.builder().id(USER_ID).build();

                        when(queryBuilderService.evaluate(querySpec, USER_ID, null))
                                        .thenReturn(BigDecimal.valueOf(200));
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
                        Milestone milestone = buildMilestone(BigDecimal.valueOf(100), "GTE", category);
                        User user = User.builder().id(USER_ID).build();

                        when(queryBuilderService.evaluate(querySpec, USER_ID, catId))
                                        .thenReturn(BigDecimal.valueOf(50));
                        when(userMilestoneLinkRepository.findByUser_IdAndMilestone_Id(USER_ID, milestone.getId()))
                                        .thenReturn(Optional.empty());
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.save(any())).thenAnswer(i -> i.getArgument(0));

                        service.evaluateSingleMilestoneForUser(USER_ID, milestone);

                        verify(queryBuilderService).evaluate(querySpec, USER_ID, catId);
                }

                @Test
                void alreadyCompletedLink_skippedEntirely() {
                        Milestone milestone = buildMilestone(BigDecimal.valueOf(100), "GTE");
                        UserMilestoneLink existingLink = UserMilestoneLink.builder()
                                        .milestone(milestone)
                                        .completed(true)
                                        .build();

                        when(userMilestoneLinkRepository.findByUser_IdAndMilestone_Id(USER_ID, milestone.getId()))
                                        .thenReturn(Optional.of(existingLink));

                        service.evaluateSingleMilestoneForUser(USER_ID, milestone);

                        verify(queryBuilderService, never()).evaluate(any(), any(), any());
                        verify(userMilestoneLinkRepository, never()).save(any());
                        verify(userRepository, never()).findById(any());
                }

                @Test
                void xpAwardedWhenCompleted() {
                        Milestone milestone = buildMilestone(BigDecimal.valueOf(100), "GTE");
                        User user = User.builder().id(USER_ID).totalXp(BigDecimal.valueOf(500)).build();

                        when(userMilestoneLinkRepository.findByUser_IdAndMilestone_Id(USER_ID, milestone.getId()))
                                        .thenReturn(Optional.empty());
                        when(queryBuilderService.evaluate(querySpec, USER_ID, null))
                                        .thenReturn(BigDecimal.valueOf(150));
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.save(any())).thenAnswer(i -> i.getArgument(0));
                        when(userMilestoneSetBonusRepository.existsByUser_IdAndMilestoneSet_Id(USER_ID,
                                        milestoneSet.getId())).thenReturn(false);
                        when(milestoneRepository.countActiveBySetId(milestoneSet.getId())).thenReturn(1L);
                        when(userMilestoneLinkRepository.countCompletedByUserAndSet(USER_ID, milestoneSet.getId()))
                                        .thenReturn(0L);
                        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

                        service.evaluateSingleMilestoneForUser(USER_ID, milestone);

                        assertThat(user.getTotalXp()).isEqualByComparingTo(BigDecimal.valueOf(800));
                        verify(userRepository).save(user);
                }

                @Test
                void xpNotAwardedWhenNotCompleted() {
                        Milestone milestone = buildMilestone(BigDecimal.valueOf(500), "GTE");
                        User user = User.builder().id(USER_ID).build();

                        when(userMilestoneLinkRepository.findByUser_IdAndMilestone_Id(USER_ID, milestone.getId()))
                                        .thenReturn(Optional.empty());
                        when(queryBuilderService.evaluate(querySpec, USER_ID, null))
                                        .thenReturn(BigDecimal.valueOf(100));
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
                                        .setBonusXp(BigDecimal.valueOf(200))
                                        .build();
                        Milestone milestone = Milestone.builder()
                                        .id(UUID.randomUUID())
                                        .milestoneSet(bonusSet)
                                        .xp(BigDecimal.valueOf(100))
                                        .querySpec(querySpec)
                                        .targetValue(BigDecimal.valueOf(50))
                                        .comparison("GTE")
                                        .active(true)
                                        .build();
                        User user = User.builder().id(USER_ID).totalXp(BigDecimal.ZERO).build();

                        when(userMilestoneLinkRepository.findByUser_IdAndMilestone_Id(USER_ID, milestone.getId()))
                                        .thenReturn(Optional.empty());
                        when(queryBuilderService.evaluate(querySpec, USER_ID, null))
                                        .thenReturn(BigDecimal.valueOf(100));
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.save(any())).thenAnswer(i -> i.getArgument(0));
                        when(userMilestoneSetBonusRepository.existsByUser_IdAndMilestoneSet_Id(USER_ID,
                                        bonusSet.getId())).thenReturn(false);
                        when(milestoneRepository.countActiveBySetId(bonusSet.getId())).thenReturn(1L);
                        when(userMilestoneLinkRepository.countCompletedByUserAndSet(USER_ID, bonusSet.getId()))
                                        .thenReturn(1L);
                        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

                        service.evaluateSingleMilestoneForUser(USER_ID, milestone);

                        assertThat(user.getTotalXp()).isEqualByComparingTo(BigDecimal.valueOf(300));
                }

                @Test
                void backfill_usesQualifyingScoreTimeSet() {
                        Milestone milestone = buildMilestone(BigDecimal.valueOf(100), "GTE");
                        Instant scoreTime = Instant.parse("2025-03-10T08:30:00Z");
                        Score qualifying = Score.builder()
                                        .id(UUID.randomUUID())
                                        .mapDifficulty(scoreMapDifficulty)
                                        .timeSet(scoreTime)
                                        .build();
                        User user = User.builder().id(USER_ID).totalXp(BigDecimal.ZERO).build();

                        when(queryBuilderService.evaluate(querySpec, USER_ID, null))
                                        .thenReturn(BigDecimal.valueOf(200));
                        when(queryBuilderService.findQualifyingScore(querySpec, USER_ID, null,
                                        BigDecimal.valueOf(100), "GTE")).thenReturn(qualifying);
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
                        Milestone milestone = buildMilestone(BigDecimal.valueOf(10), "LTE");
                        milestone.setQuerySpec(statsSpec);
                        User user = User.builder().id(USER_ID).totalXp(BigDecimal.ZERO).build();

                        when(queryBuilderService.evaluate(statsSpec, USER_ID, null))
                                        .thenReturn(BigDecimal.ONE);
                        when(queryBuilderService.findQualifyingScore(statsSpec, USER_ID, null,
                                        BigDecimal.valueOf(10), "LTE")).thenReturn(null);
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
                        Milestone milestone = buildMilestone(BigDecimal.valueOf(100), "GTE");
                        Score newScore = buildScoreWithMapDifficulty();
                        User user = User.builder().id(USER_ID).build();

                        mockScopedQuery(List.of(milestone));
                        mockBatchEval(List.of(milestone), BigDecimal.valueOf(150));
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
                        Milestone milestone = buildMilestone(BigDecimal.valueOf(100), "GTE");
                        Score newScore = buildScoreWithMapDifficulty();
                        User user = User.builder().id(USER_ID).build();

                        mockScopedQuery(List.of(milestone));
                        mockBatchEval(List.of(milestone), BigDecimal.valueOf(150));
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
                        Milestone milestone = buildMilestone(BigDecimal.valueOf(100), "GTE");
                        Score newScore = buildScoreWithMapDifficulty();
                        User user = User.builder().id(USER_ID).build();

                        mockScopedQuery(List.of(milestone));
                        mockBatchEval(List.of(milestone), BigDecimal.valueOf(150));
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
                        Milestone m1 = buildMilestone(BigDecimal.valueOf(100), "GTE");
                        Milestone m2 = buildMilestone(BigDecimal.valueOf(200), "GTE");
                        Score newScore = buildScoreWithMapDifficulty();
                        User user = User.builder().id(USER_ID).build();

                        mockScopedQuery(List.of(m1, m2));
                        mockBatchEval(List.of(m1, m2), BigDecimal.valueOf(300));
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
                void badgeAwarded_whenSetCompletedAndHasBadge() {
                        UUID badgeId = UUID.randomUUID();
                        com.accsaber.backend.model.entity.badge.Badge badge = com.accsaber.backend.model.entity.badge.Badge
                                        .builder().id(badgeId).name("Set Complete Badge").build();
                        MilestoneSet setWithBadge = MilestoneSet.builder()
                                        .id(UUID.randomUUID())
                                        .title("Badge Set")
                                        .awardsBadge(badge)
                                        .build();
                        Milestone milestone = Milestone.builder()
                                        .id(UUID.randomUUID())
                                        .milestoneSet(setWithBadge)
                                        .title("Test")
                                        .type("milestone")
                                        .tier(MilestoneTier.gold)
                                        .xp(BigDecimal.valueOf(100))
                                        .querySpec(querySpec)
                                        .targetValue(BigDecimal.valueOf(50))
                                        .comparison("GTE")
                                        .active(true)
                                        .build();
                        Score newScore = buildScoreWithMapDifficulty();
                        User user = User.builder().id(USER_ID).build();

                        mockScopedQuery(List.of(milestone));
                        mockBatchEval(List.of(milestone), BigDecimal.valueOf(100));
                        mockNoExistingLinks();
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
                        when(userMilestoneSetBonusRepository.existsByUser_IdAndMilestoneSet_Id(USER_ID,
                                        setWithBadge.getId())).thenReturn(false);
                        when(milestoneRepository.countActiveBySetId(setWithBadge.getId())).thenReturn(1L);
                        when(userMilestoneLinkRepository.countCompletedByUserAndSet(USER_ID, setWithBadge.getId()))
                                        .thenReturn(1L);

                        service.evaluateAfterScore(USER_ID, newScore);

                        verify(badgeService).awardBadgeSystem(eq(USER_ID), eq(badgeId),
                                        eq("Completed milestone set: Badge Set"));
                }

                @Test
                void badgeNotAwarded_whenSetCompletedButNoBadge() {
                        Milestone milestone = buildMilestone(BigDecimal.valueOf(50), "GTE");
                        Score newScore = buildScoreWithMapDifficulty();
                        User user = User.builder().id(USER_ID).build();

                        mockScopedQuery(List.of(milestone));
                        mockBatchEval(List.of(milestone), BigDecimal.valueOf(100));
                        mockNoExistingLinks();
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
                        when(userMilestoneSetBonusRepository.existsByUser_IdAndMilestoneSet_Id(USER_ID,
                                        milestoneSet.getId())).thenReturn(false);
                        when(milestoneRepository.countActiveBySetId(milestoneSet.getId())).thenReturn(1L);
                        when(userMilestoneLinkRepository.countCompletedByUserAndSet(USER_ID, milestoneSet.getId()))
                                        .thenReturn(1L);

                        service.evaluateAfterScore(USER_ID, newScore);

                        verify(badgeService, never()).awardBadgeSystem(any(), any(), any());
                }

                @Test
                void badgeNotAwarded_whenSetIncomplete() {
                        UUID badgeId = UUID.randomUUID();
                        com.accsaber.backend.model.entity.badge.Badge badge = com.accsaber.backend.model.entity.badge.Badge
                                        .builder().id(badgeId).name("Unreachable Badge").build();
                        MilestoneSet setWithBadge = MilestoneSet.builder()
                                        .id(UUID.randomUUID())
                                        .title("Incomplete Set")
                                        .awardsBadge(badge)
                                        .build();
                        Milestone milestone = Milestone.builder()
                                        .id(UUID.randomUUID())
                                        .milestoneSet(setWithBadge)
                                        .title("Hard")
                                        .type("milestone")
                                        .tier(MilestoneTier.gold)
                                        .xp(BigDecimal.valueOf(100))
                                        .querySpec(querySpec)
                                        .targetValue(BigDecimal.valueOf(50))
                                        .comparison("GTE")
                                        .active(true)
                                        .build();
                        Score newScore = buildScoreWithMapDifficulty();
                        User user = User.builder().id(USER_ID).build();

                        mockScopedQuery(List.of(milestone));
                        mockBatchEval(List.of(milestone), BigDecimal.valueOf(100));
                        mockNoExistingLinks();
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
                        when(userMilestoneSetBonusRepository.existsByUser_IdAndMilestoneSet_Id(USER_ID,
                                        setWithBadge.getId())).thenReturn(false);
                        when(milestoneRepository.countActiveBySetId(setWithBadge.getId())).thenReturn(3L);
                        when(userMilestoneLinkRepository.countCompletedByUserAndSet(USER_ID, setWithBadge.getId()))
                                        .thenReturn(1L);

                        service.evaluateAfterScore(USER_ID, newScore);

                        verify(badgeService, never()).awardBadgeSystem(any(), any(), any());
                }
        }

        @Nested
        class EvaluateAllForUser {

                @Test
                void evaluatesAllUncompletedMilestones() {
                        Milestone m1 = buildMilestone(BigDecimal.valueOf(900), "GTE");
                        Milestone m2 = buildMilestone(BigDecimal.valueOf(100), "GTE");
                        User user = User.builder().id(USER_ID).build();

                        when(milestoneRepository.findActiveUncompletedForUser(USER_ID))
                                        .thenReturn(List.of(m1, m2));
                        mockBatchEval(List.of(m1, m2), BigDecimal.valueOf(50));
                        mockNoExistingLinks();
                        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
                        when(userMilestoneLinkRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

                        service.evaluateAllForUser(USER_ID);

                        verify(queryBuilderService).evaluateBatch(any(), eq(USER_ID));
                }

                @Test
                void completedMilestone_isUpdated() {
                        Milestone milestone = buildMilestone(BigDecimal.valueOf(50), "GTE");
                        User user = User.builder().id(USER_ID).build();

                        when(milestoneRepository.findActiveUncompletedForUser(USER_ID))
                                        .thenReturn(List.of(milestone));
                        mockBatchEval(List.of(milestone), BigDecimal.valueOf(75));
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
}
