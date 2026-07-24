package com.accsaber.backend.model.dto.response.campaign;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CurrentMilestoneResponse {

    private UUID nodeId;
    private String label;
    private int depth;
}
