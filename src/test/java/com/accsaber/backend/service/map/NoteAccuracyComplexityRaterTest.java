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
import com.accsaber.backend.config.ComplexityRaterProperties.Coefficients;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.dto.request.map.ComplexityRaterSpec;
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
        properties.setWorstShare(0.25);
        Coefficients coefficients = new Coefficients();
        coefficients.setIntercept(40.0);
        coefficients.setMeanSlope(-10.0);
        coefficients.setWorstSlope(-2.0);
        properties.getCategories().put("tech_acc", coefficients);
        rater = new NoteAccuracyComplexityRater(zipCache, modelClient, properties, scenarioService);
    }

    @Test
    void reportsTheConfiguredVersion() {
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
        NoteAccuracyComplexityRater.Rating rating = rater.rate(response(notes), "tech_acc", NoteAccuracyComplexityRater.NO_BOARD, null);

        double mean = notes.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double expected = 40.0 - 10.0 * NoteAccuracyComplexityRater.linearised(mean)
                - 2.0 * NoteAccuracyComplexityRater.linearised(0.9945);
        assertThat(rating.complexity()).isCloseTo(expected, within(0.051));
        assertThat(rating.complexity() * 10).isEqualTo(Math.rint(rating.complexity() * 10));
        assertThat(rating.inputs()).containsEntry("predictedNotes", 8).containsEntry("model", "note-acc-beatleader")
                .containsEntry("worstShare", 0.25);
        assertThat((Double) rating.inputs().get("worstNoteAccuracy")).isCloseTo(0.9945, within(1e-6));
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> bands = (java.util.Map<String, Object>) rating.inputs().get("worstBands");
        assertThat(bands).containsKeys("0.01", "0.02", "0.05", "0.10", "0.25");
        assertThat((Double) bands.get("0.01")).isCloseTo(0.99, within(1e-6));
    }

    @Test
    void repricesStoredInputsOnlyWhenTheModelHashStillMatches() {
        NoteAccuracies response = response(List.of(0.99, 0.999, 0.999, 0.999, 0.999, 0.999, 0.999, 0.999));
        response.setModelHash("abc123");
        NoteAccuracyComplexityRater.Rating stored = rater.rate(response, "tech_acc", NoteAccuracyComplexityRater.NO_BOARD, null);
        JsonNode inputs = new ObjectMapper().valueToTree(stored.inputs());
        MapDifficulty tech = difficulty("tech_acc");

        assertThat(rater.reprice(tech, inputs, "other")).isEmpty();
        assertThat(rater.reprice(tech, inputs, null)).isEmpty();

        properties.getCategories().get("tech_acc").setIntercept(45.0);
        Optional<NoteAccuracyComplexityRater.Rating> repriced = rater.reprice(tech, inputs, "abc123");
        assertThat(repriced).isPresent();
        assertThat(repriced.get().complexity()).isCloseTo(stored.complexity() + 5.0, within(0.051));
        assertThat(chart(repriced.get())).containsEntry("intercept", 45.0);
        verify(modelClient, never()).noteAccuracies(any(), anyString(), anyString());
    }

    @Test
    void previewPricingSnapsTheWorstShareToTheNearestStoredBand() {
        NoteAccuracyComplexityRater.Rating stored = rater.rate(response(List.of(0.99, 0.999, 0.999, 0.999, 0.999, 0.999, 0.999, 0.999)),
                "tech_acc", NoteAccuracyComplexityRater.NO_BOARD, null);
        JsonNode inputs = new ObjectMapper().valueToTree(stored.inputs());
        ComplexityRaterSpec spec = properties.toSpec();
        spec.setWorstShare(0.03);

        NoteAccuracyComplexityRater.Rating priced = NoteAccuracyComplexityRater.price(inputs, spec, "tech_acc", null).orElseThrow();
        assertThat((Double) priced.inputs().get("worstNoteAccuracy")).isCloseTo(0.99, within(1e-6));
        assertThat(NoteAccuracyComplexityRater.price(inputs, spec, "true_acc", null)).isEmpty();
    }

    @Test
    void resetAndDotSharesPriceThroughTheirOwnSlopes() {
        properties.getCategories().get("tech_acc").setResetSlope(-2.0);
        properties.getCategories().get("tech_acc").setDotSlope(-4.0);
        NoteAccuracies response = response(List.of(0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99));
        NoteAccuracyComplexityRater.Rating plain = rater.rate(response, "tech_acc", NoteAccuracyComplexityRater.NO_BOARD, null);
        response.setResetShare(1.0);
        response.setDotShare(0.5);
        NoteAccuracyComplexityRater.Rating reset = rater.rate(response, "tech_acc", NoteAccuracyComplexityRater.NO_BOARD, null);

        assertThat(reset.complexity()).isCloseTo(plain.complexity() - 4.0, within(0.051));
        assertThat(reset.inputs()).containsEntry("resetShare", 1.0).containsEntry("dotShare", 0.5);
        assertThat(chart(reset)).containsEntry("resetSlope", -2.0).containsEntry("dotSlope", -4.0);
    }

    @Test
    void bottomRowUpSwingsPriceThroughTheirOwnSlopeAndLeaveCleanMapsAlone() {
        properties.getCategories().get("tech_acc").setBottomUpSlope(-4.0);
        NoteAccuracies response = response(List.of(0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99));
        NoteAccuracyComplexityRater.Rating clean = rater.rate(response, "tech_acc",
                NoteAccuracyComplexityRater.NO_BOARD, null);
        response.setBottomUpShare(0.15);
        NoteAccuracyComplexityRater.Rating pattern = rater.rate(response, "tech_acc",
                NoteAccuracyComplexityRater.NO_BOARD, null);

        assertThat(clean.inputs()).containsEntry("bottomUpShare", 0.0);
        assertThat(pattern.complexity()).isCloseTo(clean.complexity() - 0.6, within(0.051));
        assertThat(pattern.inputs()).containsEntry("bottomUpShare", 0.15);
        assertThat(chart(pattern)).containsEntry("bottomUpSlope", -4.0);
    }

    @Test
    void noteDensityPricesThroughItsOwnSlopeAndIsSkippedWithoutADuration() {
        properties.getCategories().get("tech_acc").setNpsSlope(-2.0);
        NoteAccuracies response = response(List.of(0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99));
        NoteAccuracyComplexityRater.Rating unknown = rater.rate(response, "tech_acc",
                NoteAccuracyComplexityRater.NO_BOARD, null);
        NoteAccuracyComplexityRater.Rating sparse = rater.rate(response, "tech_acc",
                NoteAccuracyComplexityRater.NO_BOARD, 8);
        NoteAccuracyComplexityRater.Rating dense = rater.rate(response, "tech_acc",
                NoteAccuracyComplexityRater.NO_BOARD, 2);

        assertThat(sparse.complexity()).isCloseTo(dense.complexity() + 2.0 * Math.log(4), within(0.051));
        assertThat(unknown.inputs()).containsEntry("npsTerm", null);
        assertThat((Double) dense.inputs().get("npsTerm")).isCloseTo(Math.log(4.0), within(1e-6));
        assertThat(chart(dense)).containsEntry("npsSlope", -2.0);
    }

    @Test
    void noteCountAndNjsPriceThroughTheirOwnSlopes() {
        properties.getCategories().get("tech_acc").setNotesSlope(1.0);
        properties.getCategories().get("tech_acc").setNjsSlope(-0.5);
        NoteAccuracies response = response(List.of(0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99));
        NoteAccuracyComplexityRater.Rating plain = rater.rate(response, "tech_acc", NoteAccuracyComplexityRater.NO_BOARD, null);
        response.setNotes(59);
        response.setNjs(4.0);
        NoteAccuracyComplexityRater.Rating priced = rater.rate(response, "tech_acc", NoteAccuracyComplexityRater.NO_BOARD, null);

        assertThat(priced.complexity()).isCloseTo(plain.complexity() + Math.log(59) - Math.log(8) - 2.0, within(0.1));
        assertThat(priced.inputs()).containsEntry("njs", 4.0);
        assertThat(chart(priced)).containsEntry("notesSlope", 1.0).containsEntry("njsSlope", -0.5);
        assertThat((Double) priced.inputs().get("notesTerm")).isCloseTo(Math.log(59), within(1e-6));
    }

    @Test
    void theBoardLineBlendsInBetweenTheScoreGates() {
        Coefficients boardLine = new Coefficients();
        boardLine.setIntercept(40.0);
        boardLine.setMeanSlope(-10.0);
        boardLine.setWorstSlope(-2.0);
        boardLine.setBoardSlope(-10.0);
        properties.getBoardCategories().put("tech_acc", boardLine);
        properties.getBoard().setMaxNudge(0.0);
        NoteAccuracies response = response(List.of(0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99));
        NoteAccuracyComplexityRater.Rating chart = rater.rate(response, "tech_acc", NoteAccuracyComplexityRater.NO_BOARD, null);
        NoteAccuracyComplexityRater.Rating thin = rater.rate(response, "tech_acc", new ComplexityScenarioService.BoardEase(0.2, 5, 500), null);
        int halfway = (properties.getBoard().getMinScores() + properties.getBoard().getFullScores()) / 2;
        NoteAccuracyComplexityRater.Rating half = rater.rate(response, "tech_acc", new ComplexityScenarioService.BoardEase(0.2, 50, halfway), null);
        NoteAccuracyComplexityRater.Rating full = rater.rate(response, "tech_acc", new ComplexityScenarioService.BoardEase(0.2, 50, 500), null);

        assertThat(thin.complexity()).isEqualTo(chart.complexity());
        assertThat(full.complexity()).isCloseTo(chart.complexity() - 2.0, within(0.051));
        assertThat(half.complexity()).isCloseTo(chart.complexity() - 1.0, within(0.051));
        assertThat(full.inputs()).containsEntry("boardWeight", 1.0).containsEntry("boardPlayers", 50)
                .containsEntry("scores", 500).containsEntry("boardEase", 0.2);
        assertThat(thin.inputs()).containsEntry("boardWeight", 0.0);
        assertThat(chart.inputs().get("boardComplexity")).isNull();
    }

    @Test
    void theBoardNudgeIsCappedUnlessTheCapIsOff() {
        Coefficients boardLine = new Coefficients();
        boardLine.setIntercept(40.0);
        boardLine.setMeanSlope(-10.0);
        boardLine.setWorstSlope(-2.0);
        boardLine.setBoardSlope(-10.0);
        NoteAccuracies response = response(List.of(0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99));
        properties.getBoardCategories().put("tech_acc", boardLine);
        NoteAccuracyComplexityRater.Rating chart = rater.rate(response, "tech_acc", NoteAccuracyComplexityRater.NO_BOARD, null);
        ComplexityScenarioService.BoardEase board = new ComplexityScenarioService.BoardEase(0.2, 50, 500);

        assertThat(rater.rate(response, "tech_acc", board, null).complexity()).isCloseTo(chart.complexity() - 1.5, within(0.051));
        properties.getBoard().setMaxNudge(0.0);
        assertThat(rater.rate(response, "tech_acc", board, null).complexity()).isCloseTo(chart.complexity() - 2.0, within(0.051));
    }

    @Test
    void repricingReadsTheLiveBoardInsteadOfTheStoredOne() {
        Coefficients boardLine = new Coefficients();
        boardLine.setIntercept(40.0);
        boardLine.setMeanSlope(-10.0);
        boardLine.setWorstSlope(-2.0);
        boardLine.setBoardSlope(-10.0);
        properties.getBoardCategories().put("tech_acc", boardLine);
        properties.getBoard().setMaxNudge(0.0);
        NoteAccuracies response = response(List.of(0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99));
        response.setModelHash("abc123");
        NoteAccuracyComplexityRater.Rating stored = rater.rate(response, "tech_acc", NoteAccuracyComplexityRater.NO_BOARD, null);
        MapDifficulty tech = difficulty("tech_acc");
        when(scenarioService.boardEase(20, 10)).thenReturn(java.util.Map.of(tech.getId(),
                new ComplexityScenarioService.BoardEase(0.2, 50, 500)));

        NoteAccuracyComplexityRater.Rating repriced = rater.reprice(tech, new ObjectMapper().valueToTree(stored.inputs()), "abc123")
                .orElseThrow();

        assertThat(repriced.complexity()).isCloseTo(stored.complexity() - 2.0, within(0.051));
        assertThat(repriced.inputs()).containsEntry("scores", 500);
    }

    @Test
    void neverGoesBelowZero() {
        List<Double> notes = List.of(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0);
        NoteAccuracyComplexityRater.Rating rating = rater.rate(response(notes), "tech_acc", NoteAccuracyComplexityRater.NO_BOARD, null);
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
