package com.accsaber.backend.service.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.accsaber.backend.model.dto.response.score.ApCurveResponse;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreModifierLinkRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.service.map.MapDifficultyComplexityService;
import com.accsaber.backend.service.player.DuplicateUserService;

@ExtendWith(MockitoExtension.class)
class ScoreQueryServiceTest {

    @Mock
    private ScoreRepository scoreRepository;
    @Mock
    private ScoreModifierLinkRepository modifierLinkRepository;
    @Mock
    private MapDifficultyRepository mapDifficultyRepository;
    @Mock
    private MapDifficultyComplexityService mapComplexityService;
    @Mock
    private XPCalculationService xpCalculationService;
    @Mock
    private DuplicateUserService duplicateUserService;

    @InjectMocks
    private ScoreQueryService scoreQueryService;

    private MapDifficulty rankedDifficulty;
    private User activeUser;

    @BeforeEach
    void setUp() {
        lenient().when(duplicateUserService.resolvePrimaryUserId(any(Long.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        rankedDifficulty = MapDifficulty.builder()
                .id(UUID.randomUUID())
                .map(Map.builder().id(UUID.randomUUID()).songName("Song").songHash("hash").build())
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

    @Nested
    class FindHistoric {

        @Test
        void invalidUnit_throws() {
            assertThatThrownBy(() -> scoreQueryService.findHistoric(
                    activeUser.getId(), rankedDifficulty.getId(), 7, "x"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class AttemptAggregates {

        private static final Instant LAST_ATTEMPT = Instant.parse("2026-08-01T12:00:00Z");

        private Score activeScoreWithStreak(int streak) {
            return Score.builder()
                    .id(UUID.randomUUID())
                    .user(activeUser)
                    .mapDifficulty(rankedDifficulty)
                    .score(950_000).scoreNoMods(950_000)
                    .rank(1).rankWhenSet(1)
                    .ap(500.000000)
                    .weightedAp(500.000000)
                    .streak115(streak)
                    .playCount(2)
                    .active(true)
                    .build();
        }

        @Test
        void hydratesEveryAttemptAggregate_notTheActiveScoreValues() {
            Score active = activeScoreWithStreak(3);
            active.setTimeSet(Instant.parse("2026-01-01T00:00:00Z"));
            when(scoreRepository.findActiveByUsers(eq(List.of(activeUser.getId())), isNull(), isNull(),
                    any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(active)));
            when(scoreRepository.findAttemptAggregatesByUsersAndDifficulties(
                    List.of(activeUser.getId()), List.of(rankedDifficulty.getId())))
                    .thenReturn(List.<Object[]>of(new Object[] {
                            activeUser.getId(), rankedDifficulty.getId(), 5, 9,
                            LAST_ATTEMPT }));

            Page<ScoreResponse> result = scoreQueryService.findByUser(
                    activeUser.getId(), null, null, PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(1);
            ScoreResponse row = result.getContent().get(0);
            assertThat(row.getStreak115()).isEqualTo(3);
            assertThat(row.getMaxStreak115()).isEqualTo(5);
            assertThat(row.getPlayCount()).isEqualTo(9);
            assertThat(row.getTimeSet()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
            assertThat(row.getLastPlayedAt()).isEqualTo(LAST_ATTEMPT);
        }

        @Test
        void fallsBackToTheRowPlayCountWhenNoAttemptCarriesOne() {
            Score active = activeScoreWithStreak(3);
            when(scoreRepository.findActiveByUsers(eq(List.of(activeUser.getId())), isNull(), isNull(),
                    any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(active)));
            when(scoreRepository.findAttemptAggregatesByUsersAndDifficulties(
                    List.of(activeUser.getId()), List.of(rankedDifficulty.getId())))
                    .thenReturn(List.<Object[]>of(new Object[] {
                            activeUser.getId(), rankedDifficulty.getId(), 5, null, null }));

            Page<ScoreResponse> result = scoreQueryService.findByUser(
                    activeUser.getId(), null, null, PageRequest.of(0, 20));

            assertThat(result.getContent().get(0).getPlayCount()).isEqualTo(2);
        }

        @Test
        void leavesMaxStreakNullWhenNoAttemptCarriesOne() {
            Score active = activeScoreWithStreak(3);
            when(scoreRepository.findActiveByUsers(eq(List.of(activeUser.getId())), isNull(), isNull(),
                    any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(active)));
            when(scoreRepository.findAttemptAggregatesByUsersAndDifficulties(
                    List.of(activeUser.getId()), List.of(rankedDifficulty.getId())))
                    .thenReturn(Collections.emptyList());

            Page<ScoreResponse> result = scoreQueryService.findByUser(
                    activeUser.getId(), null, null, PageRequest.of(0, 20));

            assertThat(result.getContent().get(0).getMaxStreak115()).isNull();
        }

        static Stream<Arguments> attemptAggregateSorts() {
            return Stream.of(
                    Arguments.of(Sort.Direction.ASC, "lastPlayedAt",
                            List.of("MAX(COALESCE(s2.timeSet, s2.createdAt))")),
                    Arguments.of(Sort.Direction.DESC, "playCount",
                            List.of("MAX(s2.playCount)")),
                    Arguments.of(Sort.Direction.DESC, "maxStreak115",
                            List.of("MAX(s2.streak115)",
                                    "s2.supersedesReason <> 'Campaign attempt'")));
        }

        @ParameterizedTest(name = "sort by {1} aggregates over every attempt")
        @MethodSource("attemptAggregateSorts")
        void sortTranslatesToAnAggregateOverEveryAttempt(Sort.Direction direction, String key,
                List<String> fragments) {
            when(scoreRepository.findActiveByUsers(eq(List.of(activeUser.getId())), isNull(), isNull(),
                    any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            scoreQueryService.findByUser(activeUser.getId(), null, null,
                    PageRequest.of(0, 20, Sort.by(direction, key)));

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(scoreRepository).findActiveByUsers(eq(List.of(activeUser.getId())), isNull(), isNull(),
                    captor.capture());
            String sort = captor.getValue().getSort().toString();
            assertThat(sort).contains(fragments);
            assertThat(sort).contains("s2.user = s.user");
            assertThat(sort).contains("s2.mapDifficulty = s.mapDifficulty");
        }
    }

    @Nested
    class FindApCurves {

        @Test
        void groupsTheRowsPerCategoryInQueryOrderAndResolvesTheMergedAccount() {
            UUID trueAcc = UUID.randomUUID();
            UUID techAcc = UUID.randomUUID();
            when(duplicateUserService.resolvePrimaryUserId(7L)).thenReturn(1L);
            when(scoreRepository.findTopApByCategory(1L, 100)).thenReturn(List.of(
                    new Object[] { techAcc, "tech_acc", 450.0 },
                    new Object[] { trueAcc, "true_acc", 900.0 },
                    new Object[] { trueAcc, "true_acc", 600.0 }));

            List<ApCurveResponse> curves = scoreQueryService.findApCurves(7L, 100);

            assertThat(curves).containsExactly(
                    new ApCurveResponse(techAcc, "tech_acc", List.of(450.0)),
                    new ApCurveResponse(trueAcc, "true_acc", List.of(900.0, 600.0)));
        }

        @Test
        void clampsTheLimitToOneHundred() {
            scoreQueryService.findApCurves(1L, 5000);
            scoreQueryService.findApCurves(1L, 0);

            verify(scoreRepository).findTopApByCategory(1L, 100);
            verify(scoreRepository).findTopApByCategory(1L, 1);
        }
    }
}
