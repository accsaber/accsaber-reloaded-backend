package com.accsaber.backend.model.dto.response.player;

import java.util.UUID;

import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;

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
    private PublicClanResponse clan;
    private Double ap;
    private Double averageAcc;
    private Double averageAp;
    private Integer rankedPlays;
    private UUID topPlayId;
    private boolean playerInactive;
    private Integer rankingLastWeek;
    private String supporterTier;
}
