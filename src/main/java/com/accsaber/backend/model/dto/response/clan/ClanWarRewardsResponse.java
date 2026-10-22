package com.accsaber.backend.model.dto.response.clan;

import java.util.List;

import com.accsaber.backend.model.dto.response.common.PlayerRef;

public record ClanWarRewardsResponse(
        List<ClanWarRewardItemResponse> pool,
        List<ClanReward> clans,
        List<PlayerReward> players) {

    public record ClanReward(PublicClanResponse clan, double clanXp, double standing,
            List<ClanWarRewardItemResponse> items) {
    }

    public record PlayerReward(PlayerRef player, PublicClanResponse clan, double xp,
            List<ClanWarRewardItemResponse> items) {
    }
}
