package com.accsaber.backend.service.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.dto.APResult;
import com.accsaber.backend.model.dto.projection.ActiveComplexityRow;
import com.accsaber.backend.model.dto.projection.SimulationScoreRow;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.map.MapDifficultyComplexityEstimateRepository;
import com.accsaber.backend.repository.map.MapDifficultyComplexityRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.service.map.ComplexityScenarioService.ScenarioState;
import com.accsaber.backend.service.score.APCalculationService;

@ExtendWith(MockitoExtension.class)
class ComplexityScenarioServiceTest {

    @Mock
    private ScoreRepository scoreRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private MapDifficultyComplexityRepository complexityRepository;
    @Mock
    private MapDifficultyComplexityEstimateRepository estimateRepository;
    @Mock
    private APCalculationService apCalculationService;

    @InjectMocks
    private ComplexityScenarioService service;

    private final UUID categoryId = UUID.randomUUID();
    private final UUID easyMap = UUID.randomUUID();
    private final UUID hardMap = UUID.randomUUID();
    private final Curve scoreCurve = Curve.builder().id(UUID.randomUUID()).build();
    private final Curve weightCurve = Curve.builder().id(UUID.randomUUID()).build();

    private void stubScores() {
        Category category = Category.builder().id(categoryId).code("tech_acc").countForOverall(true)
                .scoreCurve(scoreCurve).weightCurve(weightCurve).build();
        when(categoryRepository.findByActiveTrue()).thenReturn(List.of(category));
        when(scoreRepository.findActiveRowsByDifficultyStatus(MapDifficultyStatus.RANKED)).thenReturn(List.of(
                new SimulationScoreRow(1L, easyMap, categoryId, 950_000, 1_000_000, 900.0),
                new SimulationScoreRow(1L, hardMap, categoryId, 900_000, 1_000_000, 800.0),
                new SimulationScoreRow(2L, easyMap, categoryId, 850_000, 1_000_000, 850.0),
                new SimulationScoreRow(2L, hardMap, categoryId, 960_000, 1_000_000, 950.0)));
    }

    private void stubPool() {
        stubScores();
        when(apCalculationService.calculateRawAP(anyDouble(), anyDouble(), eq(scoreCurve)))
                .thenAnswer(inv -> new APResult(inv.getArgument(0, Double.class) * inv.getArgument(1, Double.class) * 100.0, 0.5));
        when(apCalculationService.calculateWeightedAP(anyDouble(), anyInt(), eq(weightCurve)))
                .thenAnswer(inv -> inv.getArgument(1, Integer.class) == 0
                        ? inv.getArgument(0, Double.class)
                        : inv.getArgument(0, Double.class) * 0.5);
    }

    @Test
    void pricesEveryPlayFromTheScenarioComplexity() {
        stubPool();
        ScenarioState state = service.evaluate(Map.of(easyMap, 8.0, hardMap, 10.0));

        assertThat(state.totalsByCategory().get(categoryId).get(1L).ap()).isEqualTo(1280.0);
        assertThat(state.totalsByCategory().get(categoryId).get(2L).ap()).isEqualTo(1300.0);
        assertThat(state.totalsByCategory().get(categoryId).get(2L).rank()).isEqualTo(1);
        assertThat(state.totalsByCategory().get(categoryId).get(1L).rank()).isEqualTo(2);
        assertThat(state.overallTotals().get(2L).rank()).isEqualTo(1);
        assertThat(state.aggregates().get(easyMap).topAp()).isEqualTo(760.0);
        assertThat(state.aggregates().get(easyMap).scores()).isEqualTo(2);
        assertThat(state.playsByDifficulty().get(hardMap).get(0).userId()).isEqualTo(2L);
        assertThat(state.playsByDifficulty().get(hardMap).get(0).rank()).isEqualTo(1);
        assertThat(state.ladders().get(categoryId).playersWith900()).isEqualTo(2);
        assertThat(state.ladders().get(categoryId).playersWith1000()).isZero();
    }

    @Test
    void scenarioOverridesOnlyTheMapsThatHaveAnEstimate() {
        when(complexityRepository.findActiveRowsByDifficultyStatus(MapDifficultyStatus.RANKED))
                .thenReturn(List.of(new ActiveComplexityRow(easyMap, 8.0), new ActiveComplexityRow(hardMap, 10.0)));
        when(estimateRepository.findRows())
                .thenReturn(List.of(new ActiveComplexityRow(easyMap, 5.0), new ActiveComplexityRow(UUID.randomUUID(), 3.0)));

        Map<UUID, Double> complexities = service.complexitiesFor(ComplexityScenario.NEW_SCRIPT);

        assertThat(complexities).containsEntry(easyMap, 5.0).containsEntry(hardMap, 10.0).hasSize(2);
    }

