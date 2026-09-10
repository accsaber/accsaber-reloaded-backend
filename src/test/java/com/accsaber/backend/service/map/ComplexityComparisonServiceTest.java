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
        states.put(ComplexityScenario.OLD_SCRIPT, state(Map.of()));
        states.put(ComplexityScenario.NEW_SCRIPT, state(Map.of(
                first.getId(), aggregate(9.5, 40, 90.0),
                second.getId(), aggregate(11.0, 40, 140.0),
                other.getId(), aggregate(7.0, 40, 130.0),
                thin.getId(), aggregate(8.0, 3, 500.0))));
        when(scenarioService.stored()).thenReturn(states);
        when(mapDifficultyRepository.findAllByIdInAndActiveTrueWithMapAndCategory(anyList()))
                .thenReturn(List.of(first, second, other, thin));
        when(estimateService.estimatesFor(any())).thenReturn(Map.of());

        List<DifficultyRow> rows = service.highestAverageAp(ComplexityScenario.NEW_SCRIPT, tech.getId(), 10, 1);

        assertThat(rows).hasSize(1);
        DifficultyRow row = rows.get(0);
        assertThat(row.getMapDifficultyId()).isEqualTo(second.getId());
        assertThat(row.getScenarios().get(ComplexityScenario.NEW_SCRIPT).getBoardRank()).isEqualTo(1);
        assertThat(row.getScenarios().get(ComplexityScenario.CURRENT).getBoardRank()).isEqualTo(2);
        assertThat(row.getScenarios().get(ComplexityScenario.OLD_SCRIPT).getBoardRank()).isNull();
        assertThat(row.getDeltas().get(ComplexityScenario.NEW_SCRIPT).getBoardRank()).isEqualTo(-1);
        assertThat(row.getDeltas().get(ComplexityScenario.OLD_SCRIPT).getBoardRank()).isNull();
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

        List<DifficultyRow> rows = service.highestAverageAp(ComplexityScenario.CURRENT, null, 10, 50);

        assertThat(rows).extracting(DifficultyRow::getMapDifficultyId).containsExactly(first.getId());
        assertThat(rows.get(0).getScenarios().get(ComplexityScenario.CURRENT).getBoardRank()).isEqualTo(1);
    }

    private static ScenarioState state(Map<UUID, MapAggregate> aggregates) {
        Map<UUID, Double> complexities = new java.util.HashMap<>();
        aggregates.forEach((id, a) -> complexities.put(id, a.complexity()));
        return new ScenarioState(complexities, Map.of(), Map.of(), Map.of(), aggregates, Map.of(),
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
