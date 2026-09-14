package com.accsaber.backend.model.dto.response.statistics;

import java.time.Instant;
import java.util.List;

import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ItemHolderResponse {

    private String userId;
    private String userName;
    private String avatarUrl;
    private String cdnAvatarUrl;
    private PublicClanResponse clan;
    private String country;
    private long quantity;
    private Long lowestSerial;
    private Instant acquiredAt;
    private List<String> modifiers;
    private Integer ranking;
    private boolean following;
}
