package com.accsaber.backend.service.clan.war;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.model.dto.response.clan.ClanWarStrikeMapResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarStrikePreviewResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarStrikeRulesResponse;
import com.accsaber.backend.model.entity.clan.war.ClanWarParticipant;
import com.accsaber.backend.repository.clan.war.ClanWarHitRepository;
import com.accsaber.backend.repository.clan.war.ClanWarHitRepository.PriorHitView;
import com.accsaber.backend.repository.clan.war.ClanWarHitRepository.VictimScoreView;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class ClanWarDamage {

    private static final double MIN_SKILL = 1.0;

    private final ClanWarHitRepository hitRepository;
    private final ClanProperties clanProperties;

    record Viewer(UUID warId, Long userId, UUID mapDifficultyId) {
    }

    static double skill(double raw) {
        return Math.max(raw, MIN_SKILL);
    }

    static ClanWarStrikeRulesResponse rules(ClanProperties.War config) {
        return new ClanWarStrikeRulesResponse(config.getGuard(), config.getBaseDamage(), config.getEscalation(),
                config.getDisparityExponent(), config.getMissingScoreMultiplier(), config.getChipDamage(),
                config.getChipWindow(), config.getChipSkillExponent());
    }

    static String key(Long victimId, UUID victimScoreId, boolean chip) {
        return victimScoreId == null ? "missing:" + victimId : (chip ? "chip:" : "hit:") + victimScoreId;
    }

    static Set<String> priorKeys(List<PriorHitView> hits) {
        return hits.stream().map(hit -> key(hit.getVictimId(), hit.getVictimScoreId(), hit.isChip()))
                .collect(Collectors.toCollection(HashSet::new));
    }

    static boolean hitUsed(Set<String> prior, Long victimId, UUID victimScoreId) {
        return prior.contains(key(victimId, victimScoreId, false));
    }

    static boolean chipUsed(Set<String> prior, UUID victimScoreId) {
        return prior.contains(key(null, victimScoreId, false)) || prior.contains(key(null, victimScoreId, true));
    }

    double hit(UUID warId, Long victimId, double attackerSkill, double victimSkill) {
        long hitsThisCycle = hitRepository.cycleHits(warId, List.of(victimId)).getOrDefault(victimId, 0L);
        return hit(hitsThisCycle, disparity(attackerSkill, victimSkill));
    }

    double chip(double gap, double attackerSkill, double victimSkill) {
        ClanProperties.War config = clanProperties.getWar();
        double closeness = 1.0 - gap / config.getChipWindow();
        double ratio = Math.min(1.0, skill(attackerSkill) / skill(victimSkill));
        return config.getChipDamage() * closeness * Math.pow(ratio, config.getChipSkillExponent());
    }

    Map<Long, ClanWarStrikePreviewResponse> previews(Viewer viewer, Collection<ClanWarParticipant> victims,
            Map<Long, Double> skills) {
        if (victims.isEmpty()) {
            return Map.of();
        }
        List<Long> victimIds = victims.stream().map(victim -> victim.getUser().getId()).toList();
        Map<Long, Long> cycleHits = hitRepository.cycleHits(viewer.warId(), victimIds);
        Map<Long, ClanWarStrikeMapResponse> onMap = onMap(viewer, victimIds);
        double attackerSkill = skills.getOrDefault(viewer.userId(), 0.0);
        return victims.stream().collect(Collectors.toMap(victim -> victim.getUser().getId(), victim -> {
            Long victimId = victim.getUser().getId();
            double victimSkill = skills.getOrDefault(victimId, 0.0);
            double disparity = disparity(attackerSkill, victimSkill);
            long hits = cycleHits.getOrDefault(victimId, 0L);
            return new ClanWarStrikePreviewResponse(hit(hits, disparity), hitsToBreak(victim.getGuard(), hits,
                    disparity), hits, skill(attackerSkill), skill(victimSkill), disparity, onMap.get(victimId));
        }));
    }

    private Map<Long, ClanWarStrikeMapResponse> onMap(Viewer viewer, List<Long> victimIds) {
        if (viewer.mapDifficultyId() == null) {
            return Map.of();
        }
        Map<Long, VictimScoreView> scores = hitRepository.findActiveScores(viewer.mapDifficultyId(), victimIds)
                .stream().collect(Collectors.toMap(VictimScoreView::getUserId, Function.identity()));
        Set<String> prior = priorKeys(hitRepository.findPriorHits(viewer.warId(), viewer.userId(),
                viewer.mapDifficultyId()));
        return victimIds.stream().collect(Collectors.toMap(Function.identity(), victimId -> {
            VictimScoreView score = scores.get(victimId);
            UUID scoreId = score == null ? null : score.getScoreId();
            return new ClanWarStrikeMapResponse(score == null ? null : score.getScore(),
                    hitUsed(prior, victimId, scoreId), scoreId != null && chipUsed(prior, scoreId));
        }));
    }

    private int hitsToBreak(double guard, long hitsThisCycle, double disparity) {
        int hits = 0;
        for (double left = guard; left > 0; hits++) {
            left -= hit(hitsThisCycle + hits, disparity);
        }
        return hits;
    }

    private double disparity(double attackerSkill, double victimSkill) {
        return Math.pow(skill(victimSkill) / skill(attackerSkill), clanProperties.getWar().getDisparityExponent());
    }

    private double hit(long hitsThisCycle, double disparity) {
        ClanProperties.War config = clanProperties.getWar();
        return config.getBaseDamage() * Math.pow(config.getEscalation(), hitsThisCycle) * disparity;
    }
}
