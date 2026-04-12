package com.accsaber.backend.service.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
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

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.APResult;
import com.accsaber.backend.model.dto.request.score.SubmitScoreRequest;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.CurveType;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.event.ScoreSubmittedEvent;
import com.accsaber.backend.repository.ModifierRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreModifierLinkRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.map.MapDifficultyComplexityService;
import com.accsaber.backend.service.map.MapDifficultyStatisticsService;
import com.accsaber.backend.service.milestone.MilestoneEvaluationService;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.stats.RankingService;
import com.accsaber.backend.service.stats.StatisticsService;

@ExtendWith(MockitoExtension.class)
class ScoreServiceTest {

        @Mock
        private ScoreRepository scoreRepository;
        @Mock
        private ScoreModifierLinkRepository modifierLinkRepository;
        @Mock
        private MapDifficultyRepository mapDifficultyRepository;
        @Mock
        private ModifierRepository modifierRepository;
        @Mock
        private UserRepository userRepository;
        @Mock
        private MapDifficultyComplexityService mapComplexityService;
        @Mock
        private APCalculationService apCalculationService;
        @Mock
        private StatisticsService statisticsService;
        @Mock
        private RankingService rankingService;
        @Mock
        private XPCalculationService xpCalculationService;
        @Mock
        private MilestoneEvaluationService milestoneEvaluationService;
        @Mock
        private MapDifficultyStatisticsService mapDifficultyStatisticsService;
        @Mock
        private ScoreRankingService scoreRankingService;
        @Mock
        private DuplicateUserService duplicateUserService;
        @Mock
        private ApplicationEventPublisher eventPublisher;

        @InjectMocks
        private ScoreService scoreService;

        private MapDifficulty rankedDifficulty;
        private User activeUser;
        private Curve scoreCurve;

        @BeforeEach
        void setUp() {
                lenient().when(duplicateUserService.resolvePrimaryUserId(any(Long.class)))
                                .thenAnswer(inv -> inv.getArgument(0));
                scoreCurve = Curve.builder()
                                .id(UUID.randomUUID())
                                .name("Test Score Curve")
                                .type(CurveType.POINT_LOOKUP)
                                .scale(new BigDecimal("61"))
                                .shift(new BigDecimal("-18"))
                                .build();

                Category category = Category.builder()
                                .id(UUID.randomUUID())
                                .code("true_acc")
                                .name("True Acc")
                                .scoreCurve(scoreCurve)
                                .active(true)
                                .build();

                rankedDifficulty = MapDifficulty.builder()
                                .id(UUID.randomUUID())
                                .map(Map.builder().id(UUID.randomUUID()).songName("Song").songHash("hash").build())
                                .category(category)
                                .difficulty(Difficulty.EXPERT_PLUS)
                                .characteristic("Standard")
                                .status(MapDifficultyStatus.RANKED)
                                .maxScore(1_000_000)
                                .active(true)
                                .build();

                activeUser = User.builder()
                                .id(76561198000000001L)
                                .name("TestPlayer")
                                .active(true)
                                .banned(false)
                                .build();
        }

        private SubmitScoreRequest buildRequest(int score) {
                SubmitScoreRequest req = new SubmitScoreRequest();
                req.setUserId(activeUser.getId());
                req.setMapDifficultyId(rankedDifficulty.getId());
                req.setScore(score);
                req.setScoreNoMods(score);
                req.setRank(1);
                req.setRankWhenSet(1);
                return req;
        }

        private Score buildExistingScore(BigDecimal ap) {
                return Score.builder()
                                .id(UUID.randomUUID())
                                .user(activeUser)
                                .mapDifficulty(rankedDifficulty)
                                .score(900_000)
                                .scoreNoMods(900_000)
                                .rank(1)
                                .rankWhenSet(1)
                                .ap(ap)
                                .weightedAp(ap)
                                .active(true)
                                .build();
        }

        private void stubCommonMocks(BigDecimal rawAp) {
                when(mapDifficultyRepository.findByIdAndActiveTrue(rankedDifficulty.getId()))
                                .thenReturn(Optional.of(rankedDifficulty));
                when(userRepository.findById(activeUser.getId()))
                                .thenReturn(Optional.of(activeUser));
                when(mapComplexityService.findActiveComplexity(rankedDifficulty.getId()))
                                .thenReturn(Optional.of(new BigDecimal("10.0")));
                when(apCalculationService.calculateRawAP(any(), any(), any()))
                                .thenReturn(new APResult(rawAp, new BigDecimal("0.5")));
                lenient().when(xpCalculationService.calculateXpForNewMap(any(), any()))
                                .thenReturn(BigDecimal.TEN);
                lenient().when(xpCalculationService.calculateXpForImprovement(any(), any(), any()))
                                .thenReturn(BigDecimal.TEN);
                lenient().when(xpCalculationService.calculateXpForWorseScore())
                                .thenReturn(BigDecimal.TEN);
                when(modifierLinkRepository.findByScore_Id(any()))
                                .thenReturn(Collections.emptyList());
                lenient().when(milestoneEvaluationService.evaluateAfterScore(any(), any()))
                                .thenReturn(new MilestoneEvaluationService.EvaluationResult(
                                                Collections.emptyList(), Collections.emptyList()));
        }

