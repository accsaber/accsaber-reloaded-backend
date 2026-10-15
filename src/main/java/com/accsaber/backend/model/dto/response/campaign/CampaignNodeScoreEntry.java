package com.accsaber.backend.model.dto.response.campaign;

import com.accsaber.backend.model.dto.response.statistics.UserIdentity;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignNodeScoreEntry {

    private Integer rank;
    private UserIdentity player;
    private Integer score;
    private Double accuracy;
    private Double ap;
}
