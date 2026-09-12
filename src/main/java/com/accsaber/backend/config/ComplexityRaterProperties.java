package com.accsaber.backend.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.accsaber.backend.model.dto.request.map.ComplexityRaterSpec;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "accsaber.complexity.rater")
public class ComplexityRaterProperties {

    private String version = "note-acc-2026-09-24";
    private double worstShare = 0.05;
    private Board board = new Board();
    private Map<String, Coefficients> categories = new LinkedHashMap<>();
    private Map<String, Coefficients> boardCategories = new LinkedHashMap<>();

    @Data
    public static class Board {
        private int minScores = 50;
        private int fullScores = 70;
        private int minPlayers = 10;
        private int minPlayerPlays = 20;
        private int topPlays = 10;
        private double maxNudge = 1.5;
    }

    @Data
    public static class Coefficients {
        private double intercept;
        private double meanSlope;
        private double worstSlope;
        private double resetSlope;
        private double dotSlope;
        private double bottomUpSlope;
        private double notesSlope;
        private double npsSlope;
        private double njsSlope;
        private double boardSlope;

        ComplexityRaterSpec.Coefficients toSpec() {
            ComplexityRaterSpec.Coefficients spec = new ComplexityRaterSpec.Coefficients();
            spec.setIntercept(intercept);
            spec.setMeanSlope(meanSlope);
            spec.setWorstSlope(worstSlope);
            spec.setResetSlope(resetSlope);
            spec.setDotSlope(dotSlope);
            spec.setBottomUpSlope(bottomUpSlope);
            spec.setNotesSlope(notesSlope);
            spec.setNpsSlope(npsSlope);
            spec.setNjsSlope(njsSlope);
            spec.setBoardSlope(boardSlope);
            return spec;
        }
    }

    public ComplexityRaterSpec toSpec() {
        ComplexityRaterSpec spec = new ComplexityRaterSpec();
        spec.setWorstShare(worstShare);
        spec.getBoard().setMinScores(board.getMinScores());
        spec.getBoard().setFullScores(board.getFullScores());
        spec.getBoard().setMinPlayers(board.getMinPlayers());
        spec.getBoard().setMinPlayerPlays(board.getMinPlayerPlays());
        spec.getBoard().setTopPlays(board.getTopPlays());
        spec.getBoard().setMaxNudge(board.getMaxNudge());
        categories.forEach((code, c) -> spec.getCategories().put(code, c.toSpec()));
        boardCategories.forEach((code, c) -> spec.getBoardCategories().put(code, c.toSpec()));
        return spec;
    }
}
