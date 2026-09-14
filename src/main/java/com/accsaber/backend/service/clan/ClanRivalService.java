package com.accsaber.backend.service.clan;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.clan.ClanRivalResponse;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.entity.chat.ChatEvent;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanRival;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.clan.ClanAllianceRepository;
import com.accsaber.backend.repository.clan.ClanRepository;
import com.accsaber.backend.repository.clan.ClanRivalRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClanRivalService {

    private final ClanRivalRepository rivalRepository;
    private final ClanRepository clanRepository;
    private final ClanAllianceRepository allianceRepository;
    private final ClanAccessService accessService;
    private final ClanCosmeticService cosmeticService;
    private final ClanChatChannel chatChannel;

    public Page<ClanRivalResponse> list(UUID clanId, boolean incoming, Pageable pageable) {
        clanRepository.findByIdAndActiveTrue(clanId).orElseThrow(() -> new ResourceNotFoundException("Clan", clanId));
        Page<ClanRival> page = incoming
                ? rivalRepository.findDeclaredAgainst(clanId, pageable)
                : rivalRepository.findDeclaredBy(clanId, pageable);
        List<Clan> others = page.getContent().stream().map(r -> incoming ? r.getClan() : r.getRivalClan()).toList();
        Map<UUID, PublicClanResponse> refs = cosmeticService.publicRefs(others);
        return page.map(r -> ClanRivalResponse.of(r, refs.get((incoming ? r.getClan() : r.getRivalClan()).getId()),
                incoming));
    }

    @Transactional
    public ClanRivalResponse declare(UUID clanId, Long playerId, UUID rivalClanId) {
        User actor = accessService.player(playerId);
        accessService.require(clanId, actor.getId(), ClanPermission.MANAGE_RIVALS);
        if (clanId.equals(rivalClanId)) {
            throw new ValidationException("clanId", "must be another clan");
        }
        Clan clan = active(clanId);
        Clan rival = active(rivalClanId);
        if (allianceRepository.existsActiveBetween(clanId, rivalClanId)) {
            throw new ConflictException("End the alliance before calling this clan a rival");
        }
        ClanRival row = rivalRepository.findByClan_IdAndRivalClan_Id(clanId, rivalClanId)
                .orElseGet(() -> ClanRival.builder().clan(clan).rivalClan(rival).active(false).build());
        if (row.isActive()) {
            throw new ConflictException("This clan is already a rival");
        }
        row.setActive(true);
        row.setDeclaredBy(actor);
        ClanRival saved = rivalRepository.saveAndFlush(row);
        chatChannel.announce(clan, ChatNotice.ofClan(ChatEvent.rival_declared, actor, rival));
        chatChannel.announce(rival, ChatNotice.ofClan(ChatEvent.rivaled_by, actor, clan));
        return ClanRivalResponse.of(saved, cosmeticService.publicRefs(List.of(rival)).get(rivalClanId), false);
    }

    @Transactional
    public void drop(UUID clanId, Long playerId, UUID rivalClanId) {
        User actor = accessService.player(playerId);
        accessService.require(clanId, actor.getId(), ClanPermission.MANAGE_RIVALS);
        ClanRival row = rivalRepository.findByClan_IdAndRivalClan_Id(clanId, rivalClanId)
                .filter(ClanRival::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("ClanRival", rivalClanId));
        row.setActive(false);
        rivalRepository.save(row);
    }

    private Clan active(UUID clanId) {
        return clanRepository.findByIdAndActiveTrue(clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Clan", clanId));
    }
}