    @Test
    void boardEaseFactorsOutPlayerSkillAndGatesOnPlayCount() {
        stubScores();

        Map<UUID, ComplexityScenarioService.BoardEase> ease = service.boardEase(2, 100);

        double expectedGap = ((-Math.log10(0.05) + Math.log10(0.10)) + (-Math.log10(0.15) + Math.log10(0.04))) / 2;
        assertThat(ease.get(easyMap).ease() - ease.get(hardMap).ease()).isCloseTo(expectedGap, org.assertj.core.api.Assertions.within(1e-6));
        assertThat(ease.get(easyMap).ease() + ease.get(hardMap).ease()).isCloseTo(0.0, org.assertj.core.api.Assertions.within(1e-9));
        assertThat(ease.get(easyMap).players()).isEqualTo(2);
        assertThat(ease.get(easyMap).scores()).isEqualTo(2);
        assertThat(service.boardEase(3, 100)).isEmpty();
    }

    @Test
    void thePlayGateCountsPlaysInThatCategoryOnly() {
        UUID trueCategory = UUID.randomUUID();
        UUID trueMap = UUID.randomUUID();
        UUID otherTrueMap = UUID.randomUUID();
        when(scoreRepository.findActiveRowsByDifficultyStatus(MapDifficultyStatus.RANKED)).thenReturn(List.of(
                new SimulationScoreRow(1L, easyMap, categoryId, 950_000, 1_000_000, 900.0),
                new SimulationScoreRow(1L, hardMap, categoryId, 900_000, 1_000_000, 800.0),
                new SimulationScoreRow(2L, easyMap, categoryId, 850_000, 1_000_000, 850.0),
                new SimulationScoreRow(2L, hardMap, categoryId, 960_000, 1_000_000, 950.0),
                new SimulationScoreRow(1L, trueMap, trueCategory, 990_000, 1_000_000, 900.0),
                new SimulationScoreRow(3L, trueMap, trueCategory, 980_000, 1_000_000, 850.0),
                new SimulationScoreRow(3L, otherTrueMap, trueCategory, 985_000, 1_000_000, 850.0)));
        when(categoryRepository.findByActiveTrue()).thenReturn(List.of());

        Map<UUID, ComplexityScenarioService.BoardEase> ease = service.boardEase(2, 100);

        assertThat(ease.get(easyMap).players()).isEqualTo(2);
        assertThat(ease.get(trueMap).players()).isEqualTo(1);
        assertThat(ease.get(trueMap).scores()).isEqualTo(2);
        assertThat(ease.get(otherTrueMap).players()).isEqualTo(1);
    }

    @Test
    void theEaseIsReadFromTheMapsBestPlaysOnly() {
        stubScores();

        Map<UUID, ComplexityScenarioService.BoardEase> ease = service.boardEase(2, 1);

        assertThat(ease.get(easyMap).players()).isEqualTo(1);
        assertThat(ease.get(hardMap).players()).isEqualTo(1);
        double skill1 = (-Math.log10(0.05) - Math.log10(0.10)) / 2;
        double skill2 = (-Math.log10(0.15) - Math.log10(0.04)) / 2;
        double easyBest = -Math.log10(0.05) - skill1;
        double hardBest = -Math.log10(0.04) - skill2;
        assertThat(ease.get(easyMap).ease() - ease.get(hardMap).ease()).isCloseTo(easyBest - hardBest, org.assertj.core.api.Assertions.within(1e-6));
        assertThat(ease.get(easyMap).ease() + ease.get(hardMap).ease()).isCloseTo(0.0, org.assertj.core.api.Assertions.within(1e-9));
    }

    @Test
    void cachesStatesUntilEvicted() {
        stubPool();
        when(complexityRepository.findActiveRowsByDifficultyStatus(MapDifficultyStatus.RANKED))
                .thenReturn(List.of(new ActiveComplexityRow(easyMap, 8.0), new ActiveComplexityRow(hardMap, 10.0)));

        ScenarioState first = service.state(ComplexityScenario.CURRENT);
        ScenarioState again = service.state(ComplexityScenario.CURRENT);
        service.evict();
        ScenarioState rebuilt = service.state(ComplexityScenario.CURRENT);

        assertThat(again).isSameAs(first);
        assertThat(rebuilt).isNotSameAs(first);
    }
}
