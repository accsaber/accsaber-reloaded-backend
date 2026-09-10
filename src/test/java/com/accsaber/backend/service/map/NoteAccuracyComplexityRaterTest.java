package com.accsaber.backend.service.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.client.BeatSaverClient;
import com.accsaber.backend.client.ComplexityModelClient;
import com.accsaber.backend.client.ComplexityModelClient.NoteAccuracies;
import com.accsaber.backend.config.ComplexityRaterProperties;
import com.accsaber.backend.config.ComplexityRaterProperties.Coefficients;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.ComplexityEstimateSource;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.map.MapDifficulty;

@ExtendWith(MockitoExtension.class)
class NoteAccuracyComplexityRaterTest {

    @Mock
    private BeatSaverClient beatSaverClient;
    @Mock
    private ComplexityModelClient modelClient;

    private ComplexityRaterProperties properties;
    private NoteAccuracyComplexityRater rater;

    @BeforeEach
    void setUp() {
        properties = new ComplexityRaterProperties();
        properties.setWorstShare(0.25);
        Coefficients coefficients = new Coefficients();
        coefficients.setIntercept(40.0);
        coefficients.setMeanSlope(-10.0);
        coefficients.setWorstSlope(-2.0);
        properties.getCategories().put("tech_acc", coefficients);
        rater = new NoteAccuracyComplexityRater(beatSaverClient, modelClient, properties);
    }

    @Test
    void identifiesAsTheNewScript() {
        assertThat(rater.source()).isEqualTo(ComplexityEstimateSource.NEW_SCRIPT);
        assertThat(rater.version()).isEqualTo(properties.getVersion());
    }

    @Test
    void linearisesAccuracyOnTheLogScale() {
        assertThat(NoteAccuracyComplexityRater.linearised(0.99)).isCloseTo(2.0, within(1e-9));
        assertThat(NoteAccuracyComplexityRater.linearised(0.999)).isCloseTo(3.0, within(1e-9));
        assertThat(NoteAccuracyComplexityRater.linearised(1.0)).isCloseTo(4.0, within(1e-9));
    }

    @Test
    void combinesTheMeanAndTheWorstSection() {
        List<Double> notes = List.of(0.99, 0.999, 0.999, 0.999, 0.999, 0.999, 0.999, 0.999);
        ComplexityRater.Rating rating = rater.rate(response(notes), properties.getCategories().get("tech_acc"));

        double mean = notes.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double expected = 40.0 - 10.0 * NoteAccuracyComplexityRater.linearised(mean)
                - 2.0 * NoteAccuracyComplexityRater.linearised(0.9945);
        assertThat(rating.complexity()).isCloseTo(expected, within(0.01));
        assertThat(rating.inputs()).containsEntry("predictedNotes", 8).containsEntry("model", "note-acc-beatleader")
                .containsEntry("worstShare", 0.25);
        assertThat((Double) rating.inputs().get("worstNoteAccuracy")).isCloseTo(0.9945, within(1e-6));
    }

    @Test
    void neverGoesBelowZero() {
        List<Double> notes = List.of(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0);
        ComplexityRater.Rating rating = rater.rate(response(notes), properties.getCategories().get("tech_acc"));
        assertThat(rating.complexity()).isZero();
    }

    @Test
    void skipsCategoriesWithoutCoefficientsBeforeDownloadingAnything() {
        assertThat(rater.rate(difficulty("true_acc"))).isEmpty();
        verify(beatSaverClient, never()).downloadMapZip(anyString());
    }

    @Test
    void skipsMapsWithTooFewPredictedNotes() {
        MapDifficulty tech = difficulty("tech_acc");
        when(beatSaverClient.downloadMapZip("abc")).thenReturn(Optional.of(new byte[] { 1 }));
        when(modelClient.noteAccuracies(any(), anyString(), anyString()))
                .thenReturn(Optional.of(response(List.of(0.99, 0.99))));

        assertThat(rater.rate(tech)).isEmpty();
    }

    @Test
    void sendsTheDifficultyNameAndCharacteristicTheModelExpects() {
        MapDifficulty tech = difficulty("tech_acc");
        when(beatSaverClient.downloadMapZip("abc")).thenReturn(Optional.of(new byte[] { 1 }));
        when(modelClient.noteAccuracies(any(), anyString(), anyString()))
                .thenReturn(Optional.of(response(List.of(0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99))));

        assertThat(rater.rate(tech)).isPresent();
        verify(modelClient).noteAccuracies(any(), org.mockito.ArgumentMatchers.eq("Expert"),
                org.mockito.ArgumentMatchers.eq("Standard"));
    }

    private static NoteAccuracies response(List<Double> notes) {
        NoteAccuracies response = new NoteAccuracies();
        response.setModel("note-acc-beatleader");
        response.setMapVersion("3");
        response.setNotes(notes.size());
        response.setPredictedNotes(notes.size());
        response.setNoteAccuracies(notes);
        return response;
    }

    private static MapDifficulty difficulty(String categoryCode) {
        return MapDifficulty.builder()
                .id(UUID.randomUUID())
                .difficulty(Difficulty.EXPERT)
                .characteristic("Standard")
                .category(Category.builder().id(UUID.randomUUID()).code(categoryCode).build())
                .map(Map.builder().songHash("abc").songName("Song").build())
                .build();
    }
}
