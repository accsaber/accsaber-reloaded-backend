package com.accsaber.backend.service.map;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.model.dto.response.map.MapSubscriptionResponse;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultySubscription;
import com.accsaber.backend.model.entity.notification.NotificationType;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.event.MapDifficultyStatusChangedEvent;
import com.accsaber.backend.model.event.MapVoteCastEvent;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.map.MapDifficultySubscriptionRepository;
import com.accsaber.backend.service.notification.NotificationService;
import com.accsaber.backend.service.player.DuplicateUserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MapSubscriptionService {

    private final MapDifficultySubscriptionRepository subscriptionRepository;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final NotificationService notificationService;
    private final DuplicateUserService duplicateUserService;

    @Transactional
    public void subscribe(UUID difficultyId, Long playerId) {
        mapDifficultyRepository.requireCommentable(difficultyId);
        subscriptionRepository.subscribe(difficultyId, duplicateUserService.resolvePrimaryUserId(playerId));
    }

    @Transactional
    public void unsubscribe(UUID difficultyId, Long playerId) {
        subscriptionRepository.deleteById(key(difficultyId, playerId));
    }

    @Transactional(readOnly = true)
    public MapSubscriptionResponse status(UUID difficultyId, Long playerId) {
        return new MapSubscriptionResponse(subscriptionRepository.existsById(key(difficultyId, playerId)));
    }

    public void commented(MapDifficulty difficulty, User author) {
        notifyFollowers(difficulty, author.getId(), label -> "New comments on " + label);
    }

    @EventListener
    public void onVoteCast(MapVoteCastEvent event) {
        String vote = event.vote().name().toLowerCase(Locale.ROOT);
        notifyFollowers(event.difficulty(), null,
                label -> (event.updated() ? "Vote changed on " : "New vote on ") + label + ": " + vote);
    }

    @EventListener
    public void onStatusChanged(MapDifficultyStatusChangedEvent event) {
        MapDifficulty difficulty = event.difficulty();
        String change = describe(difficulty);
        if (change != null) {
            notifyFollowers(difficulty, null, label -> label + " " + change);
        }
    }

    private static String describe(MapDifficulty difficulty) {
        if (difficulty.getDeactivatedAt() != null) {
            String reason = difficulty.getDeactivationReason();
            return reason == null ? "was removed from ranking"
                    : "was removed from ranking: " + reason.substring(0, Math.min(reason.length(), 140));
        }
        return switch (difficulty.getStatus()) {
            case QUEUE -> "is now in the ranking queue";
            case QUALIFIED -> "is now qualified";
            case RANKED -> "is now ranked";
            default -> null;
        };
    }

    private void notifyFollowers(MapDifficulty difficulty, Long actorId, UnaryOperator<String> title) {
        List<Long> followers = subscriptionRepository.findUserIds(difficulty.getId());
        if (followers.isEmpty()) {
            return;
        }
        String label = difficulty.getMap().getSongName() + " (" + difficulty.getDifficulty().getDbValue() + ")";
        notificationService.notifyAll(followers, NotificationType.map_followed, actorId, title.apply(label),
                "/ranked-queue/" + difficulty.getId());
    }

    private MapDifficultySubscription.Key key(UUID difficultyId, Long playerId) {
        return new MapDifficultySubscription.Key(difficultyId, duplicateUserService.resolvePrimaryUserId(playerId));
    }
}
