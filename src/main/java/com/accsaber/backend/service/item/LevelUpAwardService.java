package com.accsaber.backend.service.item;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.mission.MissionTemplate;
import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.repository.milestone.LevelThresholdRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.milestone.LevelService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LevelUpAwardService {

    private final LevelService levelService;
    private final LevelThresholdRepository levelThresholdRepository;
    private final ItemRepository itemRepository;
    private final ItemService itemService;
    private final UserRepository userRepository;

    @Transactional
    public void addXp(Long userId, Double delta) {
        if (delta == null)
            return;
        Double oldXp = userRepository.findTotalXpById(userId).orElse(0.0);
        userRepository.addXp(userId, delta);
        processLevelUps(userId, oldXp, delta);
    }

    @Transactional
    public void addMissionXp(Long userId, MissionTemplate template, Double delta) {
        if (template.isEventTied()) {
            addEventXp(userId, delta);
            return;
        }
        if (delta == null || Math.signum(delta) <= 0)
            return;
        userRepository.addMissionXp(userId, delta);
        addXp(userId, delta);
    }

    @Transactional
    public void addCampaignXp(Long userId, Double delta) {
        if (delta == null || Math.signum(delta) <= 0)
            return;
        userRepository.addCampaignXp(userId, delta);
        addXp(userId, delta);
    }

    @Transactional
    public void addEventXp(Long userId, Double delta) {
        if (delta == null || Math.signum(delta) <= 0)
            return;
        userRepository.addEventXp(userId, delta);
        addXp(userId, delta);
    }

    @Transactional
    public void processLevelUps(Long userId, Double oldXp, Double xpDelta) {
        if (xpDelta == null || Math.signum(xpDelta) <= 0)
            return;
        Double previous = oldXp == null ? 0.0 : oldXp;
        Double next = (previous + xpDelta);

        int oldLevel = levelService.calculateLevel(previous).getLevel();
        int newLevel = levelService.calculateLevel(next).getLevel();

        int from = Math.signum(previous) == 0 ? oldLevel : oldLevel + 1;
        if (from > newLevel)
            return;

        for (int level = from; level <= newLevel; level++) {
            grantLevelRewards(userId, level);
        }
    }

    @Transactional
    public void grantLevelRewards(Long userId, int level) {
        levelThresholdRepository.findById(level)
                .filter(t -> t.getAwardsItem() != null)
                .ifPresent(t -> itemService.awardSystem(
                        userId,
                        t.getAwardsItem().getId(),
                        ItemSource.level,
                        String.valueOf(level),
                        "Reached level " + level));

        itemRepository.findByUnlockLevelAndActiveTrue(level).forEach(item ->
                itemService.awardSystem(
                        userId,
                        item.getId(),
                        ItemSource.level,
                        String.valueOf(level),
                        "Reached level " + level));
    }
}
