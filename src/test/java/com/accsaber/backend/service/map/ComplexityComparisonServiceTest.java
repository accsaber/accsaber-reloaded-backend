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
    void ranksEveryScenarioAcrossTheWholeEligibleSetAndSortsByTheChosenOne() {
        Map<ComplexityScenario, ScenarioState> states = new EnumMap<>(ComplexityScenario.class);
        states.put(ComplexityScenario.CURRENT, state(Map.of(
                first.getId(), aggregate(10.0, 40, 120.0),
                second.getId(), aggregate(9.0, 40, 100.0),
                other.getId(), aggregate(7.0, 40, 130.0),
                thin.getId(), aggregate(8.0, 3, 500.0))));
        states.put(ComplexityScenario.NEW_SCRIPT, state(Map.of(
                first.getId(), aggregate(9.5, 40, 90.0),
                second.getId(), aggregate(11.0, 40, 140.0),
                other.getId(), aggregate(7.0, 40, 130.0),
                thin.getId(), aggregate(8.0, 3, 500.0))));
        when(scenarioService.stored()).thenReturn(states);
        when(mapDifficultyRepository.findAllByIdInAndActiveTrueWithMapAndCategory(anyList()))
                .thenReturn(List.of(first, second, other, thin));
        when(estimateService.estimatesFor(any())).thenReturn(Map.of());

        List<DifficultyRow> rows = service.highestAverageAp(ComplexityScenario.NEW_SCRIPT,
                new ComplexityComparisonService.MapFilter(tech.getId(), MapDifficultyStatus.RANKED, null, null), 10, 1);

        assertThat(rows).hasSize(1);
        DifficultyRow row = rows.get(0);
        assertThat(row.getMapDifficultyId()).isEqualTo(second.getId());
        assertThat(row.getScenarios().get(ComplexityScenario.NEW_SCRIPT).getBoardRank()).isEqualTo(1);
        assertThat(row.getScenarios().get(ComplexityScenario.CURRENT).getBoardRank()).isEqualTo(2);
        assertThat(row.getDeltas().get(ComplexityScenario.NEW_SCRIPT).getBoardRank()).isEqualTo(-1);
    }

    @Test
    void mapsUnderTheScoreMinimumGetNoPosition() {
        Map<ComplexityScenario, ScenarioState> states = new EnumMap<>(ComplexityScenario.class);
        states.put(ComplexityScenario.CURRENT, state(Map.of(
                first.getId(), aggregate(10.0, 40, 120.0),
                thin.getId(), aggregate(8.0, 3, 500.0))));
        when(scenarioService.stored()).thenReturn(states);
        when(mapDifficultyRepository.findAllByIdInAndActiveTrueWithMapAndCategory(anyList()))
                .thenReturn(List.of(first, thin));
        when(estimateService.estimatesFor(any())).thenReturn(Map.of());

        List<DifficultyRow> rows = service.highestAverageAp(ComplexityScenario.CURRENT,
                new ComplexityComparisonService.MapFilter(null, MapDifficultyStatus.RANKED, null, null), 10, 50);

        assertThat(rows).extracting(DifficultyRow::getMapDifficultyId).containsExactly(first.getId());
        assertThat(rows.get(0).getScenarios().get(ComplexityScenario.CURRENT).getBoardRank()).isEqualTo(1);
    }

    @Test
    void searchNarrowsTheBoardWithoutMovingThePositions() {
        Map<ComplexityScenario, ScenarioState> states = new EnumMap<>(ComplexityScenario.class);
        states.put(ComplexityScenario.CURRENT, state(Map.of(
                first.getId(), aggregate(10.0, 40, 120.0),
                second.getId(), aggregate(9.0, 40, 100.0))));
        when(scenarioService.stored()).thenReturn(states);
        when(mapDifficultyRepository.findAllByIdInAndActiveTrueWithMapAndCategory(anyList()))
                .thenReturn(List.of(first, second));
        when(estimateService.estimatesFor(any())).thenReturn(Map.of());

        List<DifficultyRow> rows = service.highestAverageAp(ComplexityScenario.CURRENT,
                new ComplexityComparisonService.MapFilter(null, MapDifficultyStatus.RANKED, null, "SECOND"), 10, 50);

        assertThat(rows).extracting(DifficultyRow::getMapDifficultyId).containsExactly(second.getId());
        assertThat(rows.get(0).getScenarios().get(ComplexityScenario.CURRENT).getBoardRank()).isEqualTo(2);
    }

    @Test
    void applyCapsEveryMoveAtTheStepLimit() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        when(scenarioService.complexitiesFor(ComplexityScenario.CURRENT)).thenReturn(Map.of(a, 5.0, b, 8.0, c, 6.0));
        when(scenarioService.complexitiesFor(ComplexityScenario.NEW_SCRIPT)).thenReturn(Map.of(a, 7.0, b, 8.0, c, 4.2));

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
        when(estimateService.estimatesFor(any())).thenReturn(Map.of());
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
        when(mapDifficultyRepository.findByStatusAndActiveTrueWithCategory(MapDifficultyStatus.QUEUE))
                .thenReturn(List.of(queued, priced));
        when(complexityService.findActiveComplexitiesForDifficulties(any())).thenReturn(Map.of(priced.getId(), 9.0));
        when(estimateService.estimatesFor(any())).thenReturn(Map.of(
                queued.getId(), com.accsaber.backend.model.entity.map.MapDifficultyComplexityEstimate.builder().complexity(8.5).build(),
                priced.getId(), com.accsaber.backend.model.entity.map.MapDifficultyComplexityEstimate.builder().complexity(9.0).build()));

        service.apply(new ComplexityComparisonService.ApplyOptions("queue pass", null, null, MapDifficultyStatus.QUEUE), 1L,
                UUID.randomUUID());

        org.mockito.ArgumentCaptor<com.accsaber.backend.model.dto.request.map.UpdateMapComplexityRequest> request = org.mockito.ArgumentCaptor
                .captor();
        org.mockito.Mockito.verify(mapService).updateComplexity(org.mockito.ArgumentMatchers.eq(queued.getId()), request.capture(), any(), any());
        assertThat(request.getValue().getComplexity()).isEqualTo(8.5);
        assertThat(request.getValue().getReason()).isEqualTo("queue pass");
        org.mockito.Mockito.verify(reweightService, org.mockito.Mockito.never()).bulkReweight(any(), any(), any(), any());
    }

    private static ScenarioState state(Map<UUID, MapAggregate> aggregates) {
        Map<UUID, Double> complexities = new java.util.HashMap<>();
        aggregates.forEach((id, a) -> complexities.put(id, a.complexity()));
        return new ScenarioState(complexities, Map.of(), Map.of(), Map.of(), Map.of(), aggregates, Map.of(),
                new ComplexityScenarioService.Ladder(0, 0, 0, 0, 0, 0, 0, 0));
    }

    private static MapAggregate aggregate(double complexity, int scores, double averageWeightedAp) {
        return new MapAggregate(complexity, scores, 1000.0, 700.0, averageWeightedAp);
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
