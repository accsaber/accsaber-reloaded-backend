package com.accsaber.backend.service.map;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
import com.accsaber.backend.service.map.ComplexityScenarioService.BoardEase;
import com.accsaber.backend.util.Rounding;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NoteAccuracyComplexityRater {

    static final double[] WORST_BANDS = { 0.01, 0.02, 0.05, 0.10, 0.25 };
    static final BoardEase NO_BOARD = new BoardEase(0.0, 0, 0);
    private static final double ACCURACY_CEILING = 0.9999;
    private static final int MIN_NOTES = 8;
    private static final int COMPLEXITY_SCALE = 1;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    public record Rating(double complexity, Map<String, Object> inputs) {
    }

    private record Terms(double mean, double worst, double reset, double dot, int notes, double nps, double njs,
            BoardEase board) {
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
        return Optional.of(rate(notes.get(), categoryCode, board(difficulty.getId()), duration(difficulty)));
    }

    public Optional<Rating> reprice(MapDifficulty difficulty, JsonNode inputs, String currentModelHash) {
        if (difficulty.getCategory() == null || inputs == null || currentModelHash == null
                || !currentModelHash.equals(inputs.path("modelHash").asText(null))
                || !inputs.hasNonNull("meanNoteAccuracy") || !inputs.hasNonNull("worstBands")
                || !inputs.hasNonNull("resetShare") || !inputs.hasNonNull("dotShare") || !inputs.hasNonNull("njs")) {
            return Optional.empty();
        }
        Map<String, Object> refreshed = JSON.convertValue(inputs, MAP);
        refreshed.put("duration", duration(difficulty));
        putBoard(refreshed, board(difficulty.getId()));
        return price(refreshed, properties.toSpec(), difficulty.getCategory().getCode());
    }

    private static Integer duration(MapDifficulty difficulty) {
        return difficulty.getMetadata() == null ? null : difficulty.getMetadata().getDuration();
    }

    private BoardEase board(UUID difficultyId) {
        return scenarioService.boardEase(properties.getBoard().getMinPlayerPlays(), properties.getBoard().getTopPlays())
                .getOrDefault(difficultyId, NO_BOARD);
    }

    private static void putBoard(Map<String, Object> inputs, BoardEase board) {
        inputs.put("boardEase", Rounding.round(board.ease(), 6));
        inputs.put("boardPlayers", board.players());
        inputs.put("scores", board.scores());
    }

    Rating rate(NoteAccuracies notes, String categoryCode, BoardEase board, Integer duration) {
        List<Double> sorted = new ArrayList<>(notes.getNoteAccuracies());
        sorted.sort(null);
        double mean = sorted.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        Map<String, Object> bands = new LinkedHashMap<>();
        for (double share : WORST_BANDS) {
            bands.put(bandKey(share), Rounding.round(worstMean(sorted, share), 6));
        }
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("model", notes.getModel());
        inputs.put("modelHash", notes.getModelHash());
        inputs.put("mapVersion", notes.getMapVersion());
        inputs.put("njs", notes.getNjs());
        inputs.put("notes", notes.getNotes());
        inputs.put("duration", duration);
        inputs.put("predictedNotes", sorted.size());
        inputs.put("meanNoteAccuracy", Rounding.round(mean, 6));
        inputs.put("worstBands", bands);
        inputs.put("resetShare", Rounding.round(notes.getResetShare(), 6));
        inputs.put("dotShare", Rounding.round(notes.getDotShare(), 6));
        putBoard(inputs, board);
        return price(inputs, properties.toSpec(), categoryCode).orElseThrow();
    }

    public static Optional<Rating> price(JsonNode inputs, ComplexityRaterSpec spec, String categoryCode,
            BoardEase board) {
        if (inputs == null || !inputs.isObject()) {
            return Optional.empty();
        }
        Map<String, Object> values = JSON.convertValue(inputs, MAP);
        if (board != null) {
            putBoard(values, board);
        }
        return price(values, spec, categoryCode);
    }

    private static Optional<Rating> price(Map<String, Object> inputs, ComplexityRaterSpec spec, String categoryCode) {
        if (!spec.getCategories().containsKey(categoryCode) || !(inputs.get("meanNoteAccuracy") instanceof Number)) {
            return Optional.empty();
        }
        double mean = number(inputs, "meanNoteAccuracy", 0.0);
        Map<String, Object> bands = inputs.get("worstBands") instanceof Map<?, ?> stored
                ? JSON.convertValue(stored, MAP) : Map.of();
        double worst = number(bands, bandKey(nearestBand(spec.getWorstShare())), number(inputs, "worstNoteAccuracy", mean));
        BoardEase board = new BoardEase(number(inputs, "boardEase", 0.0), (int) number(inputs, "boardPlayers", 0),
                (int) number(inputs, "scores", 0));
        int notes = (int) number(inputs, "notes", 0);
        double duration = number(inputs, "duration", 0.0);
        Terms terms = new Terms(mean, worst, number(inputs, "resetShare", 0.0), number(inputs, "dotShare", 0.0),
                notes, duration > 0.0 ? notes / duration : 0.0, number(inputs, "njs", 0.0), board);
        Coefficients chart = spec.getCategories().get(categoryCode);
        Coefficients boardLine = spec.getBoardCategories().get(categoryCode);
        double weight = boardLine == null ? 0.0 : boardWeight(board, spec.getBoard());
        double chartComplexity = line(chart, terms);
        double complexity = chartComplexity + nudge(weight, chartComplexity, boardLine, terms, spec.getBoard());
        Map<String, Object> out = new LinkedHashMap<>(inputs);
        out.put("worstShare", spec.getWorstShare());
        out.put("worstNoteAccuracy", Rounding.round(worst, 6));
        out.put("meanTerm", Rounding.round(linearised(mean), 6));
        out.put("worstTerm", Rounding.round(linearised(worst), 6));
        out.put("notesTerm", Rounding.round(Math.log(Math.max(1, terms.notes())), 6));
        out.put("npsTerm", terms.nps() <= 0.0 ? null : Rounding.round(Math.log(terms.nps()), 6));
        out.put("chartComplexity", Rounding.round(Math.max(0.0, chartComplexity), COMPLEXITY_SCALE));
        out.put("boardWeight", Rounding.round(weight, 4));
        out.put("boardComplexity", weight == 0.0 ? null : Rounding.round(Math.max(0.0, line(boardLine, terms)), COMPLEXITY_SCALE));
        out.put("chart", coefficients(chart));
        out.put("board", boardLine == null ? null : coefficients(boardLine));
        return Optional.of(new Rating(Rounding.round(Math.max(0.0, complexity), COMPLEXITY_SCALE), out));
    }

    private static double line(Coefficients c, Terms terms) {
        return c.getIntercept()
                + c.getMeanSlope() * linearised(terms.mean())
                + c.getWorstSlope() * linearised(terms.worst())
                + c.getResetSlope() * terms.reset()
                + c.getDotSlope() * terms.dot()
                + c.getNotesSlope() * Math.log(Math.max(1, terms.notes()))
                + (terms.nps() <= 0.0 ? 0.0 : c.getNpsSlope() * Math.log(terms.nps()))
                + c.getNjsSlope() * terms.njs()
                + c.getBoardSlope() * terms.board().ease();
    }

    private static double nudge(double weight, double chartComplexity, Coefficients boardLine, Terms terms,
            ComplexityRaterSpec.Board gate) {
        if (weight == 0.0) {
            return 0.0;
        }
        double move = weight * (line(boardLine, terms) - chartComplexity);
        if (gate.getMaxNudge() <= 0.0) {
            return move;
        }
        return Math.max(-gate.getMaxNudge(), Math.min(gate.getMaxNudge(), move));
    }

    static double boardWeight(BoardEase board, ComplexityRaterSpec.Board gate) {
        if (board.players() < gate.getMinPlayers() || board.scores() < gate.getMinScores()) {
            return 0.0;
        }
        int span = Math.max(1, gate.getFullScores() - gate.getMinScores());
        return Math.min(1.0, (board.scores() - gate.getMinScores()) / (double) span);
    }

    private static double number(Map<String, Object> map, String key, double fallback) {
        return map.get(key) instanceof Number value ? value.doubleValue() : fallback;
    }

    private static Map<String, Object> coefficients(Coefficients c) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("intercept", c.getIntercept());
        out.put("meanSlope", c.getMeanSlope());
        out.put("worstSlope", c.getWorstSlope());
        out.put("resetSlope", c.getResetSlope());
        out.put("dotSlope", c.getDotSlope());
        out.put("notesSlope", c.getNotesSlope());
        out.put("npsSlope", c.getNpsSlope());
        out.put("njsSlope", c.getNjsSlope());
        out.put("boardSlope", c.getBoardSlope());
        return out;
    }

    static double worstMean(List<Double> sortedAscending, double share) {
        int count = Math.max(1, (int) Math.round(sortedAscending.size() * share));
        return sortedAscending.subList(0, Math.min(count, sortedAscending.size())).stream()
                .mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    static double nearestBand(double share) {
        double best = WORST_BANDS[0];
        for (double band : WORST_BANDS) {
            if (Math.abs(band - share) < Math.abs(best - share)) {
                best = band;
            }
        }
        return best;
    }

    static String bandKey(double share) {
        return String.format(Locale.ROOT, "%.2f", share);
    }

    static double linearised(double accuracy) {
        return -Math.log10(1.0 - Math.min(accuracy, ACCURACY_CEILING));
    }
}
