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

import com.accsaber.backend.client.ComplexityModelClient;
import com.accsaber.backend.client.ComplexityModelClient.NoteAccuracies;
import com.accsaber.backend.config.ComplexityRaterProperties;
import com.accsaber.backend.model.dto.request.map.ComplexityRaterSpec.Coefficients;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class NoteAccuracyComplexityRaterTest {

    @Mock
    private MapZipCache zipCache;
    @Mock
    private ComplexityModelClient modelClient;
    @Mock
    private ComplexityScenarioService scenarioService;

    private ComplexityRaterProperties properties;
    private NoteAccuracyComplexityRater rater;

    @BeforeEach
    void setUp() {
        properties = new ComplexityRaterProperties();
        Coefficients coefficients = new Coefficients();
        coefficients.setIntercept(40.0);
        coefficients.setMeanSlope(-10.0);
        properties.getCategories().put("tech_acc", coefficients);
        rater = new NoteAccuracyComplexityRater(zipCache, modelClient, properties, scenarioService);
    }

    @Test
    void linearisesAccuracyOnTheLogScale() {
        assertThat(NoteAccuracyComplexityRater.linearised(0.99)).isCloseTo(2.0, within(1e-9));
        assertThat(NoteAccuracyComplexityRater.linearised(0.999)).isCloseTo(3.0, within(1e-9));
        assertThat(NoteAccuracyComplexityRater.linearised(1.0)).isCloseTo(4.0, within(1e-9));
    }

    @Test
    void ratesFromTheMeanNoteAccuracy() {
        List<Double> notes = List.of(0.99, 0.999, 0.999, 0.999, 0.999, 0.999, 0.999, 0.999);
        NoteAccuracyComplexityRater.Rating rating = rater.rate(response(notes), "tech_acc", PayFairBoard.NONE);

        double mean = notes.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double expected = 40.0 - 10.0 * NoteAccuracyComplexityRater.linearised(mean);
        assertThat(rating.complexity()).isCloseTo(expected, within(0.051));
        assertThat(rating.complexity() * 10).isEqualTo(Math.rint(rating.complexity() * 10));
        assertThat(rating.inputs()).containsEntry("predictedNotes", 8).containsEntry("model", "note-acc-beatleader");
    }

    @Test
    void repricesStoredInputsOnlyWhenTheModelHashStillMatches() {
        NoteAccuracies response = response(List.of(0.99, 0.999, 0.999, 0.999, 0.999, 0.999, 0.999, 0.999));
        response.setModelHash("abc123");
        NoteAccuracyComplexityRater.Rating stored = rater.rate(response, "tech_acc", PayFairBoard.NONE);
        JsonNode inputs = new ObjectMapper().valueToTree(stored.inputs());
        MapDifficulty tech = difficulty("tech_acc");

        assertThat(rater.reprice(tech, inputs, "other")).isEmpty();
        assertThat(rater.reprice(tech, inputs, null)).isEmpty();

        properties.getCategories().get("tech_acc").setIntercept(45.0);
        Optional<NoteAccuracyComplexityRater.Rating> repriced = rater.reprice(tech, inputs, "abc123");
        assertThat(repriced).isPresent();
        assertThat(repriced.get().complexity()).isCloseTo(stored.complexity() + 5.0, within(0.051));
        assertThat(chart(repriced.get())).containsEntry("intercept", 45.0);
        assertThat(NoteAccuracyComplexityRater.price(inputs, properties, "true_acc", null)).isEmpty();
        verify(modelClient, never()).noteAccuracies(any(), anyString(), anyString());
    }

    @Test
    void resetAndDotSharesPriceThroughTheirOwnSlopes() {
        properties.getCategories().get("tech_acc").setResetSlope(-2.0);
        properties.getCategories().get("tech_acc").setDotSlope(-4.0);
        NoteAccuracies response = response(List.of(0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99));
        NoteAccuracyComplexityRater.Rating plain = rater.rate(response, "tech_acc", PayFairBoard.NONE);
        response.setResetShare(1.0);
        response.setDotShare(0.5);
        NoteAccuracyComplexityRater.Rating reset = rater.rate(response, "tech_acc", PayFairBoard.NONE);

        assertThat(reset.complexity()).isCloseTo(plain.complexity() - 4.0, within(0.051));
        assertThat(reset.inputs()).containsEntry("resetShare", 1.0).containsEntry("dotShare", 0.5);
        assertThat(chart(reset)).containsEntry("resetSlope", -2.0).containsEntry("dotSlope", -4.0);
    }

    @Test
    void bottomUpSharePricesThroughItsOwnSlopeAndLeavesCleanMapsAlone() {
        properties.getCategories().get("tech_acc").setBottomUpSlope(-4.0);
        NoteAccuracies response = response(List.of(0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99));
        NoteAccuracyComplexityRater.Rating clean = rater.rate(response, "tech_acc", PayFairBoard.NONE);
        response.setBottomUpShare(0.15);
        NoteAccuracyComplexityRater.Rating rated = rater.rate(response, "tech_acc", PayFairBoard.NONE);

        assertThat(clean.inputs()).containsEntry("bottomUpShare", 0.0);
        assertThat(rated.complexity()).isCloseTo(clean.complexity() - 0.6, within(0.051));
        assertThat(rated.inputs()).containsEntry("bottomUpShare", 0.15);
        assertThat(chart(rated)).containsEntry("bottomUpSlope", -4.0);
    }

    @Test
    void positionAndStackSharesPriceThroughTheirOwnSlopes() {
        Coefficients line = properties.getCategories().get("tech_acc");
        line.setCrossoverSlope(2.0);
        line.setTopRowSlope(1.0);
        line.setDiagonalSlope(1.0);
        line.setVerticalStackSlope(2.0);
        line.setOuterSlope(-2.0);
        NoteAccuracies response = response(List.of(0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99));
        NoteAccuracyComplexityRater.Rating clean = rater.rate(response, "tech_acc", PayFairBoard.NONE);
        response.setCrossoverShare(0.5);
        response.setTopRowShare(0.5);
        response.setDiagonalShare(0.5);
        response.setVerticalStackShare(0.5);
        response.setOuterShare(0.5);
        NoteAccuracyComplexityRater.Rating rated = rater.rate(response, "tech_acc", PayFairBoard.NONE);

        assertThat(rated.complexity()).isCloseTo(clean.complexity() + 2.0, within(0.051));
        assertThat(rated.inputs()).containsEntry("crossoverShare", 0.5).containsEntry("outerShare", 0.5);
        assertThat(chart(rated)).containsEntry("verticalStackSlope", 2.0).containsEntry("outerSlope", -2.0);
    }

    @Test
    void noteCountAndNjsPriceThroughTheirOwnSlopes() {
        properties.getCategories().get("tech_acc").setNotesSlope(1.0);
        properties.getCategories().get("tech_acc").setNjsSlope(-0.5);
        NoteAccuracies response = response(List.of(0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99));
        NoteAccuracyComplexityRater.Rating plain = rater.rate(response, "tech_acc", PayFairBoard.NONE);
        response.setNotes(59);
        response.setNjs(4.0);
        NoteAccuracyComplexityRater.Rating priced = rater.rate(response, "tech_acc", PayFairBoard.NONE);

        assertThat(priced.complexity()).isCloseTo(plain.complexity() + Math.log(59) - Math.log(8) - 2.0, within(0.1));
        assertThat(priced.inputs()).containsEntry("njs", 4.0);
        assertThat(chart(priced)).containsEntry("notesSlope", 1.0).containsEntry("njsSlope", -0.5);
        assertThat((Double) priced.inputs().get("notesTerm")).isCloseTo(Math.log(59), within(1e-6));
    }

    @Test
    void theBoardTakesOverFromTheChartAsScoresArrive() {
        NoteAccuracies response = response(List.of(0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99));
        NoteAccuracyComplexityRater.Rating chart = rater.rate(response, "tech_acc", PayFairBoard.NONE);
        double fair = chart.complexity() + 2.0;
        NoteAccuracyComplexityRater.Rating thin = rater.rate(response, "tech_acc", new PayFairBoard.Reading(fair, 0.9, 5, 500));
        NoteAccuracyComplexityRater.Rating early = rater.rate(response, "tech_acc", new PayFairBoard.Reading(fair, 0.9, 10, 49));
        NoteAccuracyComplexityRater.Rating half = rater.rate(response, "tech_acc", new PayFairBoard.Reading(fair, 0.9, 10, 60));
        NoteAccuracyComplexityRater.Rating big = rater.rate(response, "tech_acc", new PayFairBoard.Reading(fair, 0.9, 10, 450));

        assertThat(thin.complexity()).isEqualTo(chart.complexity());
        assertThat(early.complexity()).isEqualTo(chart.complexity());
        assertThat(half.complexity()).isCloseTo(chart.complexity() + 0.6, within(0.051));
        assertThat(big.complexity()).isCloseTo(chart.complexity() + 1.2, within(0.051));
        assertThat(half.inputs()).containsEntry("payWeight", 0.3).containsEntry("payAdjustment", 0.6)
                .containsEntry("payPlays", 10).containsEntry("scores", 60).containsEntry("botEstimate", chart.complexity());
        assertThat(big.inputs()).containsEntry("payWeight", 0.6);
        assertThat(thin.inputs()).containsEntry("payWeight", 0.0).containsEntry("payAdjustment", 0.0);
    }

    @Test
    void repricingReadsTheLiveBoardInsteadOfTheStoredOne() {
        NoteAccuracies response = response(List.of(0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99));
        response.setModelHash("abc123");
        NoteAccuracyComplexityRater.Rating stored = rater.rate(response, "tech_acc", PayFairBoard.NONE);
        MapDifficulty tech = difficulty("tech_acc");
        when(scenarioService.payFair(properties.getPayFair())).thenReturn(java.util.Map.of(tech.getId(),
                new PayFairBoard.Reading(stored.complexity() + 2.0, 0.9, 10, 450)));

        NoteAccuracyComplexityRater.Rating repriced = rater.reprice(tech, new ObjectMapper().valueToTree(stored.inputs()), "abc123")
                .orElseThrow();

        assertThat(repriced.complexity()).isCloseTo(stored.complexity() + 1.2, within(0.051));
        assertThat(repriced.inputs()).containsEntry("scores", 450);
    }

    @Test
    void neverGoesBelowZero() {
        List<Double> notes = List.of(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0);
        NoteAccuracyComplexityRater.Rating rating = rater.rate(response(notes), "tech_acc", PayFairBoard.NONE);
        assertThat(rating.complexity()).isZero();
    }

    @Test
    void skipsCategoriesWithoutCoefficientsBeforeDownloadingAnything() {
        assertThat(rater.rate(difficulty("true_acc"))).isEmpty();
        verify(zipCache, never()).get(anyString());
    }

    @Test
    void skipsMapsWithTooFewPredictedNotes() {
        MapDifficulty tech = difficulty("tech_acc");
        when(zipCache.get("abc")).thenReturn(Optional.of(new byte[] { 1 }));
        when(modelClient.noteAccuracies(any(), anyString(), anyString()))
                .thenReturn(Optional.of(response(List.of(0.99, 0.99))));

        assertThat(rater.rate(tech)).isEmpty();
    }

    @Test
    void sendsTheDifficultyNameAndCharacteristicTheModelExpects() {
        MapDifficulty tech = difficulty("tech_acc");
        when(zipCache.get("abc")).thenReturn(Optional.of(new byte[] { 1 }));
        when(modelClient.noteAccuracies(any(), anyString(), anyString()))
                .thenReturn(Optional.of(response(List.of(0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99))));

        assertThat(rater.rate(tech)).isPresent();
        verify(modelClient).noteAccuracies(any(), org.mockito.ArgumentMatchers.eq("Expert"),
                org.mockito.ArgumentMatchers.eq("Standard"));
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, Object> chart(NoteAccuracyComplexityRater.Rating rating) {
        return (java.util.Map<String, Object>) rating.inputs().get("chart");
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
