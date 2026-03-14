package com.accsaber.backend.model.event;

import com.accsaber.backend.model.dto.response.score.ScoreResponse;

public record ScoreSubmittedEvent(ScoreResponse score) {
}
