package com.accsaber.backend.service.clan.war;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import com.accsaber.backend.model.entity.clan.war.ClanWarTeam;
import com.accsaber.backend.model.entity.map.MapDifficulty;
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
import com.accsaber.backend.service.clan.ClanFeed;
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
    private static final double MAX_ACCURACY = 0.999999;

    private final ClanWarRepository warRepository;
    private final ClanWarSideRepository sideRepository;
    private final ClanWarParticipantRepository participantRepository;
    private final ClanWarHitRepository hitRepository;
    private final ScoreRepository scoreRepository;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final ClanWarScoreGate scoreGate;
    private final ClanWarService warService;
    private final ClanWarRosterService rosterService;
    private final ClanWarStakeService stakeService;
    private final ClanStandingService standingService;
    private final ClanLevelService levelService;
    private final LevelUpAwardService levelUpAwardService;
    private final ClanChatChannel chatChannel;
    private final ClanFeed feed;
    private final ClanProperties clanProperties;
    private final TransactionTemplate transactionTemplate;

    record Play(ScoreResponse score, Long userId, Instant setAt) {
    }

    enum Kind {
        HIT, MISSING, CHIP
    }

    record Strike(ClanWarParticipant victim, Kind kind, Double gap) {
    }

    private record Battle(ClanWar war, List<ClanWarSide> sides, Map<UUID, ClanWarSide> sideByClan,
            Map<Long, ClanWarParticipant> roster, ClanWarParticipant attacker, Play play, Map<Long, Double> skills,
            Map<Long, ClanWarHitRepository.VictimScoreView> victimScores, Integer maxScore, Set<String> prior) {

        ClanWarTeam team(ClanWarParticipant participant) {
            return sideByClan.get(participant.getClan().getId()).getTeam();
        }
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
        Battle battle = battle(war, play);
        if (battle == null) {
            return;
        }
        restoreGuard(battle.attacker(), play);
        List<ClanWarParticipant> enemies = enemies(battle);
        if (enemies.isEmpty()) {
            return;
        }
        if (war.getRuleset() == ClanRuleset.berserker) {
            for (ClanWarParticipant enemy : enemies) {
                Kind kind = hitKind(battle, enemy);
                if (kind != null && war.getStatus() == ClanWarStatus.active) {
                    strike(battle, new Strike(enemy, kind, null));
                }
            }
            return;
        }
        duel(battle, enemies);
    }

    private Battle battle(ClanWar war, Play play) {
        List<ClanWarSide> sides = sideRepository.findByWarId(war.getId());
        Map<UUID, ClanWarSide> sideByClan = sides.stream()
                .collect(Collectors.toMap(side -> side.getClan().getId(), Function.identity()));
        Map<Long, ClanWarParticipant> roster = participantRepository.findActiveByWarId(war.getId()).stream()
                .filter(p -> sideByClan.get(p.getClan().getId()).fighting())
                .collect(Collectors.toMap(p -> p.getUser().getId(), Function.identity()));
        ClanWarParticipant attacker = roster.get(play.userId());
        if (attacker == null) {
            return null;
        }
        UUID difficultyId = play.score().getMapDifficultyId();
        List<Long> fighters = List.copyOf(roster.keySet());
        Map<Long, Double> skills = participantRepository.findOverallSkills(fighters).stream()
                .collect(Collectors.toMap(ClanMemberRepository.MemberSkillView::getUserId,
                        ClanMemberRepository.MemberSkillView::getSkill));
        Map<Long, ClanWarHitRepository.VictimScoreView> victimScores = hitRepository
                .findActiveScores(difficultyId, fighters).stream()
                .collect(Collectors.toMap(ClanWarHitRepository.VictimScoreView::getUserId, Function.identity()));
        Integer maxScore = mapDifficultyRepository.findById(difficultyId).map(MapDifficulty::getMaxScore).orElse(null);
        Set<String> prior = new HashSet<>();
        hitRepository.findPriorHits(war.getId(), play.userId(), difficultyId).forEach(hit -> prior.add(
                hit.getVictimScoreId() == null ? missingKey(hit.getVictimId()) : scoreKey(hit.getVictimScoreId(), hit.isChip())));
        return new Battle(war, sides, sideByClan, roster, attacker, play, skills, victimScores, maxScore, prior);
    }

    private List<ClanWarParticipant> enemies(Battle battle) {
        ClanWarTeam own = battle.team(battle.attacker());
        return battle.roster().values().stream()
                .filter(p -> battle.team(p) != own && p.getBrokenAt() == null)
                .sorted(Comparator.comparing(p -> p.getUser().getId()))
                .toList();
    }

    private void duel(Battle battle, List<ClanWarParticipant> enemies) {
        Strike primary = picked(battle, enemies);
        if (primary == null) {
            primary = closest(battle, enemies);
        }
        if (primary == null || !strike(battle, primary) || battle.war().getStatus() != ClanWarStatus.active) {
            return;
        }
        String key = battle.play().score().getId() + ":" + battle.war().getId();
        if (unit(key) >= clanProperties.getWar().getExtraChipChance()) {
            return;
        }
        ClanWarParticipant first = primary.victim();
        List<Strike> above = enemies.stream()
                .filter(enemy -> enemy != first && enemy.getBrokenAt() == null)
                .map(enemy -> chip(battle, enemy))
                .filter(Objects::nonNull)
                .toList();
        if (!above.isEmpty()) {
            strike(battle, above.get(Math.floorMod((key + ":pick").hashCode(), above.size())));
        }
    }

    private Strike picked(Battle battle, List<ClanWarParticipant> enemies) {
        if (battle.attacker().getTarget() == null) {
            return null;
        }
        Long targetId = battle.attacker().getTarget().getId();
        ClanWarParticipant target = enemies.stream().filter(e -> e.getUser().getId().equals(targetId)).findFirst()
                .orElse(null);
        if (target == null) {
            return null;
        }
        Kind kind = hitKind(battle, target);
        return kind != null ? new Strike(target, kind, null) : chip(battle, target);
    }

    private Strike closest(Battle battle, List<ClanWarParticipant> enemies) {
        Comparator<Strike> byScore = Comparator.comparingInt(strike -> scoreOf(battle, strike.victim()));
        return enemies.stream()
                .map(enemy -> {
                    Kind kind = hitKind(battle, enemy);
                    return kind == null ? null : new Strike(enemy, kind, null);
                })
                .filter(Objects::nonNull)
                .max(byScore)
                .or(() -> enemies.stream().map(enemy -> chip(battle, enemy)).filter(Objects::nonNull)
                        .min(byScore))
                .orElse(null);
    }

    private Strike chip(Battle battle, ClanWarParticipant enemy) {
        Double gap = chipGap(battle, enemy);
        return gap == null ? null : new Strike(enemy, Kind.CHIP, gap);
    }

    private Kind hitKind(Battle battle, ClanWarParticipant enemy) {
        ClanWarHitRepository.VictimScoreView victimScore = battle.victimScores().get(enemy.getUser().getId());
        if (victimScore == null) {
            return battle.prior().contains(missingKey(enemy.getUser().getId())) ? null : Kind.MISSING;
        }
        boolean beaten = victimScore.getScore() < battle.play().score().getScore();
        return beaten && !battle.prior().contains(scoreKey(victimScore.getScoreId(), false)) ? Kind.HIT : null;
    }

    private Double chipGap(Battle battle, ClanWarParticipant enemy) {
        ClanWarHitRepository.VictimScoreView victimScore = battle.victimScores().get(enemy.getUser().getId());
        if (victimScore == null || battle.maxScore() == null || battle.maxScore() <= 0
                || victimScore.getScore() < battle.play().score().getScore()
                || battle.prior().contains(scoreKey(victimScore.getScoreId(), false))
                || battle.prior().contains(scoreKey(victimScore.getScoreId(), true))) {
            return null;
        }
        double gap = linear(victimScore.getScore(), battle.maxScore())
                - linear(battle.play().score().getScore(), battle.maxScore());
        return gap < clanProperties.getWar().getChipWindow() ? gap : null;
    }

    private boolean strike(Battle battle, Strike strike) {
        ClanWarParticipant victim = strike.victim();
        ClanWarParticipant attacker = battle.attacker();
        ClanWarHitRepository.VictimScoreView victimScore = battle.victimScores().get(victim.getUser().getId());
        double damage = Math.min(victim.getGuard(), strike.kind() == Kind.CHIP
                ? chipDamage(battle, victim, strike.gap())
                : fullDamage(battle, victim, strike.kind() == Kind.MISSING));
        if (damage <= 0) {
            return false;
        }
        victim.setGuard(Math.max(0.0, victim.getGuard() - damage));
        double share = sizeShare(battle);
        attacker.setContribution(attacker.getContribution() + damage * share);
        UUID difficultyId = battle.play().score().getMapDifficultyId();
        ClanWarHit hit = hitRepository.saveAndFlush(ClanWarHit.builder()
                .war(battle.war())
                .attacker(attacker.getUser())
                .victim(victim.getUser())
                .victimCycle(victim.getGuardCycle())
                .mapDifficulty(mapDifficultyRepository.getReferenceById(difficultyId))
                .attackerScore(scoreRepository.getReferenceById(battle.play().score().getId()))
                .victimScore(victimScore == null ? null : scoreRepository.getReferenceById(victimScore.getScoreId()))
                .chip(strike.kind() == Kind.CHIP)
                .damage(damage)
                .guardAfter(victim.getGuard())
                .broke(victim.getGuard() <= 0.0)
                .build());
        battle.prior().add(victimScore == null ? missingKey(victim.getUser().getId())
                : scoreKey(victimScore.getScoreId(), hit.isChip()));
        if (hit.isBroke()) {
            breakGuard(battle, victim, hit, share);
        }
        feed.hit(battle.war(), hit, List.copyOf(battle.sideByClan().keySet()));
        if (hit.isBroke() || !hit.isChip()) {
            announce(battle.war(), attacker, victim, hit.isBroke() ? ChatEvent.war_break : ChatEvent.war_hit);
        }
        return true;
    }

    private double fullDamage(Battle battle, ClanWarParticipant victim, boolean missingScore) {
        ClanProperties.War config = clanProperties.getWar();
        long hitsThisCycle = hitRepository.countByWar_IdAndVictim_IdAndVictimCycleAndChipFalse(battle.war().getId(),
                victim.getUser().getId(), victim.getGuardCycle());
        double disparity = Math.pow(skill(battle, victim) / skill(battle, battle.attacker()),
                config.getDisparityExponent());
        double damage = config.getBaseDamage() * Math.pow(config.getEscalation(), hitsThisCycle) * disparity;
        return missingScore ? damage * config.getMissingScoreMultiplier() : damage;
    }

    private double chipDamage(Battle battle, ClanWarParticipant victim, double gap) {
        ClanProperties.War config = clanProperties.getWar();
        double closeness = 1.0 - gap / config.getChipWindow();
        double ratio = Math.min(1.0, skill(battle, battle.attacker()) / skill(battle, victim));
        return config.getChipDamage() * closeness * Math.pow(ratio, config.getChipSkillExponent());
    }

    private void breakGuard(Battle battle, ClanWarParticipant victim, ClanWarHit hit, double share) {
        ClanProperties.War config = clanProperties.getWar();
        ClanWar war = battle.war();
        ClanWarSide victimSide = battle.sideByClan().get(victim.getClan().getId());
        ClanWarTeam victimTeam = victimSide.getTeam();
        double moved = stakeService.fullTeamStake(battle.sides(), victimTeam) * config.getBreakShare()
                * victim.getStandingWeight() * Math.pow(config.getBreakDecay(), victim.getBreaksSuffered())
                * stakeService.risk(victimSide);
        double applied = stakeService.debit(victimSide, moved);
        hit.setStandingMoved(applied);
        victim.setBreaksSuffered(victim.getBreaksSuffered() + 1);
        victim.setBrokenAt(Instant.now());
        double engagement = stakeService.engagement(war, victimTeam);
        String sourceId = hit.getId().toString();
        UUID seasonId = war.getSeason().getId();
        UUID attackerClanId = battle.attacker().getClan().getId();
        standingService.apply(seasonId, victim.getClan().getId(), -applied, ClanStandingSource.war_break, sourceId);
        standingService.apply(seasonId, attackerClanId, applied * engagement, ClanStandingSource.war_break, sourceId);
        levelService.grantXp(attackerClanId, new ClanXpAward(config.getBreakClanXp() * share * engagement,
                ClanXpSource.war_break, sourceId, true));
        payChippers(battle, victim, share * engagement);
        sideRepository.save(victimSide);
        if (victimSide.fallen()) {
            rosterService.sync(war, battle.sides());
        }
        feed.war(war);
        if (stakeService.lost(battle.sides(), victimTeam)) {
            warService.end(war.getId(), ClanWarOutcome.wonBy(victimTeam.opponent()));
        }
    }

    private void payChippers(Battle battle, ClanWarParticipant victim, double scale) {
        ClanProperties.War config = clanProperties.getWar();
        List<ClanWarHit> cycle = hitRepository.findByWar_IdAndVictim_IdAndVictimCycle(battle.war().getId(),
                victim.getUser().getId(), victim.getGuardCycle());
        double total = cycle.stream().mapToDouble(ClanWarHit::getDamage).sum();
        Map<Long, Double> xpByChipper = new HashMap<>();
        for (ClanWarHit chip : cycle) {
            chip.setXpAwarded(config.getBreakXp() * scale * chip.getDamage() / total);
            xpByChipper.merge(chip.getAttacker().getId(), chip.getXpAwarded(), Double::sum);
        }
        xpByChipper.forEach((chipperId, xp) -> {
            levelUpAwardService.addClanXp(chipperId, xp);
            ClanWarParticipant chipper = battle.roster().get(chipperId);
            if (chipper != null) {
                chipper.setContribution(chipper.getContribution() + config.getBreakContribution() * scale);
            }
        });
    }

    private double sizeShare(Battle battle) {
        if (battle.war().getRuleset() != ClanRuleset.berserker) {
            return 1.0;
        }
        ClanWarTeam own = battle.team(battle.attacker());
        long mine = battle.roster().values().stream().filter(p -> battle.team(p) == own).count();
        long enemies = battle.roster().size() - mine;
        return Math.pow((double) mine / Math.max(1, enemies), clanProperties.getWar().getWarSizeExponent());
    }

    private void restoreGuard(ClanWarParticipant attacker, Play play) {
        if (attacker.getBrokenAt() == null || !play.score().isActive() || !play.setAt().isAfter(attacker.getBrokenAt())) {
            return;
        }
        attacker.setGuard(clanProperties.getWar().getGuard());
        attacker.setGuardCycle(attacker.getGuardCycle() + 1);
        attacker.setBrokenAt(null);
    }

    private void announce(ClanWar war, ClanWarParticipant attacker, ClanWarParticipant victim, ChatEvent event) {
        ChatNotice notice = new ChatNotice(event, attacker.getUser(), victim.getUser(), null, war);
        chatChannel.announce(attacker.getClan(), notice);
        chatChannel.announce(victim.getClan(), notice);
    }

    private static int scoreOf(Battle battle, ClanWarParticipant enemy) {
        ClanWarHitRepository.VictimScoreView victimScore = battle.victimScores().get(enemy.getUser().getId());
        return victimScore == null ? -1 : victimScore.getScore();
    }

    private static double skill(Battle battle, ClanWarParticipant participant) {
        return Math.max(battle.skills().getOrDefault(participant.getUser().getId(), 0.0), MIN_SKILL);
    }

    static double linear(int score, int maxScore) {
        return -Math.log10(1.0 - Math.min(MAX_ACCURACY, (double) score / maxScore));
    }

    static double unit(String key) {
        return (key.hashCode() & 0x7fffffff) / (double) Integer.MAX_VALUE;
    }

    private static String scoreKey(UUID scoreId, boolean chip) {
        return (chip ? "chip:" : "hit:") + scoreId;
    }

    private static String missingKey(Long victimId) {
        return "missing:" + victimId;
    }
}
