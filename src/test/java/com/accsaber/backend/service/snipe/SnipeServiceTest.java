package com.accsaber.backend.service.snipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.map.PublicMapDifficultyResponse;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.dto.response.score.SnipeComparisonResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.score.SnipeSort;
import com.accsaber.backend.model.entity.score.SnipeUnplayed;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.map.MapService;
import com.accsaber.backend.service.score.ScoreService;

@ExtendWith(MockitoExtension.class)
class SnipeServiceTest {

    private static final Long SNIPER_ID = 76561198000000001L;
    private static final Long TARGET_ID = 76561198000000002L;

    @Mock
    private ScoreRepository scoreRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ScoreService scoreService;
    @Mock
    private MapService mapService;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    @InjectMocks
    private SnipeService snipeService;

    @Nested
    class FindSnipeComparisons {

        @Test
        void returnsComparisonsWithCorrectDelta() {
            Pageable pageable = PageRequest.of(0, 10);
            UUID diffId = UUID.randomUUID();
            MapDifficulty diff = MapDifficulty.builder().id(diffId).build();
            Score sniperScore = scoreOn(diff, 900_000);
            Score targetScore = scoreOn(diff, 950_000);
            Page<Object[]> page = new PageImpl<>(List.<Object[]>of(new Object[] { targetScore, sniperScore }));

            mockUsersExist();
            when(scoreRepository.findSnipePairs(eq(SNIPER_ID), eq(TARGET_ID), isNull(), eq(false), eq(true), eq(false), any()))
                    .thenReturn(page);
            ScoreResponse sniperResponse = ScoreResponse.builder().build();
            ScoreResponse targetResponse = ScoreResponse.builder().build();
            PublicMapDifficultyResponse mapDiffResponse = PublicMapDifficultyResponse.builder().id(diffId).build();
            when(scoreService.mapToResponse(sniperScore)).thenReturn(sniperResponse);
            when(scoreService.mapToResponse(targetScore)).thenReturn(targetResponse);
            when(mapService.getDifficultyResponsePublic(diffId)).thenReturn(mapDiffResponse);

            Page<SnipeComparisonResponse> result = snipeService.findSnipeComparisons(query(null, null, null), pageable);

            assertThat(result.getContent()).hasSize(1);
            SnipeComparisonResponse comparison = result.getContent().get(0);
            assertThat(comparison.getMapDifficulty()).isSameAs(mapDiffResponse);
            assertThat(comparison.getSniperScore()).isSameAs(sniperResponse);
            assertThat(comparison.getTargetScore()).isSameAs(targetResponse);
            assertThat(comparison.getScoreDelta()).isEqualTo(50_000);
        }

        @Test
        void filtersByCategoryCode() {
            UUID categoryId = UUID.randomUUID();
            Category category = Category.builder().id(categoryId).code("true_acc").name("True Acc").build();
            mockUsersExist();
            when(categoryRepository.findByCodeAndActiveTrue("true_acc")).thenReturn(Optional.of(category));
            when(scoreRepository.findSnipePairs(eq(SNIPER_ID), eq(TARGET_ID), eq(categoryId), eq(false), eq(true), eq(false), any()))
                    .thenReturn(new PageImpl<>(List.<Object[]>of()));

            Page<SnipeComparisonResponse> result = snipeService.findSnipeComparisons(query("true_acc", null, null),
                    PageRequest.of(0, 10));

            assertThat(result.getContent()).isEmpty();
            verify(scoreRepository).findSnipePairs(eq(SNIPER_ID), eq(TARGET_ID), eq(categoryId), eq(false), eq(true), eq(false), any());
        }

