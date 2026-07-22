package com.accsaber.backend.service.item;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.accsaber.backend.model.dto.response.item.CrateContentResponse;
import com.accsaber.backend.model.dto.response.item.CrateModifierResponse;
import com.accsaber.backend.model.dto.response.item.CrateOpenResponse;
import com.accsaber.backend.model.dto.response.item.ItemModifierResponse;
import com.accsaber.backend.model.dto.response.item.ItemResponse;
import com.accsaber.backend.model.dto.response.item.ItemTypeResponse;
import com.accsaber.backend.model.dto.response.item.TradeResponse;
import com.accsaber.backend.model.dto.response.item.UnusualEffectResponse;
import com.accsaber.backend.model.dto.response.item.UserItemResponse;
import com.accsaber.backend.model.entity.item.CrateContent;
import com.accsaber.backend.model.entity.item.CrateModifier;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemModifier;
import com.accsaber.backend.model.entity.item.ItemType;
import com.accsaber.backend.model.entity.item.TradeItemSide;
import com.accsaber.backend.model.entity.item.UnusualEffect;
import com.accsaber.backend.model.entity.item.UserCrateOpen;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.model.entity.item.UserItemTrade;
import com.accsaber.backend.model.entity.item.UserItemTradeItem;
import com.accsaber.backend.model.entity.user.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class ItemMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ItemMapper() {
    }

    public static ItemTypeResponse toTypeResponse(ItemType type) {
        return ItemTypeResponse.builder()
                .id(type.getId())
                .parentTypeId(type.getParentType() != null ? type.getParentType().getId() : null)
                .key(type.getKey())
                .name(type.getName())
                .description(type.getDescription())
                .valueSchema(toObject(type.getValueSchema()))
                .active(type.isActive())
                .createdAt(type.getCreatedAt())
                .build();
    }

    public static ItemResponse toItemResponse(Item item) {
        return ItemResponse.builder()
                .id(item.getId())
                .typeId(item.getType().getId())
                .typeKey(item.getType().getKey())
                .name(item.getName())
                .description(item.getDescription())
                .iconUrl(item.getIconUrl())
                .value(toObject(item.getValue()))
                .rarity(item.getRarity().name())
                .tradeable(item.isTradeable())
                .visible(item.isVisible())
                .active(item.isActive())
                .deprecated(item.isDeprecated())
                .stackable(item.isStackable())
                .welcomeGrant(item.isWelcomeGrant())
                .missionPoolable(item.isMissionPoolable())
                .worth(item.getWorth())
                .requirement(item.getRequirement())
                .unlockLevel(item.getUnlockLevel())
                .createdAt(item.getCreatedAt())
                .build();
    }

    public static UserItemResponse.ModifierRef toModifierRef(ItemModifier m) {
        if (m == null)
            return null;
        return UserItemResponse.ModifierRef.builder()
                .id(m.getId())
                .key(m.getKey())
                .name(m.getName())
                .colorHex(m.getColorHex())
                .effectSpec(toObject(m.getEffectSpec()))
                .build();
    }

    public static ItemModifierResponse toModifierResponse(ItemModifier m) {
        return ItemModifierResponse.builder()
                .id(m.getId())
                .key(m.getKey())
                .name(m.getName())
                .description(m.getDescription())
                .colorHex(m.getColorHex())
                .effectSpec(toObject(m.getEffectSpec()))
                .globalDropChance(m.getGlobalDropChance())
                .seasonStart(m.getSeasonStart())
                .seasonEnd(m.getSeasonEnd())
                .active(m.isActive())
                .createdAt(m.getCreatedAt())
                .build();
    }

    public static List<CrateModifierResponse> toCrateModifierResponses(List<CrateModifier> modifiers) {
        return modifiers.stream()
                .map(ItemMapper::toCrateModifierResponse)
                .toList();
    }

    public static CrateModifierResponse toCrateModifierResponse(CrateModifier modifier) {
        return CrateModifierResponse.builder()
                .modifier(toModifierRef(modifier.getModifier()))
                .dropChance(modifier.getDropChance())
                .build();
    }

    public static UserItemResponse toUserItemResponse(UserItemLink link) {
        return toUserItemResponse(link, null);
    }

    public static UserItemResponse toPreviewResponse(Item item, Set<ItemModifier> modifiers,
            UnusualEffect effect, String variantKey) {
        UserItemResponse response = UserItemResponse.builder()
                .item(toItemResponse(item))
                .modifiers(toModifierRefs(modifiers))
                .unusualEffect(toEffectRef(effect))
                .quantity(1L)
                .build();
        if (variantKey != null && !variantKey.isBlank()) {
            response.setVariantKey(variantKey);
        }
        return response;
    }

    public static UnusualEffectResponse toUnusualEffectResponse(UnusualEffect effect) {
        return UnusualEffectResponse.builder()
                .id(effect.getId())
                .key(effect.getKey())
                .name(effect.getName())
                .description(effect.getDescription())
                .effectSpec(toObject(effect.getEffectSpec()))
                .active(effect.isActive())
                .createdAt(effect.getCreatedAt())
                .build();
    }

    public static UserItemResponse.EffectRef toEffectRef(UnusualEffect effect) {
        if (effect == null) {
            return null;
        }
        return UserItemResponse.EffectRef.builder()
                .id(effect.getId())
                .key(effect.getKey())
                .name(effect.getName())
                .effectSpec(toObject(effect.getEffectSpec()))
                .build();
    }

    public static UserItemResponse toUserItemResponse(UserItemLink link, Map<String, Long> counters) {
        return UserItemResponse.builder()
                .linkId(link.getId())
                .item(toItemResponse(link.getItem()))
                .modifiers(toModifierRefs(link.getModifiers()))
                .unusualEffect(toEffectRef(link.getUnusualEffect()))
                .serialNumber(link.getSerialNumber())
                .quantity(link.getQuantity())
                .counters(counters == null || counters.isEmpty() ? null : counters)
                .source(link.getSource().name())
                .sourceId(link.getSourceId())
                .awardedByStaffId(link.getAwardedBy() != null ? link.getAwardedBy().getId() : null)
                .reason(link.getReason())
                .awardedAt(link.getAwardedAt())
                .build();
    }

    public static List<CrateContentResponse> toCrateContentResponses(List<CrateContent> contents) {
        long total = contents.stream().mapToLong(CrateContent::getDropWeight).sum();
        return contents.stream()
                .map(c -> toCrateContentResponse(c, total))
                .toList();
    }

    public static CrateContentResponse toCrateContentResponse(CrateContent content, long totalWeight) {
        return CrateContentResponse.builder()
                .rewardItem(toItemResponse(content.getRewardItem()))
                .dropWeight(content.getDropWeight())
                .dropChance(totalWeight <= 0
                        ? java.math.BigDecimal.ZERO
                        : java.math.BigDecimal.valueOf(content.getDropWeight())
                                .divide(java.math.BigDecimal.valueOf(totalWeight), 6,
                                        java.math.RoundingMode.HALF_UP))
                .build();
    }

    public static CrateOpenResponse toCrateOpenResponse(UserCrateOpen open) {
        return CrateOpenResponse.builder()
                .id(open.getId())
                .crate(toItemResponse(open.getCrateItem()))
                .consumedLinkId(open.getConsumedLinkId())
                .reward(open.getRewardLink() != null ? toUserItemResponse(open.getRewardLink()) : null)
                .rolledAt(open.getRolledAt())
                .build();
    }

    public static TradeResponse toTradeResponse(UserItemTrade trade) {
        return TradeResponse.builder()
                .id(trade.getId())
                .fromUserId(trade.getFromUser().getId())
                .toUserId(trade.getToUser().getId())
                .fromUser(toTradeUserRef(trade.getFromUser()))
                .toUser(toTradeUserRef(trade.getToUser()))
                .offeredItems(tradeItemsForSide(trade, TradeItemSide.offered))
                .requestedItems(tradeItemsForSide(trade, TradeItemSide.requested))
                .offeredEssence(trade.getOfferedEssence())
                .requestedEssence(trade.getRequestedEssence())
                .status(trade.getStatus().name())
                .message(trade.getMessage())
                .createdAt(trade.getCreatedAt())
                .resolvedAt(trade.getResolvedAt())
                .build();
    }

    private static TradeResponse.TradeUserRef toTradeUserRef(User user) {
        if (user == null) {
            return null;
        }
        return TradeResponse.TradeUserRef.builder()
                .id(user.getId())
                .name(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .cdnAvatarUrl(user.getCdnAvatarUrl())
                .country(user.getCountry())
                .build();
    }

    private static List<UserItemResponse.ModifierRef> toModifierRefs(Set<ItemModifier> modifiers) {
        if (modifiers == null || modifiers.isEmpty()) {
            return List.of();
        }
        return modifiers.stream()
                .sorted(Comparator.comparing(ItemModifier::getKey))
                .map(ItemMapper::toModifierRef)
                .toList();
    }

    private static List<TradeResponse.TradeItemRef> tradeItemsForSide(UserItemTrade trade, TradeItemSide side) {
        return trade.getItems().stream()
                .filter(ti -> ti.getSide() == side)
                .sorted(Comparator.comparing(UserItemTradeItem::getCreatedAt))
                .map(ItemMapper::toTradeItemRef)
                .toList();
    }

    private static TradeResponse.TradeItemRef toTradeItemRef(UserItemTradeItem entry) {
        UserItemLink link = entry.getUserItemLink();
        return TradeResponse.TradeItemRef.builder()
                .linkId(link.getId())
                .item(toItemResponse(link.getItem()))
                .modifiers(toModifierRefs(link.getModifiers()))
                .unusualEffect(toEffectRef(link.getUnusualEffect()))
                .serialNumber(link.getSerialNumber())
                .quantity(entry.getQuantity())
                .build();
    }

    private static Object toObject(JsonNode node) {
        if (node == null)
            return null;
        try {
            return MAPPER.treeToValue(node, Object.class);
        } catch (Exception e) {
            return null;
        }
    }
}
