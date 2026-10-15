package com.accsaber.backend.model.dto.response.player;

import com.accsaber.backend.model.dto.response.common.PlayerRef;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DuplicateCandidateResponse {

    PlayerRef primary;
    PlayerRef secondary;
    int identicalScores;
    int primaryTotalScores;
    int secondaryTotalScores;
}
