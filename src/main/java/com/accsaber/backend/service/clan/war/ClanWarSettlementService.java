package com.accsaber.backend.service.clan.war;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanItemSource;
import com.accsaber.backend.model.entity.clan.ClanXpSource;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarParticipant;
import com.accsaber.backend.model.entity.clan.war.ClanWarRewardItem;
import com.accsaber.backend.model.entity.clan.war.ClanWarStatus;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.event.ClanWarEndedEvent;
import com.accsaber.backend.repository.clan.ClanItemRepository;
import com.accsaber.backend.repository.clan.war.ClanWarParticipantRepository;
import com.accsaber.backend.repository.clan.war.ClanWarRepository;
import com.accsaber.backend.repository.clan.war.ClanWarRewardItemRepository;
import com.accsaber.backend.service.clan.ClanCosmeticService;
import com.accsaber.backend.service.clan.ClanLevelService;
import com.accsaber.backend.service.clan.ClanXpAward;
import com.accsaber.backend.service.item.ItemService;
import com.accsaber.backend.service.item.LevelUpAwardService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClanWarSettlementService {

    private final ClanWarRepository warRepository;
    private final ClanWarParticipantRepository participantRepository;
    private final ClanWarRewardItemRepository rewardItemRepository;
    private final ClanItemRepository clanItemRepository;
    private final ClanLevelService levelService;
    private final LevelUpAwardService levelUpAwardService;
    private final ItemService itemService;
    private final ClanProperties clanProperties;
    private final TransactionTemplate transactionTemplate;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWarEnded(ClanWarEndedEvent event) {
        settle(event.warId());
    }

    public void settle(UUID warId) {
        ClanWar war = warRepository.findWithRefsById(warId).orElse(null);
        if (war == null || war.getStatus() != ClanWarStatus.ended) {
            return;
        }
        Clan winner = winner(war);
        List<ClanWarRewardItem> rewards = winner == null ? List.of() : rewardItemRepository.findActiveWithItems();
        if (winner != null) {
            levelService.grantXp(winner.getId(), new ClanXpAward(clanProperties.getWar().getWinClanXp(),
                    ClanXpSource.war_win, warId.toString(), true));
            transactionTemplate.executeWithoutResult(status -> rewards.stream()
                    .filter(reward -> ClanCosmeticService.isClanCosmetic(reward.getItem().getType()))
                    .forEach(reward -> clanItemRepository.grantItem(winner.getId(), reward.getItem().getId(),
                            ClanItemSource.war.name(), warId.toString())));
        }
        int winnerRank = 0;
        for (ClanWarParticipant participant : participantRepository.findByWarIdInContributionOrder(warId)) {
            boolean earnsItems = winner != null && participant.getClan().getId().equals(winner.getId())
                    && participant.getContribution() > 0;
            int rank = earnsItems ? ++winnerRank : 0;
            if (participant.getRewardedAt() == null) {
                pay(war, participant, rewards, rank);
            }
        }
    }

    private void pay(ClanWar war, ClanWarParticipant participant, List<ClanWarRewardItem> rewards, int winnerRank) {
        Long userId = participant.getUser().getId();
        double xp = participant.getContribution() * clanProperties.getWar().getXpPerContribution();
        try {
            transactionTemplate.executeWithoutResult(status -> {
                if (participantRepository.markRewarded(war.getId(), userId, xp, Instant.now()) == 0) {
                    return;
                }
                if (xp > 0) {
                    levelUpAwardService.addXp(userId, xp);
                }
                if (winnerRank == 0) {
                    return;
                }
                rewards.stream()
                        .filter(reward -> !ClanCosmeticService.isClanCosmetic(reward.getItem().getType()))
                        .filter(reward -> reward.getTopContributors() == null
                                || winnerRank <= reward.getTopContributors())
                        .forEach(reward -> itemService.awardSystem(userId, reward.getItem().getId(),
                                ItemSource.clan_war, war.getId().toString(), "Clan war reward: "
                                        + war.getAttackerClan().getTag() + " vs " + war.getDefenderClan().getTag(),
                                reward.getQuantity()));
            });
        } catch (RuntimeException e) {
            log.warn("Clan war {} could not pay user {}: {}", war.getId(), userId, e.getMessage());
        }
    }

    static Clan winner(ClanWar war) {
        return switch (war.getOutcome()) {
            case attacker_won -> war.getAttackerClan();
            case defender_won -> war.getDefenderClan();
            case forfeited -> war.getAttackerClan().isActive() == war.getDefenderClan().isActive() ? null
                    : war.getAttackerClan().isActive() ? war.getAttackerClan() : war.getDefenderClan();
            case drawn, retreated, season_ended -> null;
        };
    }
}
