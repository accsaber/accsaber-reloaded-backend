package com.accsaber.backend.model.dto.response.player;

import java.math.BigDecimal;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserAllStatisticsResponse {

    private BigDecimal totalXp;
    private BigDecimal totalScoreXp;
    private BigDecimal totalMilestoneXp;
    private BigDecimal totalMilestoneSetBonusXp;
    private BigDecimal totalMissionXp;
    private BigDecimal totalCampaignXp;
    private List<UserCategoryStatisticsResponse> categories;
}
