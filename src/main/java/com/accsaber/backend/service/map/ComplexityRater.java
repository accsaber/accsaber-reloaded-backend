package com.accsaber.backend.service.map;

import java.util.Map;
import java.util.Optional;

import com.accsaber.backend.model.entity.map.ComplexityEstimateSource;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.fasterxml.jackson.databind.JsonNode;

public interface ComplexityRater {

    record Rating(double complexity, Map<String, Object> inputs) {
    }

    ComplexityEstimateSource source();

    String version();

    Optional<Rating> rate(MapDifficulty difficulty);

    default Optional<Rating> reprice(MapDifficulty difficulty, JsonNode inputs, String currentModelHash) {
        return Optional.empty();
    }
}
