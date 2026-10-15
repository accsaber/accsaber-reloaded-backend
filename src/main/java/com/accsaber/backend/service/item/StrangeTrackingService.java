package com.accsaber.backend.service.item;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.accsaber.backend.model.entity.item.ItemModifier;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.repository.item.UserItemLinkCounterRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StrangeTrackingService {

    public static final String STAT_PLAY_COUNT = "play_count";

    private static final Logger log = LoggerFactory.getLogger(StrangeTrackingService.class);

    private final ItemEquipService itemEquipService;
    private final UserItemLinkCounterRepository counterRepository;

    public void recordPlay(Long userId) {
        try {
            incrementForUser(userId, STAT_PLAY_COUNT, 1L);
        } catch (Exception ex) {
            log.error("Strange tracking failed for user {}", userId, ex);
        }
    }

    private void incrementForUser(Long userId, String statKey, long delta) {
        for (UserItemLink link : itemEquipService.findEffectiveEquippedLinks(userId)) {
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
