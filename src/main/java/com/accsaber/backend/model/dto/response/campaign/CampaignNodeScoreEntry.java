package com.accsaber.backend.model.dto.response.campaign;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignNodeScoreEntry {

    private Integer rank;
    private CampaignLeaderboardPlayer player;
    private Integer score;
    private BigDecimal accuracy;
    private BigDecimal ap;
}
