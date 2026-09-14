package com.accsaber.backend.model.dto.response.clan;

import java.util.UUID;

import com.accsaber.backend.model.dto.response.item.ItemResponse;
import com.accsaber.backend.model.entity.clan.war.ClanWarRewardItem;
import com.accsaber.backend.service.item.ItemMapper;

public record ClanWarRewardItemResponse(UUID id, ItemResponse item, int quantity, Integer topContributors,
        boolean active) {

    public static ClanWarRewardItemResponse of(ClanWarRewardItem reward) {
        return new ClanWarRewardItemResponse(reward.getId(), ItemMapper.toItemResponse(reward.getItem()),
                reward.getQuantity(), reward.getTopContributors(), reward.isActive());
    }
}
