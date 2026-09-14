package com.accsaber.backend.model.dto.response.player;

import java.util.List;

import com.accsaber.backend.model.dto.response.clan.ClanStatsResponse;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserAllStatisticsResponse {

    private Double totalXp;
    private Double totalScoreXp;
    private Double totalMilestoneXp;
    private Double totalMilestoneSetBonusXp;
    private Double totalMissionXp;
    private Double totalCampaignXp;
    private ClanStatsResponse clan;
    private List<UserCategoryStatisticsResponse> categories;
}
