package com.accsaber.backend.service.map;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.accsaber.backend.client.ComplexityModelClient;
import com.accsaber.backend.client.ComplexityModelClient.NoteAccuracies;
import com.accsaber.backend.config.ComplexityRaterProperties;
import com.accsaber.backend.model.dto.request.map.ComplexityRaterSpec;
import com.accsaber.backend.model.dto.request.map.ComplexityRaterSpec.Coefficients;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.util.Rounding;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NoteAccuracyComplexityRater {

    private static final List<String> STORED_INPUTS = List.of("meanNoteAccuracy", "resetShare", "dotShare",
            "bottomUpShare", "crossoverShare", "topRowShare", "diagonalShare", "verticalStackShare", "outerShare",
            "njs");
    private static final double ACCURACY_CEILING = 0.9999;
    private static final int MIN_NOTES = 8;
    private static final int COMPLEXITY_SCALE = 1;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    public record Rating(double complexity, Map<String, Object> inputs) {
    }

    private record Terms(double mean, double reset, double dot, double bottomUp, double crossover, double topRow,
            double diagonal, double verticalStack, double outer, int notes, double njs) {
    }

    private final MapZipCache zipCache;
    private final ComplexityModelClient modelClient;
    private final ComplexityRaterProperties properties;
    private final ComplexityScenarioService scenarioService;

    public String version() {
        return properties.getVersion();
    }

    public Optional<Rating> rate(MapDifficulty difficulty) {
        if (difficulty.getCategory() == null || difficulty.getMap() == null || difficulty.getDifficulty() == null) {
            return Optional.empty();
        }
        String categoryCode = difficulty.getCategory().getCode();
        if (!properties.getCategories().containsKey(categoryCode)) {
            return Optional.empty();
        }
        Optional<byte[]> zip = zipCache.get(difficulty.getMap().getSongHash());
        if (zip.isEmpty()) {
            return Optional.empty();
        }
        Optional<NoteAccuracies> notes = modelClient.noteAccuracies(zip.get(), difficulty.getDifficulty().getDbValue(),
                difficulty.getCharacteristic());
        if (notes.isEmpty() || notes.get().getNoteAccuracies() == null
                || notes.get().getNoteAccuracies().size() < MIN_NOTES) {
            return Optional.empty();
        }
        return Optional.of(rate(notes.get(), categoryCode, reading(difficulty.getId())));
    }

    public Optional<Rating> reprice(MapDifficulty difficulty, JsonNode inputs, String currentModelHash) {
        if (difficulty.getCategory() == null || inputs == null || currentModelHash == null
                || !currentModelHash.equals(inputs.path("modelHash").asText(null))
                || !STORED_INPUTS.stream().allMatch(inputs::hasNonNull)) {
            return Optional.empty();
        }
        Map<String, Object> refreshed = JSON.convertValue(inputs, MAP);
        putReading(refreshed, reading(difficulty.getId()));
        return price(refreshed, properties, difficulty.getCategory().getCode());
    }

    private PayFairBoard.Reading reading(UUID difficultyId) {
        return scenarioService.payFair(properties.getPayFair()).getOrDefault(difficultyId, PayFairBoard.NONE);
    }

    private static void putReading(Map<String, Object> inputs, PayFairBoard.Reading reading) {
        inputs.put("payFair", Rounding.round(reading.complexity(), 3));
        inputs.put("payRatio", Rounding.round(reading.payRatio(), 4));
        inputs.put("payPlays", reading.plays());
        inputs.put("scores", reading.scores());
    }

    Rating rate(NoteAccuracies notes, String categoryCode, PayFairBoard.Reading reading) {
        double mean = notes.getNoteAccuracies().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("model", notes.getModel());
        inputs.put("modelHash", notes.getModelHash());
        inputs.put("mapVersion", notes.getMapVersion());
        inputs.put("njs", notes.getNjs());
        inputs.put("notes", notes.getNotes());
        inputs.put("predictedNotes", notes.getNoteAccuracies().size());
        inputs.put("meanNoteAccuracy", Rounding.round(mean, 6));
        inputs.put("resetShare", Rounding.round(notes.getResetShare(), 6));
        inputs.put("dotShare", Rounding.round(notes.getDotShare(), 6));
        inputs.put("bottomUpShare", Rounding.round(notes.getBottomUpShare(), 6));
        inputs.put("crossoverShare", Rounding.round(notes.getCrossoverShare(), 6));
        inputs.put("topRowShare", Rounding.round(notes.getTopRowShare(), 6));
        inputs.put("diagonalShare", Rounding.round(notes.getDiagonalShare(), 6));
        inputs.put("verticalStackShare", Rounding.round(notes.getVerticalStackShare(), 6));
        inputs.put("outerShare", Rounding.round(notes.getOuterShare(), 6));
        putReading(inputs, reading);
        return price(inputs, properties, categoryCode).orElseThrow();
    }

    public static Optional<Rating> price(JsonNode inputs, ComplexityRaterSpec spec, String categoryCode,
            PayFairBoard.Reading reading) {
        if (inputs == null || !inputs.isObject()) {
            return Optional.empty();
        }
        Map<String, Object> values = JSON.convertValue(inputs, MAP);
        if (reading != null) {
            putReading(values, reading);
        }
        return price(values, spec, categoryCode);
    }

    private static Optional<Rating> price(Map<String, Object> inputs, ComplexityRaterSpec spec, String categoryCode) {
        if (!spec.getCategories().containsKey(categoryCode) || !(inputs.get("meanNoteAccuracy") instanceof Number)) {
            return Optional.empty();
        }
        double mean = number(inputs, "meanNoteAccuracy", 0.0);
        Terms terms = new Terms(mean, number(inputs, "resetShare", 0.0), number(inputs, "dotShare", 0.0),
                number(inputs, "bottomUpShare", 0.0), number(inputs, "crossoverShare", 0.0),
                number(inputs, "topRowShare", 0.0), number(inputs, "diagonalShare", 0.0),
                number(inputs, "verticalStackShare", 0.0), number(inputs, "outerShare", 0.0),
                (int) number(inputs, "notes", 0), number(inputs, "njs", 0.0));
        Coefficients chart = spec.getCategories().get(categoryCode);
        double weight = payWeight((int) number(inputs, "payPlays", 0), (int) number(inputs, "scores", 0), spec.getPayFair());
        double botEstimate = line(chart, terms);
        double adjustment = weight * (number(inputs, "payFair", botEstimate) - botEstimate);
        double complexity = botEstimate + adjustment;
        Map<String, Object> out = new LinkedHashMap<>(inputs);
        out.put("meanTerm", Rounding.round(linearised(mean), 6));
        out.put("notesTerm", Rounding.round(Math.log(Math.max(1, terms.notes())), 6));
        out.put("botEstimate", Rounding.round(Math.max(0.0, botEstimate), COMPLEXITY_SCALE));
        out.put("payWeight", Rounding.round(weight, 4));
        out.put("payAdjustment", Rounding.round(adjustment, 2));
        out.put("chart", coefficients(chart));
        return Optional.of(new Rating(Rounding.round(Math.max(0.0, complexity), COMPLEXITY_SCALE), out));
    }

    private static double line(Coefficients c, Terms terms) {
        return c.getIntercept()
                + c.getMeanSlope() * linearised(terms.mean())
                + c.getResetSlope() * terms.reset()
                + c.getDotSlope() * terms.dot()
                + c.getBottomUpSlope() * terms.bottomUp()
                + c.getCrossoverSlope() * terms.crossover()
                + c.getTopRowSlope() * terms.topRow()
                + c.getDiagonalSlope() * terms.diagonal()
                + c.getVerticalStackSlope() * terms.verticalStack()
                + c.getOuterSlope() * terms.outer()
                + c.getNotesSlope() * Math.log(Math.max(1, terms.notes()))
                + c.getNjsSlope() * terms.njs();
    }

    static double payWeight(int plays, int scores, ComplexityRaterSpec.PayFair settings) {
        if (plays < settings.getMinPlays() || scores < settings.getMinScores()) {
            return 0.0;
        }
        int span = Math.max(1, settings.getFullScores() - settings.getMinScores());
        return settings.getMaxWeight() * Math.min(1.0, (scores - settings.getMinScores()) / (double) span);
    }

    private static double number(Map<String, Object> map, String key, double fallback) {
        return map.get(key) instanceof Number value ? value.doubleValue() : fallback;
    }

    private static Map<String, Object> coefficients(Coefficients c) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("intercept", c.getIntercept());
        out.put("meanSlope", c.getMeanSlope());
        out.put("resetSlope", c.getResetSlope());
        out.put("dotSlope", c.getDotSlope());
        out.put("bottomUpSlope", c.getBottomUpSlope());
        out.put("crossoverSlope", c.getCrossoverSlope());
        out.put("topRowSlope", c.getTopRowSlope());
        out.put("diagonalSlope", c.getDiagonalSlope());
        out.put("verticalStackSlope", c.getVerticalStackSlope());
        out.put("outerSlope", c.getOuterSlope());
        out.put("notesSlope", c.getNotesSlope());
        out.put("njsSlope", c.getNjsSlope());
        return out;
    }

    static double linearised(double accuracy) {
        return -Math.log10(1.0 - Math.min(accuracy, ACCURACY_CEILING));
    }
}
