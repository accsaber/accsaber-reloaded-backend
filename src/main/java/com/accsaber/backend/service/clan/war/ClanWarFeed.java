package com.accsaber.backend.service.clan.war;

import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.accsaber.backend.model.dto.response.clan.ClanWarHitResponse;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarHit;
import com.accsaber.backend.model.event.ClanFeedEvent;
import com.accsaber.backend.service.map.MapService;
import com.accsaber.backend.websocket.server.ClanFeedBroadcast;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ClanWarFeed {

    private final ClanWarResponses warResponses;
    private final MapService mapService;
    private final ApplicationEventPublisher eventPublisher;

    public void war(ClanWar war) {
        eventPublisher.publishEvent(new ClanFeedEvent(sides(war), ClanFeedBroadcast.war(war.getId(),
                warResponses.of(war))));
    }

    public void hit(ClanWar war, ClanWarHit hit) {
        UUID difficultyId = hit.getMapDifficulty().getId();
        ClanWarHitResponse response = ClanWarHitResponse.of(hit,
                mapService.getDifficultyResponsesPublic(List.of(difficultyId)).get(difficultyId));
        eventPublisher.publishEvent(new ClanFeedEvent(sides(war), ClanFeedBroadcast.hit(war.getId(), response)));
    }

    private static List<UUID> sides(ClanWar war) {
        return List.of(war.getAttackerClan().getId(), war.getDefenderClan().getId());
    }
}
