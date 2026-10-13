package com.accsaber.backend.service.clan.war;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.model.entity.clan.ClanItemSource;
import com.accsaber.backend.model.entity.clan.ClanXpSource;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarParticipant;
import com.accsaber.backend.model.entity.clan.war.ClanWarRewardItem;
import com.accsaber.backend.model.entity.clan.war.ClanWarSide;
import com.accsaber.backend.model.entity.clan.war.ClanWarStatus;
import com.accsaber.backend.model.entity.clan.war.ClanWarTeam;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.event.ClanWarEndedEvent;
import com.accsaber.backend.repository.clan.ClanItemRepository;
import com.accsaber.backend.repository.clan.war.ClanWarParticipantRepository;
import com.accsaber.backend.repository.clan.war.ClanWarRepository;
import com.accsaber.backend.repository.clan.war.ClanWarRewardItemRepository;
import com.accsaber.backend.repository.clan.war.ClanWarSideRepository;
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
    private final ClanWarSideRepository sideRepository;
    private final ClanWarStakeService stakeService;
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
        List<ClanWarSide> sides = sideRepository.findByWarId(warId);
        ClanWarTeam winning = winningTeam(war);
        List<ClanWarSide> winners = sides.stream()
                .filter(side -> side.getTeam() == winning && side.getLeftAt() == null)
                .toList();
        Map<ClanWarTeam, Double> engagement = new EnumMap<>(ClanWarTeam.class);
        for (ClanWarTeam team : ClanWarTeam.values()) {
            engagement.put(team, stakeService.engagement(war, team));
        }
        List<ClanWarRewardItem> rewards = winners.isEmpty() ? List.of() : rewardItemRepository.findActiveWithItems();
        if (!winners.isEmpty()) {
            payWinners(war, winners, rewards, engagement.get(winning.opponent()));
        }
        Map<UUID, ClanWarTeam> teamOf = sides.stream()
                .collect(Collectors.toMap(side -> side.getClan().getId(), ClanWarSide::getTeam));
        Set<UUID> winnerClans = winners.stream().map(side -> side.getClan().getId()).collect(Collectors.toSet());
        int winnerRank = 0;
        for (ClanWarParticipant participant : participantRepository.findByWarIdInContributionOrder(warId)) {
            UUID clanId = participant.getClan().getId();
            boolean earnsItems = winnerClans.contains(clanId) && participant.getContribution() > 0;
            int rank = earnsItems ? ++winnerRank : 0;
            if (participant.getRewardedAt() == null) {
                pay(war, participant, rewards, rank, engagement.get(teamOf.get(clanId).opponent()));
            }
        }
    }

    private void payWinners(ClanWar war, List<ClanWarSide> winners, List<ClanWarRewardItem> rewards,
            double engagement) {
        double total = winners.stream().mapToDouble(ClanWarSide::getStake).sum();
        String sourceId = war.getId().toString();
        for (ClanWarSide side : winners) {
            double share = total > 0 ? side.getStake() / total : 1.0 / winners.size();
            levelService.grantXp(side.getClan().getId(), new ClanXpAward(
                    clanProperties.getWar().getWinClanXp() * share * engagement, ClanXpSource.war_win, sourceId, true));
        }
        transactionTemplate.executeWithoutResult(status -> rewards.stream()
                .filter(reward -> reward.getItem().getType().isClanCosmetic())
                .forEach(reward -> winners.forEach(side -> clanItemRepository.grantItem(side.getClan().getId(),
                        reward.getItem().getId(), ClanItemSource.war.name(), sourceId))));
    }

    private void pay(ClanWar war, ClanWarParticipant participant, List<ClanWarRewardItem> rewards, int winnerRank,
            double engagement) {
        Long userId = participant.getUser().getId();
        double xp = participant.getContribution() * clanProperties.getWar().getXpPerContribution() * engagement;
        try {
            transactionTemplate.executeWithoutResult(status -> {
                if (participantRepository.markRewarded(war.getId(), userId, xp, Instant.now()) == 0) {
                    return;
                }
                if (xp > 0) {
                    levelUpAwardService.addClanXp(userId, xp);
                }
                if (winnerRank == 0) {
                    return;
                }
                rewards.stream()
                        .filter(reward -> !reward.getItem().getType().isClanCosmetic())
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

    static ClanWarTeam winningTeam(ClanWar war) {
        return switch (war.getOutcome()) {
            case attacker_won -> ClanWarTeam.attack;
            case defender_won -> ClanWarTeam.defend;
            case forfeited -> war.getAttackerClan().isActive() == war.getDefenderClan().isActive() ? null
                    : war.getAttackerClan().isActive() ? ClanWarTeam.attack : ClanWarTeam.defend;
            case drawn, retreated, season_ended -> null;
        };
    }
}