        @Nested
        class Submit {

                @Test
                void newScore_noExisting_calculatesAPAndSaves() {
                        BigDecimal rawAp = new BigDecimal("500.000000");
                        stubCommonMocks(rawAp);
                        when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(
                                        activeUser.getId(), rankedDifficulty.getId()))
                                        .thenReturn(Optional.empty());
                        Score saved = buildExistingScore(rawAp);
                        when(scoreRepository.saveAndFlush(any())).thenReturn(saved);

                        ScoreResponse response = scoreService.submit(buildRequest(950_000));

                        assertThat(response.getAp()).isEqualByComparingTo(rawAp);
                        verify(statisticsService).recalculate(activeUser.getId(),
                                        rankedDifficulty.getCategory().getId());
                }

                @Test
                void newScore_publishesScoreSubmittedEvent() {
                        BigDecimal rawAp = new BigDecimal("500.000000");
                        stubCommonMocks(rawAp);
                        when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(
                                        activeUser.getId(), rankedDifficulty.getId()))
                                        .thenReturn(Optional.empty());
                        Score saved = buildExistingScore(rawAp);
                        when(scoreRepository.saveAndFlush(any())).thenReturn(saved);

                        scoreService.submit(buildRequest(950_000));

                        verify(eventPublisher).publishEvent(any(ScoreSubmittedEvent.class));
                }

                @Test
                void betterScore_supersedesExisting() {
                        BigDecimal oldAp = new BigDecimal("400.000000");
                        BigDecimal newAp = new BigDecimal("500.000000");
                        stubCommonMocks(newAp);
                        Score existing = buildExistingScore(oldAp);
                        when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(
                                        activeUser.getId(), rankedDifficulty.getId()))
                                        .thenReturn(Optional.of(existing));
                        Score savedNew = buildExistingScore(newAp);
                        when(scoreRepository.saveAndFlush(any())).thenReturn(existing).thenReturn(savedNew);

                        scoreService.submit(buildRequest(960_000));

                        assertThat(existing.isActive()).isFalse();
                        verify(scoreRepository).saveAndFlush(existing);
                        verify(statisticsService).recalculate(activeUser.getId(),
                                        rankedDifficulty.getCategory().getId());
                        verify(rankingService).updateRankingsAsync(eq(rankedDifficulty.getCategory().getId()), any(Runnable.class));
                }

                @Test
                void worseScore_keepsExisting_savesInactiveHistory() {
                        BigDecimal oldAp = new BigDecimal("600.000000");
                        BigDecimal newAp = new BigDecimal("500.000000");
                        stubCommonMocks(newAp);
                        Score existing = buildExistingScore(oldAp);
                        when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(
                                        activeUser.getId(), rankedDifficulty.getId()))
                                        .thenReturn(Optional.of(existing));

                        ScoreResponse response = scoreService.submit(buildRequest(880_000));

                        assertThat(response.getAp()).isEqualByComparingTo(newAp);
                        assertThat(existing.isActive()).isTrue();
                        verify(statisticsService, never()).recalculate(any(), any());
                }

                @Test
                void worseScore_stillPublishesScoreSubmittedEvent() {
                        BigDecimal oldAp = new BigDecimal("600.000000");
                        BigDecimal newAp = new BigDecimal("500.000000");
                        stubCommonMocks(newAp);
                        Score existing = buildExistingScore(oldAp);
                        when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(
                                        activeUser.getId(), rankedDifficulty.getId()))
                                        .thenReturn(Optional.of(existing));

                        scoreService.submit(buildRequest(880_000));

                        verify(eventPublisher).publishEvent(any(ScoreSubmittedEvent.class));
                }

                @Test
                void duplicateScore_throwsValidationException() {
                        Score existing = buildExistingScore(new BigDecimal("600.000000"));
                        when(mapDifficultyRepository.findByIdAndActiveTrue(rankedDifficulty.getId()))
                                        .thenReturn(Optional.of(rankedDifficulty));
                        when(userRepository.findById(activeUser.getId()))
                                        .thenReturn(Optional.of(activeUser));
                        when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(
                                        activeUser.getId(), rankedDifficulty.getId()))
                                        .thenReturn(Optional.of(existing));

                        assertThatThrownBy(() -> scoreService.submit(buildRequest(900_000)))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("Duplicate");
                }

                @Test
                void unrankedDifficulty_throwsValidationException() {
                        rankedDifficulty.setStatus(MapDifficultyStatus.QUEUE);
                        when(mapDifficultyRepository.findByIdAndActiveTrue(rankedDifficulty.getId()))
                                        .thenReturn(Optional.of(rankedDifficulty));

                        assertThatThrownBy(() -> scoreService.submit(buildRequest(950_000)))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("ranked");
                }

                @Test
                void missingComplexity_throwsValidationException() {
                        when(mapDifficultyRepository.findByIdAndActiveTrue(rankedDifficulty.getId()))
                                        .thenReturn(Optional.of(rankedDifficulty));
                        when(userRepository.findById(activeUser.getId()))
                                        .thenReturn(Optional.of(activeUser));
                        when(mapComplexityService.findActiveComplexity(rankedDifficulty.getId()))
                                        .thenReturn(Optional.empty());

                        assertThatThrownBy(() -> scoreService.submit(buildRequest(950_000)))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("complexity");
                }

                @Test
                void unknownDifficulty_throwsNotFound() {
                        UUID unknownId = UUID.randomUUID();
                        when(mapDifficultyRepository.findByIdAndActiveTrue(unknownId))
                                        .thenReturn(Optional.empty());
                        SubmitScoreRequest req = buildRequest(950_000);
                        req.setMapDifficultyId(unknownId);

                        assertThatThrownBy(() -> scoreService.submit(req))
                                        .isInstanceOf(ResourceNotFoundException.class);
                }

                @Test
                void bannedUser_throwsValidationException() {
                        activeUser.setBanned(true);
                        when(mapDifficultyRepository.findByIdAndActiveTrue(rankedDifficulty.getId()))
                                        .thenReturn(Optional.of(rankedDifficulty));
                        when(userRepository.findById(activeUser.getId()))
                                        .thenReturn(Optional.of(activeUser));

                        assertThatThrownBy(() -> scoreService.submit(buildRequest(950_000)))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("Banned");
                }
        }

        @Nested
        class FindHistoric {

                @Test
                void returnsScoresMappedToResponses() {
                        Score s1 = Score.builder()
                                        .id(UUID.randomUUID())
                                        .user(activeUser)
                                        .mapDifficulty(rankedDifficulty)
                                        .score(950_000).scoreNoMods(950_000)
                                        .rank(1).rankWhenSet(1)
                                        .ap(new BigDecimal("500.000000"))
                                        .weightedAp(new BigDecimal("500.000000"))
                                        .active(false)
                                        .createdAt(Instant.now().minusSeconds(3600))
                                        .build();
                        Score s2 = Score.builder()
                                        .id(UUID.randomUUID())
                                        .user(activeUser)
                                        .mapDifficulty(rankedDifficulty)
                                        .score(970_000).scoreNoMods(970_000)
                                        .rank(1).rankWhenSet(1)
                                        .ap(new BigDecimal("600.000000"))
                                        .weightedAp(new BigDecimal("600.000000"))
                                        .active(true)
                                        .createdAt(Instant.now())
                                        .build();

                        when(scoreRepository.findHistoric(
                                        org.mockito.ArgumentMatchers.eq(activeUser.getId()),
                                        org.mockito.ArgumentMatchers.eq(rankedDifficulty.getId()),
                                        any(Instant.class)))
                                        .thenReturn(List.of(s1, s2));
                        when(modifierLinkRepository.findByScore_Id(any()))
                                        .thenReturn(Collections.emptyList());

                        List<ScoreResponse> result = scoreService.findHistoric(
                                        activeUser.getId(), rankedDifficulty.getId(), 7, "d");

                        assertThat(result).hasSize(2);
                        assertThat(result.get(0).getAp()).isEqualByComparingTo(new BigDecimal("500.000000"));
                        assertThat(result.get(1).getAp()).isEqualByComparingTo(new BigDecimal("600.000000"));
                }

                @Test
                void invalidUnit_throws() {
                        assertThatThrownBy(() -> scoreService.findHistoric(
                                        activeUser.getId(), rankedDifficulty.getId(), 7, "x"))
                                        .isInstanceOf(IllegalArgumentException.class);
                }
        }
}
