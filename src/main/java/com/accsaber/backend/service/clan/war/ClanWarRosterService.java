package com.accsaber.backend.service.clan.war;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.clan.ClanWarParticipantResponse;
import com.accsaber.backend.model.entity.chat.ChatEvent;
import com.accsaber.backend.model.entity.clan.war.ClanRuleset;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarParticipant;
import com.accsaber.backend.model.entity.clan.war.ClanWarSide;
import com.accsaber.backend.model.entity.clan.war.ClanWarStatus;
import com.accsaber.backend.model.entity.clan.war.ClanWarTeam;
import com.accsaber.backend.model.event.ClanMembershipChangedEvent;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.war.ClanWarParticipantRepository;
import com.accsaber.backend.repository.clan.war.ClanWarRepository;
import com.accsaber.backend.repository.clan.war.ClanWarSideRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.clan.ChatNotice;
import com.accsaber.backend.service.clan.ClanChatChannel;
import com.accsaber.backend.service.clan.ClanCosmeticService;
import com.accsaber.backend.service.clan.ClanFeed;
import com.accsaber.backend.service.clan.ClanNotifier;
import com.accsaber.backend.service.clan.ClanStrengthService;
import com.accsaber.backend.service.clan.ClanStrengthService.MemberStrength;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClanWarRosterService {

    private final ClanWarRepository warRepository;
    private final ClanWarSideRepository sideRepository;
    private final ClanWarParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final ClanStrengthService strengthService;
    private final ClanWarStakeService stakeService;
    private final ClanCosmeticService cosmeticService;
    private final ClanChatChannel chatChannel;
    private final ClanWarScoreGate scoreGate;
    private final ClanFeed feed;
    private final ClanNotifier notifier;
    private final ClanProperties clanProperties;

    @Transactional
    public void start(UUID warId) {
        ClanWar war = warRepository.findByIdForUpdate(warId).orElse(null);
        if (war == null || war.getStatus() != ClanWarStatus.preparing || war.getStartsAt().isAfter(Instant.now())) {
            return;
        }
        war.setStatus(ClanWarStatus.active);
        warRepository.saveAndFlush(war);
        List<ClanWarSide> sides = sideRepository.findByWarId(warId);
        stakeService.rebalance(sides);
        sync(war, sides);
        feed.war(war);
        notifier.warStarted(war, sides);
        for (ClanWarSide side : sides) {
            if (side.getLeftAt() == null) {
                chatChannel.announce(side.getClan(), ChatNotice.ofWar(ChatEvent.war_started, null,
                        war.lead(side.getTeam().opponent()), war));
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMembershipChanged(ClanMembershipChangedEvent event) {
        warRepository.findActiveIdsByClanId(event.clanId()).forEach(this::resync);
    }

    @Transactional
    public void resync(UUID warId) {
        warRepository.findByIdForUpdate(warId)
                .filter(war -> war.getStatus() == ClanWarStatus.active)
                .ifPresent(war -> sync(war, sideRepository.findByWarId(warId)));
    }

    @Transactional
    public ClanWarParticipantResponse choose(UUID warId, Long playerId, Long targetUserId) {
        ClanWar war = warRepository.lockOrThrow(warId);
        if (war.getStatus() == ClanWarStatus.ended) {
            throw new ConflictException("This war is already over");
        }
        if (war.getRuleset() != ClanRuleset.duel) {
            throw new ConflictException("Only duels have targets");
        }
        Map<Long, ClanWarParticipant> roster = participantRepository.findActiveByWarId(warId).stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), Function.identity()));
        ClanWarParticipant chooser = roster.get(playerId);
        if (chooser == null) {
            throw new ForbiddenException("You are not fighting in this war");
        }
        Map<Long, Double> skills = skills(targetUserId == null ? List.of(playerId) : List.of(playerId, targetUserId));
        double chooserSkill = skills.getOrDefault(playerId, 0.0);
        if (targetUserId == null) {
            chooser.setTarget(null);
        } else {
            Map<UUID, ClanWarTeam> teams = teams(sideRepository.findByWarId(warId));
            ClanWarParticipant target = roster.get(targetUserId);
            if (target == null || teams.get(target.getClan().getId()) == teams.get(chooser.getClan().getId())) {
                throw new ValidationException("userId", "must be an enemy still fighting in this war");
            }
            if (!inBand(chooserSkill, skills.getOrDefault(targetUserId, 0.0))) {
                throw new ValidationException("userId", "is too far below your skill to pick as a target");
            }
            chooser.setTarget(target.getUser());
        }
        participantRepository.saveAndFlush(chooser);
        return ClanWarParticipantResponse.of(chooser, cosmeticService.publicRefs(List.of(chooser.getClan()))
                .get(chooser.getClan().getId()), chooserSkill, null);
    }

    public boolean inBand(double chooserSkill, double targetSkill) {
        return targetSkill >= clanProperties.getWar().getTargetBand() * chooserSkill;
    }

    public void sync(ClanWar war, List<ClanWarSide> sides) {
        Map<UUID, ClanWarTeam> teams = teams(sides);
        Map<Long, MemberStrength> roster = new HashMap<>();
        for (ClanWarTeam team : ClanWarTeam.values()) {
            List<UUID> clanIds = sides.stream()
                    .filter(side -> side.getTeam() == team && side.fighting())
                    .map(side -> side.getClan().getId())
                    .toList();
            strengthService.memberStrengths(clanIds).forEach(member -> roster.put(member.userId(), member));
        }
        Map<UUID, ClanWarSide> sideByClan = sides.stream()
                .collect(Collectors.toMap(side -> side.getClan().getId(), Function.identity()));
        Map<Long, ClanWarParticipant> existing = participantRepository.findByWarId(war.getId()).stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), Function.identity()));
        Instant now = Instant.now();
        for (ClanWarParticipant participant : existing.values()) {
            MemberStrength member = roster.get(participant.getUser().getId());
            boolean stillIn = member != null && member.clanId().equals(participant.getClan().getId());
            participant.setLeftAt(stillIn ? null : participant.getLeftAt() != null ? participant.getLeftAt() : now);
            if (stillIn) {
                participant.setStandingWeight(member.share());
            }
        }
        for (MemberStrength member : roster.values()) {
            if (!existing.containsKey(member.userId())) {
                existing.put(member.userId(), ClanWarParticipant.builder()
                        .war(war)
                        .user(userRepository.getReferenceById(member.userId()))
                        .clan(sideByClan.get(member.clanId()).getClan())
                        .standingWeight(member.share())
                        .guard(clanProperties.getWar().getGuard())
                        .build());
            }
        }
        clearIllegalTargets(existing, teams, roster);
        participantRepository.saveAll(existing.values());
        scoreGate.refreshAfterCommit();
    }

    private void clearIllegalTargets(Map<Long, ClanWarParticipant> participants, Map<UUID, ClanWarTeam> teams,
            Map<Long, MemberStrength> roster) {
        for (ClanWarParticipant participant : participants.values()) {
            if (participant.getTarget() == null) {
                continue;
            }
            ClanWarParticipant target = participants.get(participant.getTarget().getId());
            boolean legal = participant.getLeftAt() == null && target != null && target.getLeftAt() == null
                    && teams.get(target.getClan().getId()) != teams.get(participant.getClan().getId())
                    && inBand(roster.get(participant.getUser().getId()).skill(),
                            roster.get(target.getUser().getId()).skill());
            if (!legal) {
                participant.setTarget(null);
            }
        }
    }

    private Map<Long, Double> skills(List<Long> userIds) {
        return participantRepository.findOverallSkills(userIds).stream()
                .collect(Collectors.toMap(ClanMemberRepository.MemberSkillView::getUserId,
                        ClanMemberRepository.MemberSkillView::getSkill));
    }

    static Map<UUID, ClanWarTeam> teams(List<ClanWarSide> sides) {
        return sides.stream().collect(Collectors.toMap(side -> side.getClan().getId(), ClanWarSide::getTeam));
    }
}
