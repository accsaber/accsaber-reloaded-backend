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
