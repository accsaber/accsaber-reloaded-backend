package com.accsaber.backend.service.map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.notification.NotificationType;
import com.accsaber.backend.model.entity.map.VoteType;
import com.accsaber.backend.model.event.MapDifficultyStatusChangedEvent;
import com.accsaber.backend.model.event.MapVoteCastEvent;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.map.MapDifficultySubscriptionRepository;
import com.accsaber.backend.service.notification.NotificationService;
import com.accsaber.backend.service.player.DuplicateUserService;

@ExtendWith(MockitoExtension.class)
class MapSubscriptionServiceTest {

    @Mock
    private MapDifficultySubscriptionRepository subscriptionRepository;

    @Mock
    private MapDifficultyRepository mapDifficultyRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private DuplicateUserService duplicateUserService;

    @InjectMocks
    private MapSubscriptionService service;

    @Test
    void aRejectionTellsFollowersTheReasonEvenWhenACampaignKeepsTheMap() {
        MapDifficulty difficulty = difficulty(MapDifficultyStatus.CAMPAIGN);
        difficulty.setDeactivatedAt(Instant.now());
        difficulty.setDeactivationReason("Overmapped");
        String title = "Song (Hard) was removed from ranking: Overmapped";
        String link = "/ranked-queue/" + difficulty.getId();
        when(subscriptionRepository.findUserIds(difficulty.getId())).thenReturn(List.of(7L));

        service.onStatusChanged(new MapDifficultyStatusChangedEvent(difficulty));

        verify(notificationService).notifyAll(eq(List.of(7L)), eq(NotificationType.map_followed), isNull(),
                eq(title), eq(link));
    }

    @Test
    void aChangedVoteTellsFollowersWhichWayItWent() {
        MapDifficulty difficulty = difficulty(MapDifficultyStatus.QUEUE);
        when(subscriptionRepository.findUserIds(difficulty.getId())).thenReturn(List.of(7L));

        service.onVoteCast(new MapVoteCastEvent(difficulty, VoteType.DOWNVOTE, true));

        verify(notificationService).notifyAll(eq(List.of(7L)), eq(NotificationType.map_followed), isNull(),
                eq("Vote changed on Song (Hard): downvote"), eq("/ranked-queue/" + difficulty.getId()));
    }

    @Test
    void aMoveToCampaignStatusWithoutARejectionNotifiesNobody() {
        service.onStatusChanged(new MapDifficultyStatusChangedEvent(difficulty(MapDifficultyStatus.CAMPAIGN)));

        verifyNoInteractions(notificationService, subscriptionRepository);
    }

    @Test
    void aFreshSuggestionNotifiesNobody() {
        service.onStatusChanged(new MapDifficultyStatusChangedEvent(difficulty(MapDifficultyStatus.SUGGESTED)));

        verifyNoInteractions(notificationService, subscriptionRepository);
    }

    private static MapDifficulty difficulty(MapDifficultyStatus status) {
        return MapDifficulty.builder().id(UUID.randomUUID())
                .map(Map.builder().id(UUID.randomUUID()).songName("Song").build())
                .difficulty(Difficulty.HARD).status(status).build();
    }
}
