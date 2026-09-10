package com.accsaber.backend.service.map;

import java.util.Map;
import java.util.Optional;

import com.accsaber.backend.model.entity.map.ComplexityEstimateSource;
import com.accsaber.backend.model.entity.map.MapDifficulty;

public interface ComplexityRater {

    record Rating(double complexity, Map<String, Object> inputs) {
    }

    ComplexityEstimateSource source();

    String version();

    Optional<Rating> rate(MapDifficulty difficulty);
}
