package com.accsaber.backend.service.clan;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.accsaber.backend.model.dto.response.clan.ClanSeasonClosedResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarHitResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarResponse;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarHit;
import com.accsaber.backend.model.event.ClanFeedEvent;
import com.accsaber.backend.service.clan.war.ClanWarResponses;
import com.accsaber.backend.service.map.MapService;
import com.accsaber.backend.websocket.server.ClanFeedBroadcast;
import com.accsaber.backend.websocket.server.ClanFeedType;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ClanFeed {

    private final ClanWarResponses warResponses;
    private final ClanCosmeticService cosmeticService;
    private final MapService mapService;
    private final ApplicationEventPublisher eventPublisher;

    public void war(ClanWar war) {
        war(war, null);
    }

    public void war(ClanWar war, ClanFeedType globalType) {
        ClanWarResponse response = warResponses.of(war);
        eventPublisher.publishEvent(new ClanFeedEvent(sides(war), ClanFeedBroadcast.war(war.getId(), response)));
        if (globalType != null) {
            everyone(new ClanFeedBroadcast(globalType, war.getId(), response));
        }
    }

    public void hit(ClanWar war, ClanWarHit hit) {
        UUID difficultyId = hit.getMapDifficulty().getId();
        ClanWarHitResponse response = ClanWarHitResponse.of(hit,
                mapService.getDifficultyResponsesPublic(List.of(difficultyId)).get(difficultyId));
        eventPublisher.publishEvent(new ClanFeedEvent(sides(war), ClanFeedBroadcast.hit(war.getId(), response)));
        if (hit.isBroke()) {
            everyone(new ClanFeedBroadcast(ClanFeedType.war_break, war.getId(), response));
        }
    }

    public void clans(ClanFeedType type, Clan... clans) {
        List<Clan> involved = List.of(clans);
        Map<UUID, PublicClanResponse> refs = cosmeticService.publicRefs(involved);
        everyone(new ClanFeedBroadcast(type, null, involved.stream().map(clan -> refs.get(clan.getId())).toList()));
    }

    public void seasonClosed(ClanSeasonClosedResponse closed) {
        everyone(new ClanFeedBroadcast(ClanFeedType.season_closed, null, closed));
    }

    private void everyone(ClanFeedBroadcast payload) {
        eventPublisher.publishEvent(new ClanFeedEvent(List.of(), payload));
    }

    private static List<UUID> sides(ClanWar war) {
        return List.of(war.getAttackerClan().getId(), war.getDefenderClan().getId());
    }
}
