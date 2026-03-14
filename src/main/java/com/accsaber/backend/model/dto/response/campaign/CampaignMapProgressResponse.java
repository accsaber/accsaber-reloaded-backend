package com.accsaber.backend.model.dto.response.campaign;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignMapProgressResponse {

    private UUID campaignMapId;
    private UUID mapDifficultyId;
    private String songName;
    private String difficulty;
    private String characteristic;
    private BigDecimal accuracyRequirement;
    private BigDecimal userAccuracy;
    private Integer userScore;
    private boolean completed;
    private boolean unlocked;
}
