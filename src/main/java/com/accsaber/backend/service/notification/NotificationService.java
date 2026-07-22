package com.accsaber.backend.service.notification;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.response.notification.NotificationResponse;
import com.accsaber.backend.model.entity.notification.Notification;
import com.accsaber.backend.model.entity.notification.NotificationType;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.event.NotificationCreatedEvent;
import com.accsaber.backend.repository.notification.NotificationRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.player.UserSettingsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final UserSettingsService userSettingsService;
    private final DuplicateUserService duplicateUserService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public boolean notify(Long userId, NotificationType type, Long actorId, String title, String linkTo) {
        if (userId == null) {
            return false;
        }
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        if (actorId != null && resolved.equals(duplicateUserService.resolvePrimaryUserId(actorId))) {
            return false;
        }
        if (!isEnabled(resolved, type)) {
            return false;
        }

        Notification saved = notificationRepository.save(Notification.builder()
                .user(userRepository.getReferenceById(resolved))
                .type(type)
                .title(title)
                .linkTo(linkTo)
                .build());

        eventPublisher.publishEvent(
                new NotificationCreatedEvent(resolved, NotificationMapper.toResponse(saved)));
        return true;
    }

    @Transactional
    public TestFireResult testFire(Long targetUserId, NotificationType type, String title, String linkTo) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(targetUserId);
        User target = userRepository.findByIdAndActiveTrue(resolved)
                .orElseThrow(() -> new ResourceNotFoundException("User", targetUserId));

        String effectiveTitle = (title == null || title.isBlank()) ? sampleTitle(type) : title;
        String effectiveLink = (linkTo == null || linkTo.isBlank()) ? sampleLink(type, resolved) : linkTo;

        boolean delivered = notify(resolved, type, null, effectiveTitle, effectiveLink);
        return new TestFireResult(resolved, target.getName(), type, effectiveTitle, effectiveLink, delivered,
                delivered ? null : "the player has " + type.preference().key() + " disabled");
    }

    private static String sampleTitle(NotificationType type) {
        return switch (type) {
            case trade_offer -> "You received a new trade offer";
            case trade_accepted -> "Your trade offer was accepted";
            case trade_declined -> "Your trade offer was declined";
            case market_sold -> "Alpha Crate sold for 500 essence";
            case market_bid -> "New bid of 250 essence on Alpha Crate";
            case market_outbid -> "You were outbid on Alpha Crate";
            case market_won -> "You won Alpha Crate for 500 essence";
            case item_earned -> "You received Alpha Crate!";
            case server -> "This is a test notification";
        };
    }

    private static String sampleLink(NotificationType type, Long targetUserId) {
        return switch (type) {
            case trade_offer, trade_accepted, trade_declined -> "/trade-offers";
            case market_sold, market_bid, market_outbid, market_won -> "/market";
            case item_earned -> "/players/" + targetUserId;
            case server -> null;
        };
    }

    public record TestFireResult(Long userId, String userName, NotificationType type, String title,
            String linkTo, boolean delivered, String suppressedReason) {
    }

    @Transactional
    public int broadcast(String title, String linkTo) {
        int delivered = notificationRepository.broadcast(title, linkTo);
        log.info("Broadcast '{}' delivered to {} users", title, delivered);
        return delivered;
    }

    public Page<NotificationResponse> findFeed(Long userId, boolean unreadOnly, Pageable pageable) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        return notificationRepository.findFeed(resolved, unreadOnly, pageable)
                .map(NotificationMapper::toResponse);
    }

    public long unreadCount(Long userId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        return notificationRepository.countByUser_IdAndReadAtIsNull(resolved);
    }

    @Transactional
    public void markRead(UUID notificationId, Long userId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        if (notificationRepository.markRead(notificationId, resolved, Instant.now()) == 0
                && !notificationRepository.existsById(notificationId)) {
            throw new ResourceNotFoundException("Notification", notificationId);
        }
    }

    @Transactional
    public int markAllRead(Long userId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        return notificationRepository.markAllRead(resolved, Instant.now());
    }

    @Transactional
    public int clearAll(Long userId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        return notificationRepository.deleteAllForUser(resolved);
    }

    private boolean isEnabled(Long resolvedUserId, NotificationType type) {
        Boolean enabled = userSettingsService.get(resolvedUserId, type.preference(), Boolean.class);
        return enabled == null || enabled;
    }
}
