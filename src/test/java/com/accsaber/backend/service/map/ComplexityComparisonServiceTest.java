package com.accsaber.backend.service.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.config.ComplexityRaterProperties;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.DifficultyRow;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.map.ComplexityScenarioService.MapAggregate;
import com.accsaber.backend.service.map.ComplexityScenarioService.ScenarioState;

@ExtendWith(MockitoExtension.class)
class ComplexityComparisonServiceTest {

    @Mock
    private MapDifficultyRepository mapDifficultyRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ComplexityScenarioService scenarioService;
    @Mock
    private ComplexityEstimateService estimateService;
    @Mock
    private ComplexityRaterProperties raterProperties;
    @Mock
    private ReweightService reweightService;
    @Mock
    private MapService mapService;
    @Mock
    private MapDifficultyComplexityService complexityService;
    @InjectMocks
    private ComplexityComparisonService service;

    private final Category tech = Category.builder().id(UUID.randomUUID()).code("tech_acc").build();
    private final Category standard = Category.builder().id(UUID.randomUUID()).code("standard_acc").build();
    private final MapDifficulty first = difficulty("First", tech);
    private final MapDifficulty second = difficulty("Second", tech);
    private final MapDifficulty other = difficulty("Other", standard);
    private final MapDifficulty thin = difficulty("Thin", tech);

    @Test
    void applyCapsEveryMoveAtTheStepLimit() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        when(scenarioService.complexitiesFor(ComplexityScenario.CURRENT)).thenReturn(Map.of(a, 5.0, b, 8.0, c, 6.0));
        when(scenarioService.complexitiesFor(ComplexityScenario.NEW_SCRIPT)).thenReturn(Map.of(a, 7.0, b, 9.5, c, 4.2));

        when(mapDifficultyRepository.findPinnedDifficultyIds()).thenReturn(List.of(b));

        service.apply(new ComplexityComparisonService.ApplyOptions("round 1", 0.5, null, MapDifficultyStatus.RANKED), 1L, UUID.randomUUID());

