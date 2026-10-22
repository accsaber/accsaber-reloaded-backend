package com.accsaber.backend.service.clan.war;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.model.entity.clan.ClanStandingSource;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarParticipant;
import com.accsaber.backend.model.entity.clan.war.ClanWarSide;
import com.accsaber.backend.model.entity.clan.war.ClanWarTeam;
import com.accsaber.backend.repository.clan.war.ClanWarParticipantRepository;
import com.accsaber.backend.repository.clan.war.ClanWarSideRepository;
import com.accsaber.backend.service.clan.ClanStandingService;
import com.accsaber.backend.service.clan.ClanStrengthService;
import com.accsaber.backend.service.clan.ClanStrengthService.MemberStrength;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClanWarStakeService {

    private final ClanWarSideRepository sideRepository;
    private final ClanWarParticipantRepository participantRepository;
    private final ClanStrengthService strengthService;
    private final ClanStandingService standingService;
    private final ClanProperties clanProperties;

    public void rebalance(List<ClanWarSide> sides) {
        Map<ClanWarTeam, List<ClanWarSide>> fighting = fightingByTeam(sides);
        Map<ClanWarTeam, Double> standing = new EnumMap<>(ClanWarTeam.class);
        Map<ClanWarTeam, Map<UUID, Double>> shares = new EnumMap<>(ClanWarTeam.class);
        Map<ClanWarTeam, Double> atRisk = new EnumMap<>(ClanWarTeam.class);
        for (ClanWarTeam team : ClanWarTeam.values()) {
            standing.put(team, fighting.get(team).stream()
                    .mapToDouble(side -> standingService.currentStanding(side.getClan()) * risk(side))
                    .sum());
            Map<UUID, Double> teamShares = weightShares(fighting.get(team));
            shares.put(team, teamShares);
            atRisk.put(team, fighting.get(team).stream()
                    .mapToDouble(side -> teamShares.getOrDefault(side.getClan().getId(), 0.0) * risk(side))
                    .sum());
        }
        double exposure = Math.min(standing.get(ClanWarTeam.attack), standing.get(ClanWarTeam.defend))
                * Math.min(atRisk.get(ClanWarTeam.attack), atRisk.get(ClanWarTeam.defend));
        for (ClanWarTeam team : ClanWarTeam.values()) {
            if (atRisk.get(team) <= 0) {
                continue;
            }
            for (ClanWarSide side : fighting.get(team)) {
                double growth = exposure * shares.get(team).getOrDefault(side.getClan().getId(), 0.0) * risk(side)
                        / atRisk.get(team) - side.getStake();
                if (growth > 0) {
                    side.setStake(side.getStake() + growth);
                    side.setStakeRemaining(side.getStakeRemaining() + growth);
                }
            }
        }
        sideRepository.saveAll(sides);
    }

    public double fullTeamStake(List<ClanWarSide> sides, ClanWarTeam team) {
        return sides.stream().filter(side -> side.getTeam() == team).mapToDouble(side -> side.getStake() / risk(side))
                .sum();
    }

    public double breakAmount(List<ClanWarSide> sides, ClanWarSide victimSide, ClanWarParticipant victim) {
        ClanProperties.War config = clanProperties.getWar();
        double moved = fullTeamStake(sides, victimSide.getTeam()) * config.getBreakShare() * victim.getStandingWeight()
                * Math.pow(config.getBreakDecay(), victim.getBreaksSuffered()) * risk(victimSide);
        return Math.min(victimSide.getStakeRemaining(), Math.max(0.0, moved));
    }

    public double risk(ClanWarSide side) {
        return side.getWar().leadTeamOf(side.getClan().getId()) != null ? 1.0
                : clanProperties.getWar().getAllyStakeShare();
    }

    public boolean lost(List<ClanWarSide> sides, ClanWarTeam team) {
        return sides.stream().noneMatch(side -> side.getTeam() == team && side.fighting() && side.getStake() > 0);
    }

    public double drainedShare(List<ClanWarSide> sides, ClanWarTeam team) {
        double total = sides.stream().filter(side -> side.getTeam() == team).mapToDouble(ClanWarSide::getStake).sum();
        if (total <= 0) {
            return 0.0;
        }
        double remaining = sides.stream()
                .filter(side -> side.getTeam() == team && side.getLeftAt() == null)
                .mapToDouble(ClanWarSide::getStakeRemaining)
                .sum();
        return 1.0 - remaining / total;
    }

    public double engagement(ClanWar war, ClanWarTeam team) {
        ClanProperties.War config = clanProperties.getWar();
        Instant from = war.getStartsAt() != null ? war.getStartsAt() : Instant.now();
        double active = participantRepository.findRecentlyActiveShare(war.getId(), team.name(),
                from.minus(config.getEngagementWindow()));
        return Math.min(1.0, active / config.getEngagementFull());
    }

    public void forfeit(ClanWar war, List<ClanWarSide> sides, ClanWarSide leaving) {
        double left = leaving.getStakeRemaining();
        leaving.setStakeRemaining(0.0);
        leaving.setLeftAt(Instant.now());
        List<ClanWarSide> enemies = fightingByTeam(sides).get(leaving.getTeam().opponent());
        double enemyStake = enemies.stream().mapToDouble(ClanWarSide::getStake).sum();
        if (left > 0 && enemyStake > 0) {
            UUID seasonId = war.getSeason().getId();
            String sourceId = "forfeit:" + war.getId() + ":" + leaving.getClan().getId();
            Map<UUID, Double> deltas = new HashMap<>();
            deltas.put(leaving.getClan().getId(), -left);
            for (ClanWarSide enemy : enemies) {
                deltas.put(enemy.getClan().getId(), left * enemy.getStake() / enemyStake);
            }
            standingService.applyAll(seasonId, deltas, ClanStandingSource.war_break, sourceId);
        }
        sideRepository.saveAll(sides);
    }

    public Map<UUID, Double> winShares(List<ClanWarSide> winners) {
        double total = winners.stream().mapToDouble(ClanWarSide::getStake).sum();
        return winners.stream().collect(Collectors.toMap(side -> side.getClan().getId(),
                side -> total > 0 ? side.getStake() / total : 1.0 / winners.size()));
    }

    public void awardWin(ClanWar war, List<ClanWarSide> winners, double engagement) {
        double standing = clanProperties.getWar().getWinStanding() * engagement;
        if (standing <= 0) {
            return;
        }
        Map<UUID, Double> deltas = new HashMap<>();
        winShares(winners).forEach((clanId, share) -> deltas.put(clanId, standing * share));
        standingService.applyAll(war.getSeason().getId(), deltas, ClanStandingSource.war_win, war.getId().toString());
    }

    private Map<ClanWarTeam, List<ClanWarSide>> fightingByTeam(List<ClanWarSide> sides) {
        Map<ClanWarTeam, List<ClanWarSide>> byTeam = new EnumMap<>(ClanWarTeam.class);
        for (ClanWarTeam team : ClanWarTeam.values()) {
            byTeam.put(team, sides.stream().filter(side -> side.getTeam() == team && side.fighting()).toList());
        }
        return byTeam;
    }

    private Map<UUID, Double> weightShares(List<ClanWarSide> team) {
        return strengthService.memberStrengths(team.stream().map(side -> side.getClan().getId()).toList()).stream()
                .collect(Collectors.groupingBy(MemberStrength::clanId, Collectors.summingDouble(MemberStrength::share)));
    }
}
