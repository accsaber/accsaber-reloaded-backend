package com.accsaber.backend.service.map;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.accsaber.backend.client.ComplexityModelClient;
import com.accsaber.backend.client.ComplexityModelClient.NoteAccuracies;
import com.accsaber.backend.config.ComplexityRaterProperties;
import com.accsaber.backend.model.dto.request.map.ComplexityRaterSpec;
import com.accsaber.backend.model.dto.request.map.ComplexityRaterSpec.Coefficients;
import com.accsaber.backend.model.entity.map.ComplexityEstimateSource;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.util.Rounding;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NoteAccuracyComplexityRater implements ComplexityRater {

    static final double[] WORST_BANDS = { 0.01, 0.02, 0.05, 0.10, 0.25 };
    private static final double ACCURACY_CEILING = 0.9999;
    private static final int MIN_NOTES = 8;

    private final MapZipCache zipCache;
    private final ComplexityModelClient modelClient;
    private final ComplexityRaterProperties properties;

    @Override
    public ComplexityEstimateSource source() {
        return ComplexityEstimateSource.NEW_SCRIPT;
    }

    @Override
    public String version() {
        return properties.getVersion();
    }

    @Override
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
        return Optional.of(rate(notes.get(), categoryCode));
    }

    @Override
    public Optional<Rating> reprice(MapDifficulty difficulty, JsonNode inputs, String currentModelHash) {
        if (difficulty.getCategory() == null || inputs == null || currentModelHash == null
                || !currentModelHash.equals(inputs.path("modelHash").asText(null))
                || !inputs.hasNonNull("meanNoteAccuracy") || !inputs.hasNonNull("worstBands")) {
            return Optional.empty();
        }
        return price(inputs, properties.toSpec(), difficulty.getCategory().getCode());
    }

    Rating rate(NoteAccuracies notes, String categoryCode) {
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
        inputs.put("notes", notes.getNotes());
        inputs.put("predictedNotes", sorted.size());
        inputs.put("meanNoteAccuracy", Rounding.round(mean, 6));
        inputs.put("worstBands", bands);
        ComplexityRaterSpec spec = properties.toSpec();
        Rating priced = price(mean, worstMean(sorted, spec.getWorstShare()), spec, categoryCode, inputs);
        return priced;
    }

    public static Optional<Rating> price(JsonNode inputs, ComplexityRaterSpec spec, String categoryCode) {
        Coefficients coefficients = spec.getCategories().get(categoryCode);
        if (coefficients == null || inputs == null || !inputs.hasNonNull("meanNoteAccuracy")) {
            return Optional.empty();
        }
        double mean = inputs.get("meanNoteAccuracy").asDouble();
        JsonNode bands = inputs.path("worstBands");
        String key = bandKey(nearestBand(spec.getWorstShare()));
        double worst = bands.hasNonNull(key) ? bands.get(key).asDouble() : inputs.path("worstNoteAccuracy").asDouble(mean);
        Map<String, Object> carried = new LinkedHashMap<>();
        inputs.fields().forEachRemaining(entry -> carried.put(entry.getKey(), entry.getValue()));
        return Optional.of(price(mean, worst, spec, categoryCode, carried));
    }

    private static Rating price(double mean, double worst, ComplexityRaterSpec spec, String categoryCode,
            Map<String, Object> inputs) {
        Coefficients coefficients = spec.getCategories().get(categoryCode);
        double meanTerm = linearised(mean);
        double worstTerm = linearised(worst);
        double complexity = coefficients.getIntercept()
                + coefficients.getMeanSlope() * meanTerm
                + coefficients.getWorstSlope() * worstTerm;
        inputs.put("worstShare", spec.getWorstShare());
        inputs.put("worstNoteAccuracy", Rounding.round(worst, 6));
        inputs.put("meanTerm", Rounding.round(meanTerm, 6));
        inputs.put("worstTerm", Rounding.round(worstTerm, 6));
        inputs.put("intercept", coefficients.getIntercept());
        inputs.put("meanSlope", coefficients.getMeanSlope());
        inputs.put("worstSlope", coefficients.getWorstSlope());
        return new Rating(Rounding.round(Math.max(0.0, complexity), 2), inputs);
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
        return String.format(java.util.Locale.ROOT, "%.2f", share);
    }

    static double linearised(double accuracy) {
        return -Math.log10(1.0 - Math.min(accuracy, ACCURACY_CEILING));
    }
}
