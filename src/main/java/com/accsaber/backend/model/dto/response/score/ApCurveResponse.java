package com.accsaber.backend.model.dto.response.score;

import java.util.List;
import java.util.UUID;

public record ApCurveResponse(UUID categoryId, String categoryCode, List<Double> ap) {
}
