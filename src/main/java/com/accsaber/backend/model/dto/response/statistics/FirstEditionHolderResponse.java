package com.accsaber.backend.model.dto.response.statistics;

import java.util.UUID;

import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;

import com.accsaber.backend.model.dto.response.item.ItemResponse;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class FirstEditionHolderResponse {

    private UUID itemId;
    private ItemResponse item;
    private UUID linkId;
    private Long serialNumber;
    private String userId;
    private String userName;
    private String avatarUrl;
    private String cdnAvatarUrl;
    private PublicClanResponse clan;
    private String country;
}
