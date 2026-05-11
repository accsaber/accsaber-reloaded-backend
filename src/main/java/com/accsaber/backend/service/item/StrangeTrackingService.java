package com.accsaber.backend.service.item;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.accsaber.backend.model.entity.item.ItemModifier;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.model.event.ScoreSubmittedEvent;
import com.accsaber.backend.repository.item.UserItemLinkCounterRepository;
import com.accsaber.backend.repository.item.UserItemLinkRepository;
import com.accsaber.backend.repository.user.UserSettingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StrangeTrackingService {

    public static final String STAT_PLAY_COUNT = "play_count";

    private static final Logger log = LoggerFactory.getLogger(StrangeTrackingService.class);

    private final UserSettingRepository userSettingRepository;
    private final UserItemLinkRepository userItemLinkRepository;
    private final UserItemLinkCounterRepository counterRepository;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onScoreSubmitted(ScoreSubmittedEvent event) {
        try {
            Long userId = Long.parseLong(event.score().getUserId());
            incrementForUser(userId, STAT_PLAY_COUNT, 1L);
        } catch (NumberFormatException ex) {
            log.warn("Cannot parse userId from ScoreSubmittedEvent: {}", event.score().getUserId());
        } catch (Exception ex) {
            log.error("Strange tracking failed for score event", ex);
        }
    }

    private void incrementForUser(Long userId, String statKey, long delta) {
        List<UUID> equippedLinkIds = userSettingRepository.findByUser_IdAndKeyPrefix(userId, "equipped.").stream()
                .map(s -> {
                    try {
                        return UUID.fromString(s.getValue().asText());
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
        if (equippedLinkIds.isEmpty())
            return;

        for (UserItemLink link : userItemLinkRepository.findAllById(equippedLinkIds)) {
            if (carriesStrange(link)) {
                counterRepository.incrementBy(link.getId(), statKey, delta);
            }
        }
    }

    private static boolean carriesStrange(UserItemLink link) {
        for (ItemModifier m : link.getModifiers()) {
            if (ItemModifier.STRANGE.equals(m.getKey())) {
                return true;
            }
        }
        return false;
    }
}
