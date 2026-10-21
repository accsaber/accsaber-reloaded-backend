package com.accsaber.backend.service.clan;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.accsaber.backend.config.PlainDoubleJackson2Module;
import com.accsaber.backend.model.dto.response.clan.ClanPresenceBroadcast;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.entity.user.UserSettingKey;
import com.accsaber.backend.model.event.PresenceChangedEvent;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.UserSettingsService;
import com.accsaber.backend.websocket.server.ClanChatWebSocketHandler;
import com.accsaber.backend.websocket.server.NotificationWebSocketHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClanPresenceService {

    static final Duration OFFLINE_GRACE = Duration.ofSeconds(30);

    private static final ObjectMapper MAPPER = PlainDoubleJackson2Module.mapper();

    private final Map<Long, Instant> pendingOffline = new ConcurrentHashMap<>();

    private final UserRepository userRepository;
    private final NotificationWebSocketHandler notificationHandler;
    private final ClanChatWebSocketHandler chatHandler;
    private final UserSettingsService settingsService;

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

    public Set<Long> visibleOnline(Collection<Long> userIds) {
        Set<Long> online = notificationHandler.onlineAmong(userIds);
        if (online.isEmpty()) {
            return online;
        }
        Map<Long, Boolean> optedIn = settingsService.getMany(online, UserSettingKey.PRIVACY_SHOW_ONLINE_STATUS,
                Boolean.class);
        return online.stream().filter(optedIn::get).collect(Collectors.toSet());
    }

    private void announce(Long userId, boolean online) {
        PublicClanResponse clan = ClanRefCache.forUser(userId);
        if (clan == null || !settingsService.get(userId, UserSettingKey.PRIVACY_SHOW_ONLINE_STATUS, Boolean.class)) {
            return;
        }
        userRepository.findByIdAndActiveTrue(userId).ifPresent(user -> {
            try {
                chatHandler.broadcast(clan.id(),
                        MAPPER.writeValueAsString(ClanPresenceBroadcast.of(clan.id(), ClanRefCache.playerRef(user), online)));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize clan presence for user {}: {}", userId, e.getMessage());
            }
        });
    }
}
