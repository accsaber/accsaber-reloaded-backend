package com.accsaber.backend.service.map;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.accsaber.backend.client.BeatLeaderClient;
import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.map.ComplexityEstimateSource;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.repository.CurveRepository;
import com.accsaber.backend.service.score.APCalculationService;
import com.accsaber.backend.util.Rounding;
import com.fasterxml.jackson.databind.JsonNode;

@Service
public class AiAccComplexityRater implements ComplexityRater {

    private static final String COMPLEXITY_CURVE_NAME = "AI Complexity Curve";

    private final BeatLeaderClient beatLeaderClient;
    private final CurveRepository curveRepository;
    private final APCalculationService apCalculationService;
    private final double apTarget;
    private final double accuracyShift;
    private final double transformOffset;
    private final double transformScale;
    private final double transformBase;

    public AiAccComplexityRater(BeatLeaderClient beatLeaderClient, CurveRepository curveRepository,
            APCalculationService apCalculationService,
            @Value("${accsaber.complexity-estimate.ap-target}") double apTarget,
            @Value("${accsaber.complexity-estimate.accuracy-shift}") double accuracyShift,
            @Value("${accsaber.complexity-estimate.transform-offset}") double transformOffset,
            @Value("${accsaber.complexity-estimate.transform-scale}") double transformScale,
            @Value("${accsaber.complexity-estimate.transform-base}") double transformBase) {
        this.beatLeaderClient = beatLeaderClient;
        this.curveRepository = curveRepository;
        this.apCalculationService = apCalculationService;
        this.apTarget = apTarget;
        this.accuracyShift = accuracyShift;
        this.transformOffset = transformOffset;
        this.transformScale = transformScale;
        this.transformBase = transformBase;
    }

    @Override
    public ComplexityEstimateSource source() {
        return ComplexityEstimateSource.OLD_SCRIPT;
    }

    @Override
    public String version() {
        return "ai-acc-curve";
    }

    @Override
    public Optional<Rating> rate(MapDifficulty difficulty) {
        if (difficulty.getMap() == null || difficulty.getDifficulty() == null) {
            return Optional.empty();
        }
        return beatLeaderClient.getAiAccuracy(difficulty.getMap().getSongHash(), difficulty.getCharacteristic(),
                difficulty.getDifficulty().getNumericValue()).flatMap(this::price);
    }

    @Override
    public Optional<Rating> reprice(MapDifficulty difficulty, JsonNode inputs, String currentModelHash) {
        if (inputs == null || !inputs.hasNonNull("aiAccuracy")) {
            return Optional.empty();
        }
        return price(inputs.get("aiAccuracy").asDouble());
    }

    private Optional<Rating> price(double aiAcc) {
        Curve complexityCurve = curveRepository.findByNameAndActiveTrue(COMPLEXITY_CURVE_NAME).orElse(null);
        if (complexityCurve == null) {
            return Optional.empty();
        }
        double shiftedAccuracy = aiAcc + accuracyShift;
        double rawMultiplier = apCalculationService.interpolate(complexityCurve, shiftedAccuracy);
        double transformedMultiplier = transformMultiplier(rawMultiplier);
        if (transformedMultiplier <= 0) {
            return Optional.empty();
        }
        double complexity = apTarget / (transformedMultiplier * complexityCurve.getScale()) + complexityCurve.getShift();

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("aiAccuracy", aiAcc);
        inputs.put("shiftedAccuracy", shiftedAccuracy);
        inputs.put("rawMultiplier", rawMultiplier);
        inputs.put("transformedMultiplier", transformedMultiplier);
        inputs.put("apTarget", apTarget);
        return Optional.of(new Rating(Rounding.round(complexity, 1), inputs));
    }

    private double transformMultiplier(double multiplier) {
        if (multiplier == 0) {
            return 0;
        }
        return (multiplier - transformOffset) / (1 - transformOffset) * transformScale + transformBase;
    }
}
