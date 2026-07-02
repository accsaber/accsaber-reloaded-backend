package com.accsaber.backend.model.dto.response.campaign;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignDifficultyProgressResponse {

    private CampaignDifficultyResponse node;
    private BigDecimal userValue;
    private Integer userScore;
    private boolean completed;
    private boolean unlocked;
}
