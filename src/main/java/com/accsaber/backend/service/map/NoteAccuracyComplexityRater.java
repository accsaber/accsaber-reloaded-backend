package com.accsaber.backend.service.map;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.accsaber.backend.client.BeatSaverClient;
import com.accsaber.backend.client.ComplexityModelClient;
import com.accsaber.backend.client.ComplexityModelClient.NoteAccuracies;
import com.accsaber.backend.config.ComplexityRaterProperties;
import com.accsaber.backend.config.ComplexityRaterProperties.Coefficients;
import com.accsaber.backend.model.entity.map.ComplexityEstimateSource;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.util.Rounding;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NoteAccuracyComplexityRater implements ComplexityRater {

    private static final double ACCURACY_CEILING = 0.9999;
    private static final int MIN_NOTES = 8;

    private final BeatSaverClient beatSaverClient;
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
        Coefficients coefficients = properties.getCategories().get(difficulty.getCategory().getCode());
        if (coefficients == null) {
            return Optional.empty();
        }
        Optional<byte[]> zip = beatSaverClient.downloadMapZip(difficulty.getMap().getSongHash());
        if (zip.isEmpty()) {
            return Optional.empty();
        }
        Optional<NoteAccuracies> notes = modelClient.noteAccuracies(zip.get(), difficulty.getDifficulty().getDbValue(),
                difficulty.getCharacteristic());
        if (notes.isEmpty() || notes.get().getNoteAccuracies() == null
                || notes.get().getNoteAccuracies().size() < MIN_NOTES) {
            return Optional.empty();
        }
        return Optional.of(rate(notes.get(), coefficients));
    }

    Rating rate(NoteAccuracies notes, Coefficients coefficients) {
        List<Double> sorted = new ArrayList<>(notes.getNoteAccuracies());
        sorted.sort(null);
        double mean = sorted.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        int worstCount = Math.max(1, (int) Math.round(sorted.size() * properties.getWorstShare()));
        double worst = sorted.subList(0, worstCount).stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double meanTerm = linearised(mean);
        double worstTerm = linearised(worst);
        double complexity = coefficients.getIntercept()
                + coefficients.getMeanSlope() * meanTerm
                + coefficients.getWorstSlope() * worstTerm;

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("model", notes.getModel());
        inputs.put("modelHash", notes.getModelHash());
        inputs.put("mapVersion", notes.getMapVersion());
        inputs.put("notes", notes.getNotes());
        inputs.put("predictedNotes", sorted.size());
        inputs.put("meanNoteAccuracy", Rounding.round(mean, 6));
        inputs.put("worstNoteAccuracy", Rounding.round(worst, 6));
        inputs.put("worstShare", properties.getWorstShare());
        inputs.put("meanTerm", Rounding.round(meanTerm, 6));
        inputs.put("worstTerm", Rounding.round(worstTerm, 6));
        inputs.put("intercept", coefficients.getIntercept());
        inputs.put("meanSlope", coefficients.getMeanSlope());
        inputs.put("worstSlope", coefficients.getWorstSlope());
        return new Rating(Rounding.round(Math.max(0.0, complexity), 2), inputs);
    }

    static double linearised(double accuracy) {
        return -Math.log10(1.0 - Math.min(accuracy, ACCURACY_CEILING));
    }
}
