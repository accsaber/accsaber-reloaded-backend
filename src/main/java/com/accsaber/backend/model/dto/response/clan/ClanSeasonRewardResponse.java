package com.accsaber.backend.model.dto.response.clan;

import java.util.UUID;

import com.accsaber.backend.model.dto.response.item.ItemResponse;
import com.accsaber.backend.model.entity.clan.ClanSeasonReward;
import com.accsaber.backend.service.item.ItemMapper;

public record ClanSeasonRewardResponse(UUID id, int rankFrom, int rankTo, ItemResponse item, int quantity) {

    public static ClanSeasonRewardResponse of(ClanSeasonReward reward) {
        return new ClanSeasonRewardResponse(reward.getId(), reward.getRankFrom(), reward.getRankTo(),
                ItemMapper.toItemResponse(reward.getItem()), reward.getQuantity());
    }
}
