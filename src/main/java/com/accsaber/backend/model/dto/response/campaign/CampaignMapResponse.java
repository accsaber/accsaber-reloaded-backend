package com.accsaber.backend.model.dto.response.campaign;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignMapResponse {

    private UUID id;
    private UUID mapDifficultyId;
    private String songName;
    private String songAuthor;
    private String mapAuthor;
    private String coverUrl;
    private String difficulty;
    private String characteristic;
    private BigDecimal accuracyRequirement;
    private BigDecimal xp;
    private List<UUID> prerequisiteMapIds;
}
