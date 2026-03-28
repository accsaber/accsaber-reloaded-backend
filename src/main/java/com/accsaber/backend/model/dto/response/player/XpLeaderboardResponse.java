package com.accsaber.backend.model.dto.response.player;

import java.math.BigDecimal;

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
    private String userId;
    private String userName;
    private String country;
    private String avatarUrl;
    private BigDecimal totalXp;
    private Integer level;
    private boolean ssInactive;
    private Integer rankingLastWeek;
}
