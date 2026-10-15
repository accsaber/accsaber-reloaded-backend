package com.accsaber.backend.model.dto.response.player;


import com.accsaber.backend.model.dto.response.common.PlayerRef;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XpLeaderboardResponse {

    private Integer ranking;
    private Integer countryRanking;
    private PlayerRef player;
    private Double totalXp;
    private Integer level;
    private boolean playerInactive;
    private Integer rankingLastWeek;
    private String supporterTier;
}