        @Test
        void overallCategoryFlipsFlag() {
            mockUsersExist();
            when(categoryRepository.findByCodeAndActiveTrue("overall"))
                    .thenReturn(Optional.of(Category.builder().code("overall").name("Overall").build()));
            when(scoreRepository.findSnipePairs(eq(SNIPER_ID), eq(TARGET_ID), isNull(), eq(true), eq(true), eq(false), any()))
                    .thenReturn(new PageImpl<>(List.<Object[]>of()));

            snipeService.findSnipeComparisons(query("overall", null, null), PageRequest.of(0, 10));

            verify(scoreRepository).findSnipePairs(eq(SNIPER_ID), eq(TARGET_ID), isNull(), eq(true), eq(true), eq(false), any());
        }

        @Test
        void defaultsToClosestGapAscending() {
            mockUsersExist();
            when(scoreRepository.findSnipePairs(eq(SNIPER_ID), eq(TARGET_ID), isNull(), eq(false), eq(true), eq(false), any()))
                    .thenReturn(new PageImpl<>(List.<Object[]>of()));

            snipeService.findSnipeComparisons(query(null, null, null), PageRequest.of(2, 10));

            Sort.Order primary = capturedSort().toList().get(0);
            assertThat(primary.getProperty()).isEqualTo(SnipeSort.GAP.getExpression());
            assertThat(primary.getDirection()).isEqualTo(Sort.Direction.ASC);
            assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
            assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
        }

        @Test
        void appliesRequestedSortAndDirection() {
            mockUsersExist();
            when(scoreRepository.findSnipePairs(eq(SNIPER_ID), eq(TARGET_ID), isNull(), eq(false), eq(true), eq(false), any()))
                    .thenReturn(new PageImpl<>(List.<Object[]>of()));

            snipeService.findSnipeComparisons(query(null, SnipeSort.TARGET_AP, Sort.Direction.ASC),
                    PageRequest.of(0, 10));

            Sort.Order primary = capturedSort().toList().get(0);
            assertThat(primary.getProperty()).isEqualTo(SnipeSort.TARGET_AP.getExpression());
            assertThat(primary.getDirection()).isEqualTo(Sort.Direction.ASC);
        }

        @Test
        void unplayedMapsComeBackWithNoSniperScoreAndTheFullDelta() {
            UUID diffId = UUID.randomUUID();
            MapDifficulty diff = MapDifficulty.builder().id(diffId).build();
            Score targetScore = scoreOn(diff, 950_000);

            mockUsersExist();
            when(scoreRepository.findSnipePairs(eq(SNIPER_ID), eq(TARGET_ID), isNull(), eq(false), eq(false), eq(true),
                    any()))
                    .thenReturn(new PageImpl<>(List.<Object[]>of(new Object[] { targetScore, null })));
            ScoreResponse targetResponse = ScoreResponse.builder().build();
            when(scoreService.mapToResponse(targetScore)).thenReturn(targetResponse);
            when(mapService.getDifficultyResponsePublic(diffId))
                    .thenReturn(PublicMapDifficultyResponse.builder().id(diffId).build());

            SnipeQuery onlyUnplayed = new SnipeQuery(SNIPER_ID, TARGET_ID, null, null, null, SnipeUnplayed.ONLY);
            Page<SnipeComparisonResponse> result = snipeService.findSnipeComparisons(onlyUnplayed,
                    PageRequest.of(0, 10));

            SnipeComparisonResponse comparison = result.getContent().get(0);
            assertThat(comparison.getSniperScore()).isNull();
            assertThat(comparison.getTargetScore()).isSameAs(targetResponse);
            assertThat(comparison.getScoreDelta()).isEqualTo(950_000);
        }

        @Test
        void includingUnplayedMapsAllowsBothSides() {
            mockUsersExist();
            when(scoreRepository.findSnipePairs(eq(SNIPER_ID), eq(TARGET_ID), isNull(), eq(false), eq(true), eq(true),
                    any()))
                    .thenReturn(new PageImpl<>(List.<Object[]>of()));

            snipeService.findSnipeComparisons(new SnipeQuery(SNIPER_ID, TARGET_ID, null, null, null,
                    SnipeUnplayed.INCLUDE), PageRequest.of(0, 10));

            verify(scoreRepository).findSnipePairs(eq(SNIPER_ID), eq(TARGET_ID), isNull(), eq(false), eq(true),
                    eq(true), any());
        }