        org.mockito.ArgumentCaptor<List<com.accsaber.backend.model.dto.request.map.BulkReweightRequest.Item>> items = org.mockito.ArgumentCaptor
                .captor();
        org.mockito.Mockito.verify(reweightService).bulkReweight(items.capture(), org.mockito.ArgumentMatchers.eq("round 1"), any(), any());
        assertThat(items.getValue()).extracting(i -> i.getMapDifficultyId() + "=" + i.getComplexity())
                .containsExactlyInAnyOrder(a + "=5.5", c + "=5.5");
        assertThat(ComplexityComparisonService.step(5.0, 7.0, null)).isEqualTo(7.0);
    }

    @Test
    void playerPlaysUnionTheBestPlaysUnderEveryScenario() {
        ComplexityScenarioService.Play firstNow = new ComplexityScenarioService.Play(7L, first.getId(), tech.getId(), 0.99, 900.0, 900.0, 1, 3);
        ComplexityScenarioService.Play secondNow = new ComplexityScenarioService.Play(7L, second.getId(), tech.getId(), 0.98, 800.0, 700.0, 2, 9);
        ComplexityScenarioService.Play firstNew = new ComplexityScenarioService.Play(7L, first.getId(), tech.getId(), 0.99, 850.0, 800.0, 2, 4);
        ComplexityScenarioService.Play secondNew = new ComplexityScenarioService.Play(7L, second.getId(), tech.getId(), 0.98, 950.0, 950.0, 1, 2);
        Map<ComplexityScenario, ScenarioState> states = new EnumMap<>(ComplexityScenario.class);
        states.put(ComplexityScenario.CURRENT, new ScenarioState(Map.of(), Map.of(), Map.of(7L, List.of(firstNow, secondNow)),
                Map.of(tech.getId(), Map.of(7L, new ComplexityScenarioService.PlayerTotal(1600.0, 5))), Map.of(), Map.of(), Map.of(),
                new ComplexityScenarioService.Ladder(0, 0, 0, 0, 0, 0, 0, 0)));
        states.put(ComplexityScenario.NEW_SCRIPT, new ScenarioState(Map.of(), Map.of(), Map.of(7L, List.of(firstNew, secondNew)),
                Map.of(tech.getId(), Map.of(7L, new ComplexityScenarioService.PlayerTotal(1750.0, 4))), Map.of(), Map.of(), Map.of(),
                new ComplexityScenarioService.Ladder(0, 0, 0, 0, 0, 0, 0, 0)));
        when(scenarioService.stored()).thenReturn(states);
        when(userRepository.findById(7L)).thenReturn(java.util.Optional.of(
                com.accsaber.backend.model.entity.user.User.builder().id(7L).name("Seven").build()));
        when(mapDifficultyRepository.findAllByIdInAndActiveTrueWithMapAndCategory(anyList())).thenReturn(List.of(first, second));
        when(estimateService.summaryRowsFor(any())).thenReturn(List.of());
        when(categoryRepository.findByActiveTrue()).thenReturn(List.of(tech, standard));

        var plays = service.playerPlays(7L, 1);

        assertThat(plays.getUserId()).isEqualTo("7");
        assertThat(plays.getCategories()).hasSize(1);
        var category = plays.getCategories().get(0);
        assertThat(category.getCategoryCode()).isEqualTo("tech_acc");
        assertThat(category.getScenarios().get(ComplexityScenario.NEW_SCRIPT).getAp()).isEqualTo(1750.0);
        assertThat(category.getDeltas().get(ComplexityScenario.NEW_SCRIPT).getRank()).isEqualTo(-1);
        assertThat(category.getPlays()).extracting(p -> p.getDifficulty().getMapDifficultyId())
                .containsExactly(first.getId(), second.getId());
        var secondRow = category.getPlays().get(1);
        assertThat(secondRow.getScenarios().get(ComplexityScenario.NEW_SCRIPT).getPosition()).isEqualTo(1);
        assertThat(secondRow.getDeltas().get(ComplexityScenario.NEW_SCRIPT).getPosition()).isEqualTo(-1);
        assertThat(secondRow.getDeltas().get(ComplexityScenario.NEW_SCRIPT).getAp()).isEqualTo(150.0);
    }

    @Test
    void applyScopedToABatchLeavesTheRestOfThePoolAlone() {
        UUID inBatch = UUID.randomUUID();
        UUID outside = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        when(scenarioService.complexitiesFor(ComplexityScenario.CURRENT)).thenReturn(Map.of(inBatch, 5.0, outside, 6.0));
        when(scenarioService.complexitiesFor(ComplexityScenario.NEW_SCRIPT)).thenReturn(Map.of(inBatch, 7.0, outside, 4.0));
        when(mapDifficultyRepository.findByBatchIdAndActiveTrueWithCategory(batchId))
                .thenReturn(List.of(MapDifficulty.builder().id(inBatch).build()));
        when(mapDifficultyRepository.findPinnedDifficultyIds()).thenReturn(List.of());

        service.apply(new ComplexityComparisonService.ApplyOptions("july round", null, batchId, null), 1L, UUID.randomUUID());

        org.mockito.ArgumentCaptor<List<com.accsaber.backend.model.dto.request.map.BulkReweightRequest.Item>> items = org.mockito.ArgumentCaptor
                .captor();
        org.mockito.Mockito.verify(reweightService).bulkReweight(items.capture(), org.mockito.ArgumentMatchers.eq("july round"), any(), any());
        assertThat(items.getValue()).extracting(i -> i.getMapDifficultyId() + "=" + i.getComplexity())
                .containsExactly(inBatch + "=7.0");
    }

    @Test
    void applyToTheQueueSetsEachMapToTheScriptWithoutAReweight() {
        MapDifficulty queued = difficulty("Queued", tech);
        queued.setStatus(MapDifficultyStatus.QUEUE);
        MapDifficulty priced = difficulty("Priced", tech);
        priced.setStatus(MapDifficultyStatus.QUEUE);
        MapDifficulty held = difficulty("Held", tech);
        held.setStatus(MapDifficultyStatus.QUEUE);
        held.setComplexityPinned(true);
        when(mapDifficultyRepository.findByStatusAndActiveTrueWithCategory(MapDifficultyStatus.QUEUE))
                .thenReturn(List.of(queued, priced, held));
        when(complexityService.findActiveComplexitiesForDifficulties(any())).thenReturn(Map.of(priced.getId(), 9.0));
        when(estimateService.estimatesFor(any())).thenReturn(Map.of(
                queued.getId(), com.accsaber.backend.model.entity.map.MapDifficultyComplexityEstimate.builder().complexity(8.5).build(),
                priced.getId(), com.accsaber.backend.model.entity.map.MapDifficultyComplexityEstimate.builder().complexity(9.0).build(),
                held.getId(), com.accsaber.backend.model.entity.map.MapDifficultyComplexityEstimate.builder().complexity(4.0).build()));

        service.apply(new ComplexityComparisonService.ApplyOptions("queue pass", null, null, MapDifficultyStatus.QUEUE), 1L,
                UUID.randomUUID());

        org.mockito.ArgumentCaptor<com.accsaber.backend.model.dto.request.map.UpdateMapComplexityRequest> request = org.mockito.ArgumentCaptor
                .captor();
        org.mockito.Mockito.verify(mapService).updateComplexity(org.mockito.ArgumentMatchers.eq(queued.getId()), request.capture(), any(), any());
        assertThat(request.getValue().getComplexity()).isEqualTo(8.5);
        assertThat(request.getValue().getReason()).isEqualTo("queue pass");
        org.mockito.Mockito.verify(reweightService, org.mockito.Mockito.never()).bulkReweight(any(), any(), any(), any());
        org.mockito.Mockito.verify(mapService, org.mockito.Mockito.never()).updateComplexity(org.mockito.ArgumentMatchers.eq(held.getId()), any(), any(), any());
    }

    @Test
    void theListPagesTheRowsButCountsTheWholeRound() {
        thin.setComplexityPinned(true);
        when(mapDifficultyRepository.findByStatusAndActiveTrueWithCategory(MapDifficultyStatus.RANKED))
                .thenReturn(List.of(first, second, other, thin));
        when(scenarioService.stored()).thenReturn(states(
                Map.of(first.getId(), aggregate(5.0, 10, 400.0), second.getId(), aggregate(7.0, 10, 500.0),
                        other.getId(), aggregate(6.0, 10, 450.0), thin.getId(), aggregate(3.0, 10, 200.0)),
                Map.of(first.getId(), aggregate(9.0, 10, 700.0), second.getId(), aggregate(7.0, 10, 500.0),
                        other.getId(), aggregate(6.5, 10, 470.0), thin.getId(), aggregate(8.0, 10, 600.0))));
        when(estimateService.summaryRowsFor(any())).thenReturn(List.of(
                summaryRow(first.getId(), 9.0, "b7"),
                summaryRow(second.getId(), 7.0, "b7"),
                summaryRow(other.getId(), 6.5, "b6")));

        var page = service.difficulties(filter(null), new ComplexityComparisonService.Paging(0, 2, null, true));

        assertThat(page.getRows()).hasSize(2);
        assertThat(page.getTotal()).isEqualTo(4);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getRows()).extracting(DifficultyRow::getMapDifficultyId)
                .containsExactly(thin.getId(), first.getId());
        var summary = page.getSummary();
        assertThat(summary.getDifficulties()).isEqualTo(4);
        assertThat(summary.getPinned()).isEqualTo(1);
        assertThat(summary.getMissingEstimate()).isEqualTo(1);
        assertThat(summary.getStaleEstimate()).isEqualTo(1);
        assertThat(summary.getModelHash()).isEqualTo("b7");
        assertThat(summary.getMoving().get(ComplexityScenario.NEW_SCRIPT)).isEqualTo(2);
    }

    @Test
    void theListLeavesTheEstimateInputsToTheSingleMapView() {
        when(mapDifficultyRepository.findByStatusAndActiveTrueWithCategory(MapDifficultyStatus.RANKED))
                .thenReturn(List.of(first));
        when(scenarioService.stored()).thenReturn(states(Map.of(first.getId(), aggregate(5.0, 10, 400.0)),
                Map.of(first.getId(), aggregate(6.0, 10, 450.0))));
        when(estimateService.summaryRowsFor(any())).thenReturn(List.of(summaryRow(first.getId(), 6.0, "b7")));

        var page = service.difficulties(filter(null), new ComplexityComparisonService.Paging(0, 50, null, false));

        assertThat(page.getRows().get(0).getEstimates()).isEmpty();
        org.mockito.Mockito.verify(estimateService, org.mockito.Mockito.never()).estimatesFor(any());
    }

    @Test
    void aPinnedFilterNarrowsTheRoundItself() {
        thin.setComplexityPinned(true);
        when(mapDifficultyRepository.findByStatusAndActiveTrueWithCategory(MapDifficultyStatus.RANKED))
                .thenReturn(List.of(first, thin));
        when(scenarioService.stored()).thenReturn(states(
                Map.of(first.getId(), aggregate(5.0, 10, 400.0), thin.getId(), aggregate(3.0, 10, 200.0)),
                Map.of(first.getId(), aggregate(9.0, 10, 700.0), thin.getId(), aggregate(8.0, 10, 600.0))));
        when(estimateService.summaryRowsFor(any())).thenReturn(List.of());

        var page = service.difficulties(filter(true), new ComplexityComparisonService.Paging(0, 50, null, false));

        assertThat(page.getRows()).extracting(DifficultyRow::getMapDifficultyId).containsExactly(thin.getId());
        assertThat(page.getSummary().getDifficulties()).isEqualTo(1);
        assertThat(page.getSummary().getMoving().get(ComplexityScenario.NEW_SCRIPT)).isZero();
    }

    @Test
    void theLeaderboardOnlyLooksUpThePlayersOnThePage() {
        List<ComplexityScenarioService.Play> now = new java.util.ArrayList<>();
        List<ComplexityScenarioService.Play> next = new java.util.ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            now.add(new ComplexityScenarioService.Play((long) i, first.getId(), tech.getId(), 0.99, 1000.0 - i,
                    1000.0 - i, 1, i));
            next.add(new ComplexityScenarioService.Play((long) i, first.getId(), tech.getId(), 0.99, 1100.0 - i,
                    1100.0 - i, 1, i));
        }
        Map<ComplexityScenario, ScenarioState> states = new EnumMap<>(ComplexityScenario.class);
        states.put(ComplexityScenario.CURRENT, board(now));
        states.put(ComplexityScenario.NEW_SCRIPT, board(next));
        when(mapDifficultyRepository.findByIdAndActiveTrueWithMapAndCategory(first.getId()))
                .thenReturn(java.util.Optional.of(first));
        when(scenarioService.stored()).thenReturn(states);
        when(estimateService.estimatesFor(List.of(first.getId()))).thenReturn(Map.of());
        when(userRepository.findAllById(any())).thenReturn(List.of());

        var leaderboard = service.leaderboard(first.getId(),
                new ComplexityComparisonService.Paging(1, 2, null, false));

        assertThat(leaderboard.getTotal()).isEqualTo(5);
        assertThat(leaderboard.getTotalPages()).isEqualTo(3);
        assertThat(leaderboard.getRows()).extracting(r -> r.getScenarios().get(ComplexityScenario.CURRENT).getRank())
                .containsExactly(3, 4);
        org.mockito.ArgumentCaptor<Iterable<Long>> ids = org.mockito.ArgumentCaptor.captor();
        org.mockito.Mockito.verify(userRepository).findAllById(ids.capture());
        assertThat(ids.getValue()).containsExactlyInAnyOrder(3L, 4L);
    }

    private static ComplexityComparisonService.MapFilter filter(Boolean pinned) {
        return new ComplexityComparisonService.MapFilter(null, MapDifficultyStatus.RANKED, null, null, pinned);
    }

    private static com.accsaber.backend.model.dto.projection.EstimateSummaryRow summaryRow(UUID difficultyId,
            double complexity, String modelHash) {
        return new com.accsaber.backend.model.dto.projection.EstimateSummaryRow(difficultyId, complexity, "v9",
                java.time.Instant.parse("2026-09-0" + (modelHash.equals("b7") ? "9" : "1") + "T00:00:00Z"), modelHash);
    }

    private static Map<ComplexityScenario, ScenarioState> states(Map<UUID, MapAggregate> now,
            Map<UUID, MapAggregate> next) {
        Map<ComplexityScenario, ScenarioState> states = new EnumMap<>(ComplexityScenario.class);
        states.put(ComplexityScenario.CURRENT, state(now));
        states.put(ComplexityScenario.NEW_SCRIPT, state(next));
        return states;
    }

    private static ScenarioState board(List<ComplexityScenarioService.Play> plays) {
        return new ScenarioState(Map.of(), Map.of(plays.get(0).difficultyId(), plays), Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), new ComplexityScenarioService.Ladder(0, 0, 0, 0, 0, 0, 0, 0));
    }

    private static ScenarioState state(Map<UUID, MapAggregate> aggregates) {
        Map<UUID, Double> complexities = new java.util.HashMap<>();
        aggregates.forEach((id, a) -> complexities.put(id, a.complexity()));
        return new ScenarioState(complexities, Map.of(), Map.of(), Map.of(), Map.of(), aggregates, Map.of(),
                new ComplexityScenarioService.Ladder(0, 0, 0, 0, 0, 0, 0, 0));
    }

    private static MapAggregate aggregate(double complexity, int scores, double averageWeightedAp) {
        return new MapAggregate(complexity, scores, 1000.0, averageWeightedAp);
    }

    private static MapDifficulty difficulty(String name, Category category) {
        return MapDifficulty.builder()
                .id(UUID.randomUUID())
                .status(MapDifficultyStatus.RANKED)
                .characteristic("Standard")
                .category(category)
                .map(com.accsaber.backend.model.entity.map.Map.builder().id(UUID.randomUUID()).songName(name).build())
                .build();
    }
}
