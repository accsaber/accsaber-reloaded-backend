package com.accsaber.backend.service.clan.war;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.accsaber.backend.model.dto.response.clan.ClanWarResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarSideResponse;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarSide;
import com.accsaber.backend.repository.clan.war.ClanWarSideRepository;
import com.accsaber.backend.service.clan.ClanCosmeticService;

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
        Map<UUID, PublicClanResponse> clans = cosmeticService.publicRefs(wars.stream()
                .flatMap(war -> Stream.of(war.getAttackerClan(), war.getDefenderClan()))
                .toList());
        return wars.stream()
                .map(war -> ClanWarResponse.of(war, sides.getOrDefault(war.getId(), List.of()).stream()
                        .map(side -> ClanWarSideResponse.of(side, clans.get(side.getClan().getId())))
                        .toList()))
                .toList();
    }
}
