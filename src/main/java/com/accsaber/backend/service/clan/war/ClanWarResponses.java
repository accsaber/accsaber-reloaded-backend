package com.accsaber.backend.service.clan.war;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.accsaber.backend.model.dto.response.clan.ClanWarResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarSideResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarTeamResponse;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarSide;
import com.accsaber.backend.model.entity.clan.war.ClanWarTeam;
import com.accsaber.backend.repository.clan.war.ClanWarSideRepository;
import com.accsaber.backend.service.clan.ClanCosmeticService;
import com.accsaber.backend.service.clan.ClanRefCache;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ClanWarResponses {

    private final ClanWarSideRepository sideRepository;
    private final ClanCosmeticService cosmeticService;

    public ClanWarResponse of(ClanWar war) {
        return of(List.of(war)).getFirst();
    }

    public List<ClanWarResponse> of(List<ClanWar> wars) {
        if (wars.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<ClanWarSide>> sides = sideRepository.findByWarIds(wars.stream().map(ClanWar::getId).toList())
                .stream().collect(Collectors.groupingBy(side -> side.getWar().getId()));
        Map<UUID, PublicClanResponse> clans = cosmeticService.publicRefs(sides.values().stream()
                .flatMap(List::stream)
                .map(ClanWarSide::getClan)
                .toList());
        return wars.stream()
                .map(war -> toWarResponse(war, sides.getOrDefault(war.getId(), List.of()).stream()
                        .map(side -> toWarSideResponse(side, clans.get(side.getClan().getId())))
                        .toList()))
                .toList();
    }

    private static ClanWarResponse toWarResponse(ClanWar war, List<ClanWarSideResponse> sides) {
        return new ClanWarResponse(war.getId(), war.getArena(), war.getArenaSpec(), war.getRuleset(),
                war.getStatus(), war.getOutcome(), ClanRefCache.playerRef(war.getDeclaredBy()),
                ClanWarTeamResponse.of(war.getAttackerClan().getId(), ClanWarTeam.attack, sides),
                ClanWarTeamResponse.of(war.getDefenderClan().getId(), ClanWarTeam.defend, sides),
                war.getDeclaredAt(), war.getPicksDueAt(), war.getStartsAt(), war.getRetreatAt(), war.getWipedAt(), war.getEndedAt());
    }

    private static ClanWarSideResponse toWarSideResponse(ClanWarSide side, PublicClanResponse clan) {
        return new ClanWarSideResponse(clan, side.getTeam(), ClanRefCache.playerRef(side.getLeadUser()), side.getStake(),
                side.getStakeRemaining(), side.getStandingAtDeclare(), side.getPicksSubmittedAt(), side.getJoinedAt(),
                side.getLeftAt(), side.fallen());
    }
}
