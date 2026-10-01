package com.accsaber.backend.service.clan;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.accsaber.backend.config.PlainDoubleJackson2Module;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.event.PresenceChangedEvent;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.websocket.server.ClanChatWebSocketHandler;
import com.accsaber.backend.websocket.server.ClanPresenceBroadcast;
import com.accsaber.backend.websocket.server.NotificationWebSocketHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClanPresenceService {

    static final Duration OFFLINE_GRACE = Duration.ofSeconds(30);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(PlainDoubleJackson2Module.create())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final Map<Long, Instant> pendingOffline = new ConcurrentHashMap<>();

    private final UserRepository userRepository;
    private final NotificationWebSocketHandler notificationHandler;
    private final ClanChatWebSocketHandler chatHandler;

    @EventListener
    public void onPresenceChanged(PresenceChangedEvent event) {
        if (!event.online()) {
            pendingOffline.put(event.userId(), Instant.now().plus(OFFLINE_GRACE));
            return;
        }
        if (pendingOffline.remove(event.userId()) == null) {
            announce(event.userId(), true);
        }
    }

    @Scheduled(fixedDelay = 5_000)
    public void flushOffline() {
        flushOffline(Instant.now());
    }

    void flushOffline(Instant now) {
        pendingOffline.forEach((userId, due) -> {
            if (due.isAfter(now) || !pendingOffline.remove(userId, due)) {
                return;
            }
            if (notificationHandler.onlineAmong(List.of(userId)).isEmpty()) {
                announce(userId, false);
            }
        });
    }

    private void announce(Long userId, boolean online) {
        PublicClanResponse clan = ClanRefCache.forUser(userId);
        if (clan == null) {
            return;
        }
        userRepository.findByIdAndActiveTrue(userId).ifPresent(user -> {
            try {
                chatHandler.broadcast(clan.id(),
                        MAPPER.writeValueAsString(ClanPresenceBroadcast.of(clan.id(), PlayerRef.of(user), online)));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize clan presence for user {}: {}", userId, e.getMessage());
            }
        });
    }
}
