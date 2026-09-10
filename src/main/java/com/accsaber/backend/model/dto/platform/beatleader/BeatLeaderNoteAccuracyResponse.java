package com.accsaber.backend.model.dto.platform.beatleader;

import java.util.List;

public record BeatLeaderNoteAccuracyResponse(List<Double> noteAccuracies, Double aiAccuracy) {
}
