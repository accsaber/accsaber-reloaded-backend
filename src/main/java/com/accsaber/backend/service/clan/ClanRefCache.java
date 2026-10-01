package com.accsaber.backend.service.clan;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.dto.response.item.ItemResponse;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanEquippedItem;
import com.accsaber.backend.model.entity.clan.ClanMember;
import com.accsaber.backend.model.event.ClanMembershipChangedEvent;
import com.accsaber.backend.repository.clan.ClanEquippedItemRepository;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.ClanRepository;
import com.accsaber.backend.service.item.ItemMapper;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ClanRefCache {

    private static final String TAG_COSMETIC = "clan_tag_card";

    private static volatile Map<Long, PublicClanResponse> byUser = Map.of();

    private final ClanRepository clanRepository;
    private final ClanMemberRepository memberRepository;
    private final ClanEquippedItemRepository equippedRepository;

    public static PublicClanResponse forUser(Long userId) {
        return userId == null ? null : byUser.get(userId);
    }

    public static PublicClanResponse forUser(String userId) {
        return userId == null ? null : byUser.get(Long.valueOf(userId));
    }

    @PostConstruct
    @Scheduled(fixedDelay = 600_000, initialDelay = 600_000)
    public synchronized void reload() {
        Map<UUID, List<ItemResponse>> equipped = tagCosmetics(equippedRepository.findAllOfActiveClans());
        Map<UUID, PublicClanResponse> refs = new HashMap<>();
        Map<Long, PublicClanResponse> next = new HashMap<>();
        for (ClanMember member : memberRepository.findAllOpenInActiveClans()) {
            Clan clan = member.getClan();
            next.put(member.getUser().getId(), refs.computeIfAbsent(clan.getId(),
                    id -> PublicClanResponse.of(clan, equipped.getOrDefault(id, List.of()))));
        }
        byUser = Map.copyOf(next);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMembershipChanged(ClanMembershipChangedEvent event) {
        refresh(event.clanId());
    }

    public void refreshAfterCommit(UUID clanId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            refresh(clanId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                refresh(clanId);
            }
        });
    }

    synchronized void refresh(UUID clanId) {
        Map<Long, PublicClanResponse> next = new HashMap<>(byUser);
        next.values().removeIf(ref -> ref.id().equals(clanId));
        clanRepository.findByIdAndActiveTrue(clanId).ifPresent(clan -> {
            PublicClanResponse ref = PublicClanResponse.of(clan,
                    tagCosmetics(equippedRepository.findByClanIds(List.of(clanId))).getOrDefault(clanId, List.of()));
            memberRepository.findOpenUserIds(clanId).forEach(userId -> next.put(userId, ref));
        });
        byUser = Map.copyOf(next);
    }

    private static Map<UUID, List<ItemResponse>> tagCosmetics(List<ClanEquippedItem> equipped) {
        return equipped.stream()
                .filter(e -> TAG_COSMETIC.equals(e.getItem().getType().getKey()))
                .collect(Collectors.groupingBy(e -> e.getClan().getId(),
                        Collectors.mapping(e -> ItemMapper.toItemResponse(e.getItem()), Collectors.toList())));
    }
}
