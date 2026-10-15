package com.accsaber.backend.service.campaign;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.accsaber.backend.config.PlainDoubleJackson2Module;
import com.accsaber.backend.model.dto.response.campaign.CampaignProgressBroadcast;
import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.event.CampaignCompletedEvent;
import com.accsaber.backend.model.event.CampaignNodeCompletedEvent;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.clan.ClanRefCache;
import com.accsaber.backend.websocket.server.CampaignProgressWebSocketHandler;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CampaignProgressBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(CampaignProgressBroadcastService.class);
    private static final ObjectMapper MAPPER = PlainDoubleJackson2Module.mapper()
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);

    private final CampaignProgressWebSocketHandler campaignProgressHandler;
    private final CampaignService campaignService;
    private final UserRepository userRepository;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNodeCompleted(CampaignNodeCompletedEvent event) {
        if (event.silent()) {
            return;
        }
        broadcast(new CampaignProgressBroadcast("node_completed", player(event.userId()),
                campaignService.getCampaignSummary(event.campaignId()),
                campaignService.getCampaignNode(event.nodeId()), event.completedAt()));
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCampaignCompleted(CampaignCompletedEvent event) {
        if (event.silent()) {
            return;
        }
        broadcast(new CampaignProgressBroadcast("campaign_completed", player(event.userId()),
                campaignService.getCampaignSummary(event.campaignId()), null, event.completedAt()));
    }

    private PlayerRef player(Long userId) {
        return userRepository.findByIdAndActiveTrue(userId).map(ClanRefCache::playerRef).orElse(null);
    }

    private void broadcast(CampaignProgressBroadcast payload) {
        try {
            campaignProgressHandler.broadcast(MAPPER.writeValueAsString(payload));
        } catch (Exception e) {
            log.error("Failed to broadcast campaign progress event: {}", e.getMessage());
        }
    }
}
