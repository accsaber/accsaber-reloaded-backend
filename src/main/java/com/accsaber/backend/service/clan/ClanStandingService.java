package com.accsaber.backend.service.clan;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.response.clan.ClanStandingEventResponse;
import com.accsaber.backend.model.dto.response.clan.ClanStandingResponse;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanSeason;
import com.accsaber.backend.model.entity.clan.ClanSeasonResult;
import com.accsaber.backend.repository.clan.ClanRepository;
import com.accsaber.backend.repository.clan.ClanSeasonRepository;
import com.accsaber.backend.repository.clan.ClanSeasonResultRepository;
import com.accsaber.backend.repository.clan.ClanSeasonStandingRepository;
import com.accsaber.backend.repository.clan.ClanStandingEventRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClanStandingService {

    private static final String CURRENT_SEASON = "current";

    private final ClanRepository clanRepository;
    private final ClanSeasonRepository seasonRepository;
    private final ClanSeasonStandingRepository standingRepository;
    private final ClanSeasonResultRepository resultRepository;
    private final ClanStandingEventRepository eventRepository;
    private final ClanCosmeticService cosmeticService;
    private final ClanProperties clanProperties;

    public Optional<ClanSeason> currentSeason() {
        return seasonRepository.findCurrent(Instant.now());
    }

    public ClanSeason resolveSeason(String slugOrId) {
        if (slugOrId == null || CURRENT_SEASON.equals(slugOrId)) {
            return currentSeason().orElseThrow(() -> new ResourceNotFoundException("ClanSeason", CURRENT_SEASON));
        }
        UUID id = parseUuid(slugOrId);
        return (id != null ? seasonRepository.findById(id) : seasonRepository.findBySlug(slugOrId))
                .orElseThrow(() -> new ResourceNotFoundException("ClanSeason", slugOrId));
    }

    public double baseStanding(Clan clan) {
        return (clan.getRosterStrength() + clan.getAllyStrength()) * clanProperties.getStandingPerSkill();
    }

    public Map<UUID, Double> earnedInCurrentSeason(Collection<UUID> clanIds) {
        return currentSeason()
                .filter(season -> !clanIds.isEmpty())
                .map(season -> standingRepository.findEarned(season.getId(), clanIds).stream()
                        .collect(Collectors.toMap(ClanSeasonStandingRepository.EarnedView::getClanId,
                                ClanSeasonStandingRepository.EarnedView::getEarned)))
                .orElse(Map.of());
    }

    public Page<ClanStandingResponse> ranking(ClanSeason season, Pageable pageable) {
        if (season.getClosedAt() != null) {
            Page<ClanSeasonResult> results = resultRepository.findPageBySeasonId(season.getId(), pageable);
            Map<UUID, PublicClanResponse> refs = cosmeticService.publicRefs(
                    results.getContent().stream().map(ClanSeasonResult::getClan).toList());
            return results.map(r -> new ClanStandingResponse(refs.get(r.getClan().getId()), r.getRank(),
                    r.getBaseStanding() + r.getEarned(), r.getBaseStanding(), r.getEarned()));
        }
        Page<ClanSeasonStandingRepository.RankingRow> rows = standingRepository.findLiveRanking(season.getId(),
                clanProperties.getStandingPerSkill(), pageable);
        List<UUID> clanIds = rows.getContent().stream().map(ClanSeasonStandingRepository.RankingRow::getClanId)
                .toList();
        Map<UUID, PublicClanResponse> refs = cosmeticService.publicRefs(clanRepository.findAllById(clanIds));
        List<ClanStandingResponse> content = new ArrayList<>();
        for (int i = 0; i < rows.getContent().size(); i++) {
            ClanSeasonStandingRepository.RankingRow row = rows.getContent().get(i);
            content.add(new ClanStandingResponse(refs.get(row.getClanId()), pageable.getOffset() + i + 1,
                    row.getBaseStanding() + row.getEarned(), row.getBaseStanding(), row.getEarned()));
        }
        return new PageImpl<>(content, pageable, rows.getTotalElements());
    }

    public ClanStandingResponse standingOf(UUID clanId, String seasonSlugOrId) {
        Clan clan = clanRepository.findById(clanId).orElseThrow(() -> new ResourceNotFoundException("Clan", clanId));
        ClanSeason season = resolveSeason(seasonSlugOrId);
        PublicClanResponse ref = cosmeticService.publicRefs(List.of(clan)).get(clanId);
        if (season.getClosedAt() != null) {
            ClanSeasonResult result = resultRepository.findBySeason_IdAndClan_Id(season.getId(), clanId)
                    .orElseThrow(() -> new ResourceNotFoundException("ClanSeasonResult", clanId));
            return new ClanStandingResponse(ref, result.getRank(), result.getBaseStanding() + result.getEarned(),
                    result.getBaseStanding(), result.getEarned());
        }
        double base = baseStanding(clan);
        double earned = standingRepository.findEarned(season.getId(), List.of(clanId)).stream()
                .mapToDouble(ClanSeasonStandingRepository.EarnedView::getEarned).sum();
        long rank = standingRepository.findLiveRank(season.getId(), clanProperties.getStandingPerSkill(),
                base + earned, clan.getCreatedAt(), clanId);
        return new ClanStandingResponse(ref, rank, base + earned, base, earned);
    }

    public Page<ClanStandingEventResponse> events(UUID clanId, String seasonSlugOrId, Pageable pageable) {
        ClanSeason season = resolveSeason(seasonSlugOrId);
        return eventRepository.findBySeason_IdAndClan_IdOrderByCreatedAtDesc(season.getId(), clanId, pageable)
                .map(ClanStandingEventResponse::of);
    }

    public List<ClanSeasonStandingRepository.RankingRow> fullLiveRanking(UUID seasonId) {
        return standingRepository.findFullLiveRanking(seasonId, clanProperties.getStandingPerSkill());
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
