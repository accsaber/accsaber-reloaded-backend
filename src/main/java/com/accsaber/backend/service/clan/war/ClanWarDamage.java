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

    record Viewer(UUID warId, Long userId, UUID mapDifficultyId) {
    }

    record Field(UUID warId, Long attackerId, List<ClanWarParticipant> enemies, Map<Long, Double> skills,
            ToDoubleFunction<ClanWarParticipant> standing) {
    }

    static List<ClanWarParticipant> enemies(Collection<ClanWarParticipant> roster, Map<UUID, ClanWarSide> sideByClan,
            ClanWarTeam team) {
        return roster.stream()
                .filter(p -> p.getLeftAt() == null && p.getBrokenAt() == null)
                .filter(p -> {
                    ClanWarSide side = sideByClan.get(p.getClan().getId());
                    return side.fighting() && side.getTeam() != team;
                })
                .sorted(Comparator.comparing(p -> p.getUser().getId()))
                .toList();
    }

    static double skill(double raw) {
        return Math.max(raw, MIN_SKILL);
    }

    static ClanWarStrikeRulesResponse rules(ClanProperties.War config) {
        return new ClanWarStrikeRulesResponse(config.getGuard(), config.getBaseDamage(), config.getEscalation(),
                config.getDisparityExponent(), config.getMissingScoreMultiplier(), config.getChipDamage(),
                config.getChipWindow(), config.getChipSkillExponent(), config.getMinChipDamage(),
                config.getMissingScoreEscalation(), config.getChipEscalation(), config.getBrokenDuration().toHours());
    }

    double hitDamage(Streak streak, double attackerSkill, double victimSkill, boolean missingScore) {
        if (missingScore) {
            return hit(streak, 1.0) * clanProperties.getWar().getMissingScoreMultiplier();
        }
        return hit(streak, disparity(attackerSkill, victimSkill));
    }

    double chip(double gap, double attackerSkill, double victimSkill) {
        ClanProperties.War config = clanProperties.getWar();
        double closeness = 1.0 - gap / config.getChipWindow();
        double ratio = Math.min(1.0, skill(attackerSkill) / skill(victimSkill));
        return Math.max(config.getMinChipDamage(),
                config.getChipDamage() * closeness * Math.pow(ratio, config.getChipSkillExponent()));
    }

    Map<UUID, ClanWarParticipant> bestPairings(Field field, Collection<UUID> difficultyIds) {
        List<Long> enemyIds = field.enemies().stream().map(enemy -> enemy.getUser().getId()).toList();
        Map<Long, Streak> cycleHits = hitRepository.cycleHits(field.warId(), enemyIds);
        Map<UUID, Map<Long, VictimScoreView>> scores = hitRepository.findActiveScores(difficultyIds, enemyIds).stream()
                .collect(Collectors.groupingBy(VictimScoreView::getMapDifficultyId,
                        Collectors.toMap(VictimScoreView::getUserId, Function.identity())));
        Map<UUID, ClanWarParticipant> best = new HashMap<>();
        for (UUID difficultyId : difficultyIds) {
            Strike strike = bestOn(field, scores.getOrDefault(difficultyId, Map.of()), cycleHits);
            if (strike != null) {
                best.put(difficultyId, strike.victim());
            }
        }
        return best;
    }

    private Strike bestOn(Field field, Map<Long, VictimScoreView> onMap, Map<Long, Streak> cycleHits) {
        double attackerSkill = field.skills().getOrDefault(field.attackerId(), 0.0);
        Strike best = null;
        for (ClanWarParticipant enemy : field.enemies()) {
            Long enemyId = enemy.getUser().getId();
            VictimScoreView score = onMap.get(enemyId);
            Strike strike = new Strike(enemy, score == null ? Kind.MISSING : Kind.HIT,
                    hitDamage(cycleHits.getOrDefault(enemyId, Streak.NONE), attackerSkill,
                            field.skills().getOrDefault(enemyId, 0.0), score == null),
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
        Map<Long, ClanWarStrikeMapResponse> onMap = onMap(viewer, victimIds);
        double attackerSkill = skills.getOrDefault(viewer.userId(), 0.0);
        return victims.stream().collect(Collectors.toMap(victim -> victim.getUser().getId(), victim -> {
            Long victimId = victim.getUser().getId();
            double victimSkill = skills.getOrDefault(victimId, 0.0);
            double disparity = disparity(attackerSkill, victimSkill);
            Streak streak = cycleHits.getOrDefault(victimId, Streak.NONE);
            double standing = field.standing().applyAsDouble(victim);
            return new ClanWarStrikePreviewResponse(hit(streak, disparity), hitsToBreak(victim.getGuard(), streak,
                    disparity), streak.hits(), streak.missing(), streak.chips(), skill(attackerSkill), skill(victimSkill), disparity,
                    standing, standing * engagement, onMap.get(victimId));
        }));
    }

    private Map<Long, ClanWarStrikeMapResponse> onMap(Viewer viewer, List<Long> victimIds) {
        if (viewer.mapDifficultyId() == null) {
            return Map.of();
        }
        Map<Long, VictimScoreView> scores = hitRepository.findActiveScores(List.of(viewer.mapDifficultyId()), victimIds)
                .stream().collect(Collectors.toMap(VictimScoreView::getUserId, Function.identity()));
        return victimIds.stream().collect(Collectors.toMap(Function.identity(), victimId -> {
            VictimScoreView score = scores.get(victimId);
            return new ClanWarStrikeMapResponse(score == null ? null : score.getScore());
        }));
    }

    private int hitsToBreak(double guard, Streak streak, double disparity) {
        int hits = 0;
        for (double left = guard; left > 0; hits++) {
            left -= hit(new Streak(streak.hits() + hits, streak.missing(), streak.chips()), disparity);
        }
        return hits;
    }

    private double disparity(double attackerSkill, double victimSkill) {
        return Math.pow(skill(victimSkill) / skill(attackerSkill), clanProperties.getWar().getDisparityExponent());
    }

    private double hit(Streak streak, double disparity) {
        ClanProperties.War config = clanProperties.getWar();
        return config.getBaseDamage() * Math.pow(config.getEscalation(), streak.hits())
                * Math.pow(config.getMissingScoreEscalation(), streak.missing())
                * Math.pow(config.getChipEscalation(), streak.chips()) * disparity;
    }
}
