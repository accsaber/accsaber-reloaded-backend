package com.accsaber.backend.service.clan.war;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.entity.chat.ChatEvent;
import com.accsaber.backend.model.entity.clan.ClanStandingSource;
import com.accsaber.backend.model.entity.clan.ClanXpSource;
import com.accsaber.backend.model.entity.clan.war.ClanRuleset;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarHit;
import com.accsaber.backend.model.entity.clan.war.ClanWarOutcome;
import com.accsaber.backend.model.entity.clan.war.ClanWarParticipant;
import com.accsaber.backend.model.entity.clan.war.ClanWarSide;
import com.accsaber.backend.model.entity.clan.war.ClanWarStatus;
import com.accsaber.backend.model.event.ScoreSubmittedEvent;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.war.ClanWarHitRepository;
import com.accsaber.backend.repository.clan.war.ClanWarParticipantRepository;
import com.accsaber.backend.repository.clan.war.ClanWarRepository;
import com.accsaber.backend.repository.clan.war.ClanWarSideRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.service.clan.ChatNotice;
import com.accsaber.backend.service.clan.ClanChatChannel;
import com.accsaber.backend.service.clan.ClanLevelService;
import com.accsaber.backend.service.clan.ClanStandingService;
import com.accsaber.backend.service.clan.ClanXpAward;
import com.accsaber.backend.service.item.LevelUpAwardService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClanWarCombatService {

    private static final double MIN_SKILL = 1.0;

    private final ClanWarRepository warRepository;
    private final ClanWarSideRepository sideRepository;
    private final ClanWarParticipantRepository participantRepository;
    private final ClanWarHitRepository hitRepository;
    private final ScoreRepository scoreRepository;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final ClanWarScoreGate scoreGate;
    private final ClanWarService warService;
    private final ClanStandingService standingService;
    private final ClanLevelService levelService;
    private final LevelUpAwardService levelUpAwardService;
    private final ClanChatChannel chatChannel;
    private final ClanProperties clanProperties;
    private final TransactionTemplate transactionTemplate;

    record Play(ScoreResponse score, Long userId, Instant setAt) {
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onScoreSubmitted(ScoreSubmittedEvent event) {
        ScoreResponse score = event.score();
        Long userId = Long.valueOf(score.getUserId());
        if (score.isPartial() || !scoreGate.mayMatter(userId, score.getMapDifficultyId())) {
            return;
        }
        Play play = new Play(score, userId, score.getTimeSet() != null ? score.getTimeSet() : Instant.now());
        for (UUID warId : warRepository.findActiveIdsFighting(userId, score.getMapDifficultyId())) {
            try {
                transactionTemplate.executeWithoutResult(status -> fight(warId, play));
            } catch (RuntimeException e) {
                log.warn("Clan war {} could not score play {}: {}", warId, score.getId(), e.getMessage());
            }
        }
    }

    void fight(UUID warId, Play play) {
        ClanWar war = warRepository.findByIdForUpdate(warId).orElse(null);
        if (war == null || war.getStatus() != ClanWarStatus.active || play.setAt().isBefore(war.getStartsAt())) {
            return;
        }
        Map<Long, ClanWarParticipant> roster = participantRepository.findActiveByWarId(warId).stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), Function.identity()));
        ClanWarParticipant attacker = roster.get(play.userId());
        if (attacker == null) {
            return;
        }
        restoreGuard(attacker, play);
        List<ClanWarParticipant> victims = targets(war, attacker, roster);
        if (victims.isEmpty()) {
            return;
        }
        List<Long> fighters = victims.stream().map(p -> p.getUser().getId()).collect(Collectors.toList());
        fighters.add(play.userId());
        Map<Long, Double> skills = participantRepository.findOverallSkills(fighters).stream()
                .collect(Collectors.toMap(ClanMemberRepository.MemberSkillView::getUserId,
                        ClanMemberRepository.MemberSkillView::getSkill));
        Map<Long, ClanWarHitRepository.VictimScoreView> victimScores = hitRepository
                .findActiveScores(play.score().getMapDifficultyId(), fighters).stream()
                .collect(Collectors.toMap(ClanWarHitRepository.VictimScoreView::getUserId, Function.identity()));
        for (ClanWarParticipant victim : victims) {
            if (war.getStatus() == ClanWarStatus.active) {
                strike(war, attacker, victim, play, victimScores.get(victim.getUser().getId()), skills, roster);
            }
        }
    }

    private void restoreGuard(ClanWarParticipant attacker, Play play) {
        if (attacker.getBrokenAt() == null || !play.score().isActive() || !play.setAt().isAfter(attacker.getBrokenAt())) {
            return;
        }
        attacker.setGuard(clanProperties.getWar().getGuard());
        attacker.setGuardCycle(attacker.getGuardCycle() + 1);
        attacker.setBrokenAt(null);
    }

    private List<ClanWarParticipant> targets(ClanWar war, ClanWarParticipant attacker,
            Map<Long, ClanWarParticipant> roster) {
        if (war.getRuleset() == ClanRuleset.duel) {
            ClanWarParticipant target = attacker.getDuelTarget() == null ? null
                    : roster.get(attacker.getDuelTarget().getId());
            return target == null || target.getBrokenAt() != null ? List.of() : List.of(target);
        }
        UUID ownClan = attacker.getClan().getId();
        return roster.values().stream()
                .filter(p -> !p.getClan().getId().equals(ownClan) && p.getBrokenAt() == null)
                .sorted(Comparator.comparing(p -> p.getUser().getId()))
                .toList();
    }

    private void strike(ClanWar war, ClanWarParticipant attacker, ClanWarParticipant victim, Play play,
            ClanWarHitRepository.VictimScoreView victimScore, Map<Long, Double> skills,
            Map<Long, ClanWarParticipant> roster) {
        Long attackerId = attacker.getUser().getId();
        Long victimId = victim.getUser().getId();
        UUID difficultyId = play.score().getMapDifficultyId();
        if (victimScore != null && (victimScore.getScore() >= play.score().getScore()
                || hitRepository.existsByWar_IdAndAttacker_IdAndVictimScore_Id(war.getId(), attackerId,
                        victimScore.getScoreId()))) {
            return;
        }
        if (victimScore == null && hitRepository.existsByWar_IdAndAttacker_IdAndVictim_IdAndMapDifficulty_IdAndVictimScoreIsNull(
                war.getId(), attackerId, victimId, difficultyId)) {
            return;
        }
        double damage = damage(war, victim, skills.getOrDefault(attackerId, 0.0),
                skills.getOrDefault(victimId, 0.0), victimScore == null);
        victim.setGuard(Math.max(0.0, victim.getGuard() - damage));
        attacker.setContribution(attacker.getContribution() + damage);
        ClanWarHit hit = hitRepository.saveAndFlush(ClanWarHit.builder()
                .war(war)
                .attacker(attacker.getUser())
                .victim(victim.getUser())
                .victimCycle(victim.getGuardCycle())
                .mapDifficulty(mapDifficultyRepository.getReferenceById(difficultyId))
                .attackerScore(scoreRepository.getReferenceById(play.score().getId()))
                .victimScore(victimScore == null ? null : scoreRepository.getReferenceById(victimScore.getScoreId()))
                .damage(damage)
                .guardAfter(victim.getGuard())
                .broke(victim.getGuard() <= 0.0)
                .build());
        if (hit.isBroke()) {
            breakGuard(war, attacker, victim, hit, roster);
        }
        announce(war, attacker, victim, hit.isBroke() ? ChatEvent.war_break : ChatEvent.war_hit);
    }

    private double damage(ClanWar war, ClanWarParticipant victim, double attackerSkill, double victimSkill,
            boolean missingScore) {
        ClanProperties.War config = clanProperties.getWar();
        long hitsThisCycle = hitRepository.countByWar_IdAndVictim_IdAndVictimCycle(war.getId(),
                victim.getUser().getId(), victim.getGuardCycle());
        double disparity = Math.pow(Math.max(victimSkill, MIN_SKILL) / Math.max(attackerSkill, MIN_SKILL),
                config.getDisparityExponent());
        double damage = config.getBaseDamage() * Math.pow(config.getEscalation(), hitsThisCycle) * disparity;
        return missingScore ? damage * config.getMissingScoreMultiplier() : damage;
    }

    private void breakGuard(ClanWar war, ClanWarParticipant attacker, ClanWarParticipant victim, ClanWarHit hit,
            Map<Long, ClanWarParticipant> roster) {
        ClanProperties.War config = clanProperties.getWar();
        ClanWarSide victimSide = sideRepository.findByWar_IdAndClan_Id(war.getId(), victim.getClan().getId())
                .orElseThrow();
        double moved = victimSide.getStake() * config.getBreakShare() * victim.getStandingWeight()
                * Math.pow(config.getBreakDecay(), victim.getBreaksSuffered());
        victimSide.setStakeRemaining(Math.max(0.0, victimSide.getStakeRemaining() - moved));
        hit.setStandingMoved(moved);
        victim.setBreaksSuffered(victim.getBreaksSuffered() + 1);
        victim.setBrokenAt(Instant.now());
        String sourceId = hit.getId().toString();
        UUID seasonId = war.getSeason().getId();
        standingService.apply(seasonId, victim.getClan().getId(), -moved, ClanStandingSource.war_break, sourceId);
        standingService.apply(seasonId, attacker.getClan().getId(), moved, ClanStandingSource.war_break, sourceId);
        levelService.grantXp(attacker.getClan().getId(),
                new ClanXpAward(config.getBreakClanXp(), ClanXpSource.war_break, sourceId, true));
        payChippers(war, victim, roster);
        if (victimSide.getStakeRemaining() <= 0.0) {
            warService.end(war.getId(), attacker.getClan().getId().equals(war.getAttackerClan().getId())
                    ? ClanWarOutcome.attacker_won : ClanWarOutcome.defender_won);
        }
    }

    private void payChippers(ClanWar war, ClanWarParticipant victim, Map<Long, ClanWarParticipant> roster) {
        ClanProperties.War config = clanProperties.getWar();
        List<ClanWarHit> cycle = hitRepository.findByWar_IdAndVictim_IdAndVictimCycle(war.getId(),
                victim.getUser().getId(), victim.getGuardCycle());
        double total = cycle.stream().mapToDouble(ClanWarHit::getDamage).sum();
        Map<Long, Double> xpByChipper = new HashMap<>();
        for (ClanWarHit chip : cycle) {
            chip.setXpAwarded(config.getBreakXp() * chip.getDamage() / total);
            xpByChipper.merge(chip.getAttacker().getId(), chip.getXpAwarded(), Double::sum);
        }
        xpByChipper.forEach((chipperId, xp) -> {
            levelUpAwardService.addXp(chipperId, xp);
            ClanWarParticipant chipper = roster.get(chipperId);
            if (chipper != null) {
                chipper.setContribution(chipper.getContribution() + config.getBreakContribution());
            }
        });
    }

    private void announce(ClanWar war, ClanWarParticipant attacker, ClanWarParticipant victim, ChatEvent event) {
        ChatNotice notice = new ChatNotice(event, attacker.getUser(), victim.getUser(), null, war);
        chatChannel.announce(attacker.getClan(), notice);
        chatChannel.announce(victim.getClan(), notice);
    }
}
