package com.accsaber.backend.service.mission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.mission.MissionBand;
import com.accsaber.backend.model.entity.score.Score;

class MissionBuilderServiceTest {

    private MissionSkillService skillService;
    private MissionBuilderService service;
    private MissionAssignmentContext ctx;
    private Category category;
    private Curve scoreCurve;

    @BeforeEach
    void setUp() {
        skillService = mock(MissionSkillService.class);
        service = new MissionBuilderService(null, null, null, null, skillService);
        category = Category.builder().id(UUID.randomUUID()).build();
        scoreCurve = Curve.builder().scale(1.0).shift(0.0).build();
        ctx = new MissionAssignmentContext(42L, List.of(), Map.of(), Map.of(), 0.0);
    }

    @ParameterizedTest(name = "{0} scores at {1} -> {2}")
    @CsvSource({
            "150, 0.70, 70",
            "500, 0.45, 45",
            "900, 0.22, 22",
            "150, 0.10, 10",
            "101, 0.10, 10",
            "100, 0.10, 10",
            "100, 0.70, 70",
            "80, 0.45, 36",
            "60, 0.22, 13",
            "50, 0.10, 5"
    })
    void pbAboveThresholdAnchorIndexCapsThePoolAtOneHundredScores(int scoreCount, double percentile,
            int expected) {
        assertThat(service.pbAboveThresholdAnchorIndex(scoreCount, percentile)).isEqualTo(expected);
    }

    @Test
    void pbAboveThresholdAvailabilityCapUsesAFixedDepthRegardlessOfScoreCount() {
        assertThat(service.pbAboveThresholdAvailabilityCap(descendingScores(60, 1200), MissionBand.hard, 1500.0))
                .isEqualTo(1186.0);
        assertThat(service.pbAboveThresholdAvailabilityCap(descendingScores(400, 1200), MissionBand.hard, 1500.0))
                .isEqualTo(1186.0);
        assertThat(service.pbAboveThresholdAvailabilityCap(descendingScores(400, 1200), MissionBand.extreme, 1500.0))
                .isEqualTo(1191.0);
    }

    @Test
    void pbAboveThresholdAvailabilityCapKeepsTheThresholdWhenItIsAlreadyLower() {
        assertThat(service.pbAboveThresholdAvailabilityCap(descendingScores(400, 1200), MissionBand.hard, 900.0))
                .isEqualTo(900.0);
    }

    @Test
    void pbAboveThresholdAvailabilityCapDoesNotApplyToLowBandsOrSmallScoreSets() {
        assertThat(service.pbAboveThresholdAvailabilityCap(descendingScores(400, 1200), MissionBand.easy, 1500.0))
                .isEqualTo(1500.0);
        assertThat(service.pbAboveThresholdAvailabilityCap(descendingScores(40, 1200), MissionBand.hard, 1500.0))
                .isEqualTo(1500.0);
    }

    @Test
    void complexityCapScalesTheRepresentativeAccuracyByThePickedMapComplexity() {
        stubRepresentativeNormalizedAp(80.0);

        Double atTopOfBand = service.applyComplexityAwareScoreTargetCap(ctx, category, scoreCurve,
                pick(12.9), MissionBand.medium, 5000.0);
        Double atBottomOfBand = service.applyComplexityAwareScoreTargetCap(ctx, category, scoreCurve,
                pick(10.1), MissionBand.medium, 5000.0);

        assertThat(atTopOfBand).isEqualTo(80.0 * 12.9 * 1.06);
        assertThat(atBottomOfBand).isEqualTo(80.0 * 10.1 * 1.06);
    }

    @Test
    void complexityCapAccountsForTheCurveShift() {
        stubRepresentativeNormalizedAp(80.0);
        Curve shifted = Curve.builder().scale(1.0).shift(2.0).build();

        Double capped = service.applyComplexityAwareScoreTargetCap(ctx, category, shifted,
                pick(12.0), MissionBand.medium, 5000.0);

        assertThat(capped).isEqualTo(80.0 * 10.0 * 1.06);
    }

    @Test
    void complexityCapLeavesTargetsBelowTheCapAlone() {
        stubRepresentativeNormalizedAp(80.0);

        Double capped = service.applyComplexityAwareScoreTargetCap(ctx, category, scoreCurve,
                pick(11.2), MissionBand.medium, 500.0);

        assertThat(capped).isEqualTo(500.0);
    }

    @Test
    void complexityCapLeavesTargetAloneWhenThePlayerHasNoUsableHistory() {
        stubRepresentativeNormalizedAp(null);

        Double capped = service.applyComplexityAwareScoreTargetCap(ctx, category, scoreCurve,
                pick(11.2), MissionBand.medium, 1010.0);

        assertThat(capped).isEqualTo(1010.0);
    }

    @Test
    void complexityCapWidensByBandAndSamplesEachBandOnlyOncePerUser() {
        stubRepresentativeNormalizedAp(80.0);

        service.applyComplexityAwareScoreTargetCap(ctx, category, scoreCurve, pick(10.1), MissionBand.easy, 5000.0);
        service.applyComplexityAwareScoreTargetCap(ctx, category, scoreCurve, pick(12.9), MissionBand.hard, 5000.0);
        service.applyComplexityAwareScoreTargetCap(ctx, category, scoreCurve, pick(7.5), MissionBand.hard, 5000.0);

        verify(skillService, times(1)).representativeNormalizedApForComplexityBand(
                eq(42L), eq(category.getId()), eq(0.0), eq(10.0), eq(13.0));
        verify(skillService, times(1)).representativeNormalizedApForComplexityBand(
                eq(42L), eq(category.getId()), eq(0.0), eq(7.0), eq(10.0));
    }

    private void stubRepresentativeNormalizedAp(Double value) {
        when(skillService.representativeNormalizedApForComplexityBand(
                anyLong(), any(UUID.class), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(value);
    }

    private MapPick pick(double complexity) {
        return new MapPick(MapDifficulty.builder().id(UUID.randomUUID()).build(), complexity, 1000);
    }

    private List<Score> descendingScores(int count, int topAp) {
        List<Score> scores = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            scores.add(Score.builder().ap(topAp - i).build());
        }
        return scores;
    }
}
