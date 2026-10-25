package com.accsaber.backend.service.clan.war;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.model.dto.response.clan.ClanWarStrikeMapResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarStrikePreviewResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarStrikeRulesResponse;
import com.accsaber.backend.model.entity.clan.war.ClanWarParticipant;
import com.accsaber.backend.model.entity.clan.war.ClanWarSide;
import com.accsaber.backend.model.entity.clan.war.ClanWarTeam;
import com.accsaber.backend.repository.clan.war.ClanWarHitRepository;
import com.accsaber.backend.repository.clan.war.ClanWarHitRepository.Streak;
import com.accsaber.backend.repository.clan.war.ClanWarHitRepository.VictimScoreView;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class ClanWarDamage {

    private static final double MIN_SKILL = 1.0;

    static final Comparator<Strike> BEST = Comparator.comparing(Strike::breaks)
            .thenComparingDouble(strike -> strike.breaks() ? strike.standing() : strike.damage());

    private final ClanWarHitRepository hitRepository;
    private final ClanProperties clanProperties;

    enum Kind {
        HIT, MISSING, CHIP
    }

    record Strike(ClanWarParticipant victim, Kind kind, double damage, double standing) {

        boolean breaks() {
            return damage >= victim.getGuard();
        }
    }

    record Viewer(UUID warId, Long userId, UUID clanId, UUID mapDifficultyId) {
    }

    record Field(UUID warId, Long attackerId, List<ClanWarParticipant> enemies, Map<Long, Double> skills,
            ToDoubleFunction<ClanWarParticipant> standing, double lastStand) {
    }

    static List<ClanWarParticipant> team(Collection<ClanWarParticipant> roster, Map<UUID, ClanWarSide> sideByClan,
            ClanWarTeam team) {
        return roster.stream()
                .filter(p -> {
                    ClanWarSide side = sideByClan.get(p.getClan().getId());
                    return p.getLeftAt() == null && side.fighting() && side.getTeam() == team;
                })
                .toList();
    }

    static List<ClanWarParticipant> enemies(Collection<ClanWarParticipant> roster, Map<UUID, ClanWarSide> sideByClan,
            ClanWarTeam team) {
        return team(roster, sideByClan, team.opponent()).stream()
                .filter(p -> p.getBrokenAt() == null)
                .sorted(Comparator.comparing(p -> p.getUser().getId()))
                .toList();
    }

    static double skill(double raw) {
        return Math.max(raw, MIN_SKILL);
    }

    static ClanWarStrikeRulesResponse rules(ClanProperties.War config) {
        return new ClanWarStrikeRulesResponse(config.getGuard(), config.getBaseDamage(), config.getEscalation(),
                config.getDisparityExponent(), config.getMissingScoreMultiplier(), config.getChipDamage(),
                config.getChipWindow(), config.getChipSkillExponent(),
                config.getMissingScoreEscalation(), config.getChipEscalation(), config.getMapBreakDecay(),
                config.getMapBreakFloor(), config.getLastStandDamage(), config.getLastStandFrom(),
                config.getBrokenDuration().toHours(), config.getWipeDuration().toHours());
    }

    double hitDamage(Streak streak, UUID mapDifficultyId, double disparity, boolean missingScore) {
        Streak onMap = streak.on(mapDifficultyId);
        double scale = missingScore ? clanProperties.getWar().getMissingScoreMultiplier() : disparity;
        return escalated(onMap.hits(), onMap.missing(), onMap.chips()) * scale
                * mapPenalty(streak.breaksOn(mapDifficultyId));
    }

    double chip(double gap, double attackerSkill, double victimSkill) {
        ClanProperties.War config = clanProperties.getWar();
        double closeness = 1.0 - gap / config.getChipWindow();
        double ratio = Math.min(1.0, skill(attackerSkill) / skill(victimSkill));
        return config.getChipDamage() * closeness * Math.pow(ratio, config.getChipSkillExponent());
    }

    double lastStand(Collection<ClanWarParticipant> roster, Map<UUID, ClanWarSide> sideByClan, ClanWarTeam team) {
        ClanProperties.War config = clanProperties.getWar();
        List<ClanWarParticipant> fighters = team(roster, sideByClan, team);
        long alive = fighters.stream().filter(p -> p.getBrokenAt() == null).count();
        double full = config.getLastStandFrom() * fighters.size();
        if (alive >= full) {
            return 1.0;
        }
        if (alive <= 1) {
            return config.getLastStandDamage();
        }
        return config.getLastStandDamage() + (1.0 - config.getLastStandDamage()) * (alive - 1) / (full - 1);
    }

    private double mapPenalty(long breaks) {
        ClanProperties.War config = clanProperties.getWar();
        return Math.max(config.getMapBreakFloor(), Math.pow(config.getMapBreakDecay(), breaks));
    }

    Map<UUID, Strike> bestPairings(Field field, Collection<UUID> difficultyIds) {
        List<Long> enemyIds = field.enemies().stream().map(enemy -> enemy.getUser().getId()).toList();
        Map<Long, Streak> cycleHits = hitRepository.cycleHits(field.warId(), enemyIds);
        Map<UUID, Map<Long, VictimScoreView>> scores = hitRepository.findActiveScores(difficultyIds, enemyIds).stream()
                .collect(Collectors.groupingBy(VictimScoreView::getMapDifficultyId,
                        Collectors.toMap(VictimScoreView::getUserId, Function.identity())));
        Map<UUID, Strike> best = new HashMap<>();
        for (UUID difficultyId : difficultyIds) {
            Strike strike = bestOn(field, difficultyId, scores.getOrDefault(difficultyId, Map.of()), cycleHits);
            if (strike != null) {
                best.put(difficultyId, strike);
            }
        }
        return best;
    }

    private Strike bestOn(Field field, UUID difficultyId, Map<Long, VictimScoreView> onMap,
            Map<Long, Streak> cycleHits) {
        double attackerSkill = field.skills().getOrDefault(field.attackerId(), 0.0);
        Strike best = null;
        for (ClanWarParticipant enemy : field.enemies()) {
            Long enemyId = enemy.getUser().getId();
            VictimScoreView score = onMap.get(enemyId);
            double disparity = disparity(attackerSkill, field.skills().getOrDefault(enemyId, 0.0));
            Strike strike = new Strike(enemy, score == null ? Kind.MISSING : Kind.HIT,
                    hitDamage(cycleHits.getOrDefault(enemyId, Streak.NONE), difficultyId, disparity, score == null)
                            * field.lastStand(),
                    field.standing().applyAsDouble(enemy));
            if (best == null || BEST.compare(strike, best) > 0) {
                best = strike;
            }
        }
        return best;
    }

    Map<Long, ClanWarStrikePreviewResponse> previews(Viewer viewer, Field field, double engagement) {
        List<ClanWarParticipant> victims = field.enemies();
        Map<Long, Double> skills = field.skills();
        if (victims.isEmpty()) {
            return Map.of();
        }
        List<Long> victimIds = victims.stream().map(victim -> victim.getUser().getId()).toList();
        Map<Long, Streak> cycleHits = hitRepository.cycleHits(viewer.warId(), victimIds);
        Map<Long, ClanWarStrikeMapResponse> onMap = onMap(viewer, victimIds, cycleHits);
        double attackerSkill = skills.getOrDefault(viewer.userId(), 0.0);
        return victims.stream().collect(Collectors.toMap(victim -> victim.getUser().getId(), victim -> {
            Long victimId = victim.getUser().getId();
            double victimSkill = skills.getOrDefault(victimId, 0.0);
            double disparity = disparity(attackerSkill, victimSkill);
            Streak streak = cycleHits.getOrDefault(victimId, Streak.NONE);
            ClanWarStrikeMapResponse map = onMap.get(victimId);
            boolean missing = map != null && map.targetScore() == null;
            double damage = hitDamage(streak, viewer.mapDifficultyId(), disparity, missing) * field.lastStand();
            double standing = field.standing().applyAsDouble(victim);
            return new ClanWarStrikePreviewResponse(damage,
                    hitsToBreak(victim.getGuard(), streak, disparity, field.lastStand()), streak.hits(),
                    streak.missing(), streak.chips(), skill(attackerSkill), skill(victimSkill), disparity,
                    field.lastStand(), standing, standing * engagement, map);
        }));
    }

    private Map<Long, ClanWarStrikeMapResponse> onMap(Viewer viewer, List<Long> victimIds,
            Map<Long, Streak> cycleHits) {
        UUID difficultyId = viewer.mapDifficultyId();
        if (difficultyId == null) {
            return Map.of();
        }
        Map<Long, VictimScoreView> scores = hitRepository.findActiveScores(List.of(difficultyId), victimIds)
                .stream().collect(Collectors.toMap(VictimScoreView::getUserId, Function.identity()));
        return victimIds.stream().collect(Collectors.toMap(Function.identity(), victimId -> {
            VictimScoreView score = scores.get(victimId);
            Streak streak = cycleHits.getOrDefault(victimId, Streak.NONE);
            return new ClanWarStrikeMapResponse(score == null ? null : score.getScore(),
                    streak.repeats(difficultyId), mapPenalty(streak.breaksOn(difficultyId)));
        }));
    }

    private int hitsToBreak(double guard, Streak streak, double disparity, double lastStand) {
        int hits = 0;
        for (double left = guard; left > 0; hits++) {
            left -= escalated(streak.hits() + hits, streak.missing(), streak.chips()) * disparity * lastStand;
        }
        return hits;
    }

    double disparity(double attackerSkill, double victimSkill) {
        return Math.pow(skill(victimSkill) / skill(attackerSkill), clanProperties.getWar().getDisparityExponent());
    }

    private double escalated(long hits, long missing, long chips) {
        ClanProperties.War config = clanProperties.getWar();
        return config.getBaseDamage() * Math.pow(config.getEscalation(), hits)
                * Math.pow(config.getMissingScoreEscalation(), missing)
                * Math.pow(config.getChipEscalation(), chips);
    }
}
