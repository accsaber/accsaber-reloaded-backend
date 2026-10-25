package com.accsaber.backend.service.clan.war;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
import com.accsaber.backend.repository.clan.ClanRepository;
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
import com.accsaber.backend.service.clan.war.ClanWarDamage.Kind;
import com.accsaber.backend.service.clan.war.ClanWarDamage.Strike;
import com.accsaber.backend.service.item.LevelUpAwardService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClanWarCombatService {

    private static final double MAX_ACCURACY = 0.999999;

    private final ClanWarRepository warRepository;
    private final ClanRepository clanRepository;
    private final ClanWarSideRepository sideRepository;
    private final ClanWarParticipantRepository participantRepository;
    private final ClanWarHitRepository hitRepository;
    private final ScoreRepository scoreRepository;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final ClanWarScoreGate scoreGate;
    private final ClanWarService warService;
    private final ClanWarRosterService rosterService;
    private final ClanWarStakeService stakeService;
    private final ClanWarDamage warDamage;
    private final ClanStandingService standingService;
    private final ClanLevelService levelService;
    private final LevelUpAwardService levelUpAwardService;
    private final ClanChatChannel chatChannel;
    private final ClanFeed feed;
    private final ClanProperties clanProperties;
    private final TransactionTemplate transactionTemplate;

    record Play(UUID scoreId, Long userId, UUID mapDifficultyId, int score, Instant setAt) {

        static Play of(ScoreResponse score) {
            return new Play(score.getId(), Long.valueOf(score.getUserId()), score.getMapDifficultyId(),
                    score.getScore(), score.getTimeSet() != null ? score.getTimeSet() : Instant.now());
        }

        static Play of(ClanWarRepository.UnscoredPlayView row) {
            return new Play(row.getScoreId(), row.getUserId(), row.getMapDifficultyId(), row.getScore(),
                    row.getSetAt());
        }
    }

    private record Battle(ClanWar war, List<ClanWarSide> sides, Map<UUID, ClanWarSide> sideByClan,
            Map<Long, ClanWarParticipant> roster, ClanWarParticipant attacker, Play play, Map<Long, Double> skills,
            Map<Long, ClanWarHitRepository.VictimScoreView> victimScores, Integer maxScore,
            Map<Long, ClanWarHitRepository.Streak> cycleHits, double lastStand) {

        ClanWarTeam team(ClanWarParticipant participant) {
            return sideByClan.get(participant.getClan().getId()).getTeam();
        }
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onScoreSubmitted(ScoreSubmittedEvent event) {
        ScoreResponse score = event.score();
        Long userId = Long.valueOf(score.getUserId());
        if (score.isPartial() || !scoreGate.mayMatter(score)) {
            return;
        }
        Play play = Play.of(score);
        for (UUID warId : warRepository.findActiveIdsFighting(userId, score.getMapDifficultyId())) {
            scoreIn(warId, play);
        }
    }

    public int scoreMissed(Instant since, Instant settledBefore, int limit) {
        int scored = 0;
        for (ClanWarRepository.UnscoredPlayView row : warRepository.findUnscoredPlays(since, settledBefore, limit)) {
            if (scoreIn(row.getWarId(), Play.of(row))) {
                scored++;
            }
        }
        return scored;
    }

    private boolean scoreIn(UUID warId, Play play) {
        try {
            transactionTemplate.executeWithoutResult(status -> fight(warId, play));
            return true;
        } catch (RuntimeException e) {
            log.warn("Clan war {} could not score play {}: {}", warId, play.scoreId(), e.getMessage());
            return false;
        }
    }

    void fight(UUID warId, Play play) {
        participantRepository.findOpenClanId(warId, play.userId())
                .ifPresent(clanRepository::findByIdAndActiveTrueForUpdate);
        ClanWar war = warRepository.findByIdForUpdate(warId).orElse(null);
        if (war == null || war.getStatus() != ClanWarStatus.active || play.setAt().isBefore(war.getStartsAt())
                || warRepository.markScored(warId, play.scoreId()) == 0) {
            return;
        }
        Battle battle = battle(war, play);
        if (battle == null) {
            return;
        }
        restoreGuard(battle.attacker(), play);
        if (battle.attacker().halted() || war.getRetreatAt() != null && battle.team(battle.attacker()) == ClanWarTeam.attack) {
            return;
        }
        List<ClanWarParticipant> enemies = enemies(battle);
        if (enemies.isEmpty()) {
            return;
        }
        if (war.getRuleset() == ClanRuleset.berserker) {
            for (ClanWarParticipant enemy : enemies) {
                Kind kind = hitKind(battle, enemy);
                if (kind != null && war.getStatus() == ClanWarStatus.active) {
                    strike(battle, hit(battle, enemy, kind));
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
        UUID difficultyId = play.mapDifficultyId();
        List<Long> fighters = List.copyOf(roster.keySet());
        Map<Long, Double> skills = participantRepository.findOverallSkills(fighters).stream()
                .collect(Collectors.toMap(ClanMemberRepository.MemberSkillView::getUserId,
                        ClanMemberRepository.MemberSkillView::getSkill));
        Map<Long, ClanWarHitRepository.VictimScoreView> victimScores = hitRepository
                .findActiveScores(List.of(difficultyId), fighters).stream()
                .collect(Collectors.toMap(ClanWarHitRepository.VictimScoreView::getUserId, Function.identity()));
        Integer maxScore = mapDifficultyRepository.findById(difficultyId).map(MapDifficulty::getMaxScore).orElse(null);
        ClanWarTeam victimTeam = sideByClan.get(attacker.getClan().getId()).getTeam().opponent();
        stakeService.loadBounties(war.getId(), roster.values());
        return new Battle(war, sides, sideByClan, roster, attacker, play, skills, victimScores, maxScore,
                hitRepository.cycleHits(war.getId(), fighters),
                warDamage.lastStand(roster.values(), sideByClan, victimTeam));
    }

    private List<ClanWarParticipant> enemies(Battle battle) {
        return ClanWarDamage.enemies(battle.roster().values(), battle.sideByClan(), battle.team(battle.attacker()));
    }

    private void duel(Battle battle, List<ClanWarParticipant> enemies) {
        Strike primary = picked(battle, enemies)
                .or(() -> enemies.stream().map(enemy -> option(battle, enemy)).filter(Objects::nonNull)
                        .max(ClanWarDamage.BEST))
                .orElse(null);
        if (primary == null || !strike(battle, primary) || battle.war().getStatus() != ClanWarStatus.active) {
            return;
        }
        String key = battle.play().scoreId() + ":" + battle.war().getId();
        if (unit(key) >= clanProperties.getWar().getExtraChipChance()) {
            return;
        }
        ClanWarParticipant first = primary.victim();
        List<Strike> above = enemies.stream()
                .filter(enemy -> enemy != first)
                .map(enemy -> chip(battle, enemy))
                .filter(Objects::nonNull)
                .toList();
        if (!above.isEmpty()) {
            strike(battle, above.get(Math.floorMod((key + ":pick").hashCode(), above.size())));
        }
    }

    private Optional<Strike> picked(Battle battle, List<ClanWarParticipant> enemies) {
        if (battle.attacker().aim() == null) {
            return Optional.empty();
        }
        Long targetId = battle.attacker().aim().getId();
        return enemies.stream().filter(e -> e.getUser().getId().equals(targetId)).findFirst()
                .map(target -> option(battle, target));
    }

    private Strike option(Battle battle, ClanWarParticipant enemy) {
        Kind kind = hitKind(battle, enemy);
        return kind != null ? hit(battle, enemy, kind) : chip(battle, enemy);
    }

    private Strike hit(Battle battle, ClanWarParticipant enemy, Kind kind) {
        double disparity = warDamage.disparity(skill(battle, battle.attacker()), skill(battle, enemy));
        double damage = warDamage.hitDamage(
                battle.cycleHits().getOrDefault(enemy.getUser().getId(), ClanWarHitRepository.Streak.NONE),
                battle.play().mapDifficultyId(), disparity * battle.lastStand(), kind == Kind.MISSING);
        return new Strike(enemy, kind, damage, standing(battle, enemy));
    }

    private Strike chip(Battle battle, ClanWarParticipant enemy) {
        Double gap = chipGap(battle, enemy);
        return gap == null ? null : new Strike(enemy, Kind.CHIP,
                warDamage.chip(gap, skill(battle, battle.attacker()), skill(battle, enemy)) * battle.lastStand(),
                standing(battle, enemy));
    }

    private double standing(Battle battle, ClanWarParticipant enemy) {
        return stakeService.breakAmount(battle.sides(), battle.sideByClan().get(enemy.getClan().getId()), enemy);
    }

    private Kind hitKind(Battle battle, ClanWarParticipant enemy) {
        ClanWarHitRepository.VictimScoreView victimScore = battle.victimScores().get(enemy.getUser().getId());
        if (victimScore == null) {
            return Kind.MISSING;
        }
        return victimScore.getScore() < battle.play().score() ? Kind.HIT : null;
    }

    private Double chipGap(Battle battle, ClanWarParticipant enemy) {
        ClanWarHitRepository.VictimScoreView victimScore = battle.victimScores().get(enemy.getUser().getId());
        if (victimScore == null || battle.maxScore() == null || battle.maxScore() <= 0
                || victimScore.getScore() < battle.play().score()) {
            return null;
        }
        double gap = linear(victimScore.getScore(), battle.maxScore())
                - linear(battle.play().score(), battle.maxScore());
        return gap < clanProperties.getWar().getChipWindow() ? gap : null;
    }

    private boolean strike(Battle battle, Strike strike) {
        ClanWarParticipant victim = strike.victim();
        ClanWarParticipant attacker = battle.attacker();
        ClanWarHitRepository.VictimScoreView victimScore = battle.victimScores().get(victim.getUser().getId());
        double damage = Math.min(victim.getGuard(), strike.damage());
        if (damage <= 0) {
            return false;
        }
        victim.setGuard(Math.max(0.0, victim.getGuard() - damage));
        double share = sizeShare(battle);
        attacker.setContribution(attacker.getContribution() + damage * share);
        UUID difficultyId = battle.play().mapDifficultyId();
        ClanWarHit hit = hitRepository.saveAndFlush(ClanWarHit.builder()
                .war(battle.war())
                .attacker(attacker.getUser())
                .victim(victim.getUser())
                .victimCycle(victim.getGuardCycle())
                .mapDifficulty(mapDifficultyRepository.getReferenceById(difficultyId))
                .attackerScore(scoreRepository.getReferenceById(battle.play().scoreId()))
                .victimScore(victimScore == null ? null : scoreRepository.getReferenceById(victimScore.getScoreId()))
                .chip(strike.kind() == Kind.CHIP)
                .damage(damage)
                .guardAfter(victim.getGuard())
                .broke(victim.getGuard() <= 0.0)
                .build());
        if (hit.isBroke()) {
            breakGuard(battle, victim, hit, share);
        }
        feed.hit(battle.war(), hit, List.copyOf(battle.sideByClan().keySet()));
        if (hit.isBroke() || !hit.isChip()) {
            announce(battle.war(), attacker, victim, hit.isBroke() ? ChatEvent.war_break : ChatEvent.war_hit);
        }
        return true;
    }

    private void breakGuard(Battle battle, ClanWarParticipant victim, ClanWarHit hit, double share) {
        ClanProperties.War config = clanProperties.getWar();
        ClanWar war = battle.war();
        ClanWarSide victimSide = battle.sideByClan().get(victim.getClan().getId());
        ClanWarTeam victimTeam = victimSide.getTeam();
        double applied = stakeService.breakAmount(battle.sides(), victimSide, victim);
        victimSide.setStakeRemaining(victimSide.getStakeRemaining() - applied);
        hit.setStandingMoved(applied);
        victim.setBreaksSuffered(victim.getBreaksSuffered() + 1);
        victim.breakGuard(Instant.now(), config.getBrokenDuration());
        markWipe(battle, victimTeam, victim.getBrokenAt());
        double engagement = stakeService.engagement(war, victimTeam);
        String sourceId = hit.getId().toString();
        UUID seasonId = war.getSeason().getId();
        UUID attackerClanId = battle.attacker().getClan().getId();
        standingService.applyAll(seasonId, Map.of(victim.getClan().getId(), -applied, attackerClanId,
                applied * engagement), ClanStandingSource.war_break, sourceId);
        levelService.grantXp(attackerClanId, new ClanXpAward(config.getBreakClanXp() * share * engagement,
                ClanXpSource.war_break, sourceId, true));
        payChippers(battle, victim, share * engagement * victim.getBounty());
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

    private void markWipe(Battle battle, ClanWarTeam team, Instant at) {
        Collection<ClanWarParticipant> roster = battle.roster().values();
        List<ClanWarParticipant> fighters = ClanWarDamage.team(roster, battle.sideByClan(), team);
        if (battle.war().getWipedAt() != null || fighters.isEmpty()
                || fighters.stream().anyMatch(p -> p.getBrokenAt() == null)) {
            return;
        }
        battle.war().setWipedAt(at);
        Instant back = at.plus(clanProperties.getWar().getWipeDuration());
        for (ClanWarTeam side : ClanWarTeam.values()) {
            double speed = warDamage.reviveSpeed(roster, battle.sideByClan(), side);
            ClanWarDamage.team(roster, battle.sideByClan(), side).stream()
                    .filter(p -> p.getBrokenAt() != null)
                    .forEach(p -> p.comeBackAt(side == team || p.getReviveAt() == null || p.getReviveAt().isAfter(back)
                            ? back : p.getReviveAt(), speed));
        }
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
        if (attacker.getBrokenAt() == null || !play.setAt().isAfter(attacker.getBrokenAt())) {
            return;
        }
        attacker.restoreGuard(clanProperties.getWar().getGuard());
    }

    private void announce(ClanWar war, ClanWarParticipant attacker, ClanWarParticipant victim, ChatEvent event) {
        ChatNotice notice = new ChatNotice(event, attacker.getUser(), victim.getUser(), null, war);
        chatChannel.announce(attacker.getClan(), notice);
        chatChannel.announce(victim.getClan(), notice);
    }

    private static double skill(Battle battle, ClanWarParticipant participant) {
        return ClanWarDamage.skill(battle.skills().getOrDefault(participant.getUser().getId(), 0.0));
    }

    static double linear(int score, int maxScore) {
        return -Math.log10(1.0 - Math.min(MAX_ACCURACY, (double) score / maxScore));
    }

    static double unit(String key) {
        return (key.hashCode() & 0x7fffffff) / (double) Integer.MAX_VALUE;
    }
}
