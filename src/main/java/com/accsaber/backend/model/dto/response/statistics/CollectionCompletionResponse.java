package com.accsaber.backend.model.dto.response.statistics;

import com.accsaber.backend.model.dto.response.common.PlayerRef;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CollectionCompletionResponse {

    private PlayerRef player;
    private long ownedCount;
    private long catalogTotal;
    private Double completionPercent;
}
