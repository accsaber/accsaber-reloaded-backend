package com.accsaber.backend.service.infra;

import java.util.function.Consumer;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.accsaber.backend.config.PlainDoubleJackson2Module;
import com.accsaber.backend.model.dto.response.chat.ChatBroadcast;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.event.ChatMessageEvent;
import com.accsaber.backend.model.event.ClanFeedEvent;
import com.accsaber.backend.model.event.CrateOpenedEvent;
import com.accsaber.backend.model.event.MarketListingEvent;
import com.accsaber.backend.model.event.MilestoneCompletedEvent;
import com.accsaber.backend.model.event.MissionCompletedEvent;
import com.accsaber.backend.model.event.NotificationCreatedEvent;
import com.accsaber.backend.model.event.ScoreSubmittedEvent;
import com.accsaber.backend.websocket.server.ClanFeedWebSocketHandler;
import com.accsaber.backend.websocket.server.ClanGlobalFeedWebSocketHandler;
import com.accsaber.backend.websocket.server.CrateFeedWebSocketHandler;
import com.accsaber.backend.websocket.server.MarketFeedWebSocketHandler;
import com.accsaber.backend.websocket.server.MilestoneFeedWebSocketHandler;
import com.accsaber.backend.websocket.server.MissionFeedWebSocketHandler;
import com.accsaber.backend.websocket.server.NotificationWebSocketHandler;
import com.accsaber.backend.websocket.server.ScoreFeedWebSocketHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedBroadcastService {

    private static final ObjectMapper MAPPER = PlainDoubleJackson2Module.mapper();

    private final ScoreFeedWebSocketHandler scoreFeedHandler;
    private final CrateFeedWebSocketHandler crateFeedHandler;
    private final MarketFeedWebSocketHandler marketFeedHandler;
    private final MilestoneFeedWebSocketHandler milestoneFeedHandler;
    private final MissionFeedWebSocketHandler missionFeedHandler;
    private final NotificationWebSocketHandler notificationHandler;
    private final ClanFeedWebSocketHandler clanFeedHandler;
    private final ClanGlobalFeedWebSocketHandler clanGlobalFeedHandler;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onScoreSubmitted(ScoreSubmittedEvent event) {
        ScoreResponse score = event.score();
        if (score == null || score.isPartial() || !score.isActive()) {
            return;
        }
        send("score", score, scoreFeedHandler::broadcast);
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCrateOpened(CrateOpenedEvent event) {
        send("crate open", event.payload(), crateFeedHandler::broadcast);
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMarketListingEvent(MarketListingEvent event) {
        send("market event", event, json -> marketFeedHandler.broadcast(event.listingId(), json));
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMilestoneCompleted(MilestoneCompletedEvent event) {
        send("milestone completion", event.payload(), milestoneFeedHandler::broadcast);
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMissionCompleted(MissionCompletedEvent event) {
        send("mission completion", event.payload(), missionFeedHandler::broadcast);
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        send("notification", event.notification(), json -> notificationHandler.sendToUser(event.userId(), json));
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatMessage(ChatMessageEvent event) {
        send("chat message", ChatBroadcast.of(event.channelId(), event.message()), event.sink());
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onClanFeed(ClanFeedEvent event) {
        send("clan feed payload", event.payload(), json -> {
            if (event.clanIds().isEmpty()) {
                clanGlobalFeedHandler.broadcast(json);
            }
            event.clanIds().forEach(clanId -> clanFeedHandler.broadcast(clanId, json));
        });
    }

    private static void send(String what, Object payload, Consumer<String> sink) {
        try {
            sink.accept(MAPPER.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize {} for broadcast: {}", what, e.getMessage());
        }
    }
}
