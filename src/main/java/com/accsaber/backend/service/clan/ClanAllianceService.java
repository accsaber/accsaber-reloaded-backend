package com.accsaber.backend.service.clan;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.clan.ClanAllianceResponse;
import com.accsaber.backend.model.dto.response.clan.ClanTrustResponse;
import com.accsaber.backend.model.dto.response.item.ItemResponse;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanAlliance;
import com.accsaber.backend.model.entity.clan.ClanAllianceStatus;
import com.accsaber.backend.model.entity.clan.ClanAuditAction;
import com.accsaber.backend.model.entity.clan.ClanAuditEntry;
import com.accsaber.backend.model.entity.clan.ClanCapacity;
import com.accsaber.backend.model.entity.clan.ClanMember;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.clan.ClanAllianceRepository;
import com.accsaber.backend.repository.clan.ClanAuditEntryRepository;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.ClanRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClanAllianceService {

    private static final Comparator<UUID> LOCK_ORDER = Comparator
            .comparing(UUID::getMostSignificantBits, Long::compareUnsigned)
            .thenComparing(UUID::getLeastSignificantBits, Long::compareUnsigned);

    private final ClanAllianceRepository allianceRepository;
    private final ClanRepository clanRepository;
    private final ClanMemberRepository memberRepository;
    private final ClanAuditEntryRepository auditRepository;
    private final ClanRoster roster;
    private final ClanAccessService accessService;
    private final ClanLevelService levelService;
    private final ClanCosmeticService cosmeticService;
    private final ClanStrengthService strengthService;
    private final ClanProperties clanProperties;

    public Page<ClanAllianceResponse> list(UUID clanId, Pageable pageable) {
        clanRepository.findByIdAndActiveTrue(clanId).orElseThrow(() -> new ResourceNotFoundException("Clan", clanId));
        return toResponses(clanId, allianceRepository.findPageByClanIdAndStatus(clanId, ClanAllianceStatus.active,
                pageable));
    }

    public Page<ClanAllianceResponse> proposals(UUID clanId, Long playerId, Pageable pageable) {
        User viewer = accessService.player(playerId);
        accessService.require(clanId, viewer.getId(), ClanPermission.MANAGE_ALLIANCES);
        return toResponses(clanId, allianceRepository.findPageByClanIdAndStatus(clanId, ClanAllianceStatus.pending,
                pageable));
    }

    @Transactional
    public ClanAllianceResponse propose(UUID clanId, Long playerId, UUID allyId) {
        User actor = accessService.player(playerId);
        accessService.require(clanId, actor.getId(), ClanPermission.MANAGE_ALLIANCES);
        if (clanId.equals(allyId)) {
            throw new ValidationException("clanId", "must be another clan");
        }
        List<Clan> pair = lockPair(clanId, allyId);
        if (allianceRepository.existsOpenBetween(pair.get(0).getId(), pair.get(1).getId())) {
            throw new ConflictException("These clans already have an alliance or a proposal between them");
        }
        Clan proposer = pair.get(0).getId().equals(clanId) ? pair.get(0) : pair.get(1);
        assertFreeSlot(proposer);
        ClanAlliance alliance = allianceRepository.saveAndFlush(ClanAlliance.builder()
                .clanA(pair.get(0))
                .clanB(pair.get(1))
                .proposedByClan(proposer)
                .proposedByUser(actor)
                .build());
        return toResponse(alliance, clanId);
    }

    @Transactional
    public ClanAllianceResponse resolve(UUID allianceId, Long playerId, ClanAllianceStatus status) {
        User actor = accessService.player(playerId);
        ClanAlliance alliance = allianceRepository.findWithRefsById(allianceId)
                .orElseThrow(() -> new ResourceNotFoundException("ClanAlliance", allianceId));
        UUID side = managedSide(alliance, actor);
        switch (status) {
            case active -> accept(alliance, side, actor);
            case declined -> close(alliance, ClanAllianceStatus.pending, ClanAllianceStatus.declined, actor);
            case ended -> close(alliance, ClanAllianceStatus.active, ClanAllianceStatus.ended, actor);
            case pending -> throw new ValidationException("status", "must be active, declined or ended");
        }
        return toResponse(alliance, side);
    }

    @Transactional
    public void endAll(Clan clan, User actor) {
        List<ClanAlliance> open = allianceRepository.findOpenByClanId(clan.getId());
        Instant now = Instant.now();
        for (ClanAlliance alliance : open) {
            boolean active = alliance.getStatus() == ClanAllianceStatus.active;
            alliance.setStatus(active ? ClanAllianceStatus.ended : ClanAllianceStatus.declined);
            alliance.setEndedAt(now);
            alliance.setEndedByUser(actor);
            if (active) {
                audit(alliance.otherThan(clan.getId()), actor, ClanAuditAction.alliance_ended, clan);
            }
        }
        allianceRepository.saveAllAndFlush(open);
        strengthService.recompute(open.stream()
                .filter(a -> a.getStatus() == ClanAllianceStatus.ended)
                .map(a -> a.otherThan(clan.getId()).getId())
                .toList());
    }

    private Map<UUID, ClanTrustResponse> trustOf(Collection<ClanAlliance> alliances) {
        List<ClanAlliance> active = alliances.stream()
                .filter(a -> a.getStatus() == ClanAllianceStatus.active)
                .toList();
        if (active.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Double> contributions = allianceRepository
                .findTrustContributions(active.stream().map(ClanAlliance::getId).toList()).stream()
                .collect(Collectors.toMap(ClanAllianceRepository.TrustView::getAllianceId,
                        ClanAllianceRepository.TrustView::getContribution));
        Instant now = Instant.now();
        return active.stream().collect(Collectors.toMap(ClanAlliance::getId,
                a -> trust(Duration.between(a.getAcceptedAt(), now), contributions.getOrDefault(a.getId(), 0.0))));
    }

    private ClanTrustResponse trust(Duration age, double contribution) {
        List<ClanProperties.TrustTier> tiers = clanProperties.getTrustTiers();
        int level = 0;
        for (int i = 0; i < tiers.size(); i++) {
            ClanProperties.TrustTier tier = tiers.get(i);
            if (age.compareTo(tier.minAge()) >= 0 && contribution >= tier.minContribution()) {
                level = i;
            }
        }
        return new ClanTrustResponse(level, tiers.get(level).loanCap(), contribution);
    }

    private void accept(ClanAlliance alliance, UUID side, User actor) {
        requireStatus(alliance, ClanAllianceStatus.pending);
        if (alliance.getProposedByClan().getId().equals(side)) {
            throw new ForbiddenException("The other clan has to accept this proposal");
        }
        List<Clan> pair = lockPair(alliance.getClanA().getId(), alliance.getClanB().getId());
        pair.forEach(this::assertFreeSlot);
        alliance.setStatus(ClanAllianceStatus.active);
        alliance.setAcceptedAt(Instant.now());
        allianceRepository.saveAndFlush(alliance);
        audit(pair.get(0), actor, ClanAuditAction.alliance_formed, pair.get(1));
        audit(pair.get(1), actor, ClanAuditAction.alliance_formed, pair.get(0));
        strengthService.recompute(pair.stream().map(Clan::getId).toList());
    }

    private void close(ClanAlliance alliance, ClanAllianceStatus from, ClanAllianceStatus to, User actor) {
        requireStatus(alliance, from);
        alliance.setStatus(to);
        alliance.setEndedAt(Instant.now());
        alliance.setEndedByUser(actor);
        allianceRepository.saveAndFlush(alliance);
        if (to == ClanAllianceStatus.ended) {
            audit(alliance.getClanA(), actor, ClanAuditAction.alliance_ended, alliance.getClanB());
            audit(alliance.getClanB(), actor, ClanAuditAction.alliance_ended, alliance.getClanA());
            strengthService.recompute(List.of(alliance.getClanA().getId(), alliance.getClanB().getId()));
        }
    }

    private UUID managedSide(ClanAlliance alliance, User actor) {
        UUID clanId = memberRepository.findOpenByUserId(actor.getId())
                .map(ClanMember::getClan)
                .map(Clan::getId)
                .filter(alliance::involves)
                .orElseThrow(() -> new ForbiddenException("You are not in either clan of this alliance"));
        accessService.require(clanId, actor.getId(), ClanPermission.MANAGE_ALLIANCES);
        return clanId;
    }

    private List<Clan> lockPair(UUID first, UUID second) {
        return Stream.of(first, second).sorted(LOCK_ORDER).map(roster::lock).toList();
    }

    private void assertFreeSlot(Clan clan) {
        if (allianceRepository.countActiveByClanId(clan.getId())
                >= levelService.capacityOf(clan, ClanCapacity.ally_slots)) {
            throw new ConflictException(clan.getName() + " has no free ally slots");
        }
    }

    private void requireStatus(ClanAlliance alliance, ClanAllianceStatus expected) {
        if (alliance.getStatus() != expected) {
            throw new ConflictException("This alliance is " + alliance.getStatus());
        }
    }

    private void audit(Clan clan, User actor, ClanAuditAction action, Clan ally) {
        auditRepository.save(ClanAuditEntry.builder().clan(clan).actor(actor).action(action)
                .details(Map.<String, Object>of("clanId", ally.getId().toString(), "name", ally.getName(),
                        "tag", ally.getTag()))
                .build());
    }

    private ClanAllianceResponse toResponse(ClanAlliance alliance, UUID clanId) {
        UUID allyId = alliance.otherThan(clanId).getId();
        return ClanAllianceResponse.of(alliance, clanId,
                cosmeticService.equippedByClanIds(List.of(allyId)).getOrDefault(allyId, List.of()),
                trustOf(List.of(alliance)).get(alliance.getId()));
    }

    private Page<ClanAllianceResponse> toResponses(UUID clanId, Page<ClanAlliance> page) {
        Map<UUID, List<ItemResponse>> equipped = cosmeticService.equippedByClanIds(
                page.getContent().stream().map(a -> a.otherThan(clanId).getId()).distinct().toList());
        Map<UUID, ClanTrustResponse> trust = trustOf(page.getContent());
        return page.map(a -> ClanAllianceResponse.of(a, clanId,
                equipped.getOrDefault(a.otherThan(clanId).getId(), List.of()), trust.get(a.getId())));
    }
}
