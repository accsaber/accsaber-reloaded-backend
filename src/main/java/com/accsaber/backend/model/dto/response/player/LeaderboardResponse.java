package com.accsaber.backend.model.dto.response.player;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardResponse {

    private Integer ranking;
    private Integer countryRanking;
    private String userId;
    private String userName;
    private String country;
    private String avatarUrl;
    private String cdnAvatarUrl;
    private BigDecimal ap;
    private BigDecimal averageAcc;
    private BigDecimal averageAp;
    private Integer rankedPlays;
    private UUID topPlayId;
    private boolean playerInactive;
    private Integer rankingLastWeek;
    private String supporterTier;
}
