package com.accsaber.backend.model.dto.response.statistics;

import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;

import com.accsaber.backend.model.dto.response.item.ItemResponse;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class RarestUnboxedResponse {

    private UUID linkId;
    private String userId;
    private String userName;
    private String avatarUrl;
    private String cdnAvatarUrl;
    private PublicClanResponse clan;
    private String country;
    private UUID itemId;
    private ItemResponse item;
    private Long serialNumber;
    private long modifierCount;
    private List<String> modifiers;
    private String unusualEffect;
}