        @Test
        void unknownCategoryThrows() {
            mockUsersExist();
            when(categoryRepository.findByCodeAndActiveTrue("nope")).thenReturn(Optional.empty());

            assertThatThrownBy(
                    () -> snipeService.findSnipeComparisons(query("nope", null, null), PageRequest.of(0, 10)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void rejectsSelfSnipe() {
            SnipeQuery selfQuery = new SnipeQuery(SNIPER_ID, SNIPER_ID, null, null, null, null);

            assertThatThrownBy(() -> snipeService.findSnipeComparisons(selfQuery, PageRequest.of(0, 10)))
                    .isInstanceOf(ValidationException.class);
            verify(userRepository, never()).findByIdAndActiveTrue(any());
        }

        @Test
        void throwsWhenSniperMissing() {
            when(userRepository.findByIdAndActiveTrue(SNIPER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> snipeService.findSnipeComparisons(query(null, null, null), PageRequest.of(0, 10)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class FindSnipeDifficulties {

        @Test
        void returnsTargetDifficultiesAndCategoryLabel() {
            UUID categoryId = UUID.randomUUID();
            Category category = Category.builder().id(categoryId).code("tech_acc").name("Tech Acc").build();
            MapDifficulty diff = MapDifficulty.builder().id(UUID.randomUUID()).build();
            mockUsersExist();
            when(categoryRepository.findByCodeAndActiveTrue("tech_acc")).thenReturn(Optional.of(category));
            when(scoreRepository.findSnipePairs(eq(SNIPER_ID), eq(TARGET_ID), eq(categoryId), eq(false), eq(true), eq(false), any()))
                    .thenReturn(new PageImpl<>(List.<Object[]>of(new Object[] { scoreOn(diff, 950_000), null })));

            SnipeSelection selection = snipeService.findSnipeDifficulties(query("tech_acc", null, null), 20);

            assertThat(selection.target().getName()).isEqualTo("Target");
            assertThat(selection.categoryLabel()).isEqualTo("Tech Acc");
            assertThat(selection.difficulties()).containsExactly(diff);
            capturedSort();
            assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
        }

        @Test
        void unlimitedSizeStaysUnpagedButKeepsTheSort() {
            mockUsersExist();
            when(scoreRepository.findSnipePairs(eq(SNIPER_ID), eq(TARGET_ID), isNull(), eq(false), eq(true), eq(false), any()))
                    .thenReturn(new PageImpl<>(List.<Object[]>of()));

            snipeService.findSnipeDifficulties(query(null, SnipeSort.AP_GAP, null), 0);

            Sort.Order primary = capturedSort().toList().get(0);
            assertThat(pageableCaptor.getValue().isUnpaged()).isTrue();
            assertThat(primary.getProperty()).isEqualTo(SnipeSort.AP_GAP.getExpression());
            assertThat(primary.getDirection()).isEqualTo(Sort.Direction.DESC);
        }
    }

    private Sort capturedSort() {
        verify(scoreRepository).findSnipePairs(any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean(),
                pageableCaptor.capture());
        return pageableCaptor.getValue().getSort();
    }

    private SnipeQuery query(String categoryCode, SnipeSort sort, Sort.Direction direction) {
        return new SnipeQuery(SNIPER_ID, TARGET_ID, categoryCode, sort, direction, null);
    }

    private void mockUsersExist() {
        when(userRepository.findByIdAndActiveTrue(eq(SNIPER_ID)))
                .thenReturn(Optional.of(User.builder().id(SNIPER_ID).name("Sniper").build()));
        when(userRepository.findByIdAndActiveTrue(eq(TARGET_ID)))
                .thenReturn(Optional.of(User.builder().id(TARGET_ID).name("Target").build()));
    }

    private Score scoreOn(MapDifficulty diff, int value) {
        return Score.builder()
                .id(UUID.randomUUID())
                .mapDifficulty(diff)
                .score(value)
                .build();
    }
}
