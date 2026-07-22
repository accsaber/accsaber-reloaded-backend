package com.accsaber.backend.model.dto.response.item;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TradeResponse {

    private UUID id;
    private Long fromUserId;
    private Long toUserId;
    private TradeUserRef fromUser;
    private TradeUserRef toUser;
    private List<TradeItemRef> offeredItems;
    private List<TradeItemRef> requestedItems;
    private long offeredEssence;
    private long requestedEssence;
    private String status;
    private String message;
    private Instant createdAt;
    private Instant resolvedAt;

    @Getter
    @Builder
    public static class TradeUserRef {
        private Long id;
        private String name;
        private String avatarUrl;
        private String cdnAvatarUrl;
        private String country;
    }

    @Getter
    @Builder
    public static class TradeItemRef {
        private UUID linkId;
        private ItemResponse item;
        private List<UserItemResponse.ModifierRef> modifiers;
        private UserItemResponse.EffectRef unusualEffect;
        private Long serialNumber;
        private Long quantity;
    }
}
