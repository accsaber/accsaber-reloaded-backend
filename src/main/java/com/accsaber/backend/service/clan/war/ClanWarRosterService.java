package com.accsaber.backend.service.clan.war;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.clan.ClanWarStrikePreviewResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarParticipantResponse;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.entity.chat.ChatEvent;
import com.accsaber.backend.model.entity.clan.war.ClanRuleset;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarParticipant;
import com.accsaber.backend.model.entity.clan.war.ClanWarSide;
import com.accsaber.backend.model.entity.clan.war.ClanWarStatus;
import com.accsaber.backend.model.entity.clan.war.ClanWarTeam;
import com.accsaber.backend.model.entity.user.User;
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
import com.accsaber.backend.service.clan.ClanMapper;
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
    private final ClanWarDamage warDamage;
    private final ClanCosmeticService cosmeticService;
    private final ClanChatChannel chatChannel;
    private final ClanWarScoreGate scoreGate;
    private final ClanFeed feed;
    private final ClanNotifier notifier;
    private final ClanProperties clanProperties;

    public void prepare(ClanWar war) {
        war.setStatus(ClanWarStatus.preparing);
        ClanProperties.War config = clanProperties.getWar();
        Duration prep = war.getPicksDueAt() == null ? config.getPrepDuration().plus(config.getPickWindow())
                : config.getPrepDuration();
        war.setStartsAt(Instant.now().plus(prep));
        warRepository.saveAndFlush(war);
        List<ClanWarSide> sides = sideRepository.findByWarId(war.getId());
        sync(war, sides);
        feed.war(war);
        announce(war, sides, ChatEvent.war_preparing);
    }

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
        announce(war, sides, ChatEvent.war_started);
    }

    @Transactional
    public void restoreGuards(UUID warId, Instant brokenBefore) {
        ClanWar war = warRepository.findByIdForUpdate(warId).orElse(null);
        if (war != null && war.getStatus() == ClanWarStatus.active) {
            participantRepository.restoreGuards(warId, brokenBefore, clanProperties.getWar().getGuard());
        }
    }

    private void announce(ClanWar war, List<ClanWarSide> sides, ChatEvent event) {
        for (ClanWarSide side : sides) {
            if (side.getLeftAt() == null) {
                chatChannel.announce(side.getClan(), ChatNotice.ofWar(event, null,
                        war.lead(side.getTeam().opponent()), war));
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMembershipChanged(ClanMembershipChangedEvent event) {
        warRepository.findRosteredIds(event.clanId()).forEach(this::resync);
    }

    @Transactional
    public CompletableFuture<Void> resyncAll() {
        warRepository.findRosteredIds(null).forEach(this::resync);
        return CompletableFuture.completedFuture(null);
    }

    private void resync(UUID warId) {
        warRepository.findByIdForUpdate(warId)
                .filter(ClanWar::rostered)
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
        double chooserSkill = skills(List.of(playerId)).getOrDefault(playerId, 0.0);
        if (targetUserId == null) {
            chooser.setTarget(null);
        } else {
            Map<UUID, ClanWarTeam> teams = teams(sideRepository.findByWarId(warId));
            ClanWarParticipant target = roster.get(targetUserId);
            if (target == null || teams.get(target.getClan().getId()) == teams.get(chooser.getClan().getId())) {
                throw new ValidationException("userId", "must be an enemy still fighting in this war");
            }
            chooser.setTarget(target.getUser());
        }
        participantRepository.saveAndFlush(chooser);
        return ClanMapper.toWarParticipantResponse(chooser, cosmeticService.publicRefs(List.of(chooser.getClan()))
                .get(chooser.getClan().getId()), chooserSkill, null);
    }

    @Transactional(readOnly = true)
    public Page<ClanWarParticipantResponse> participants(UUID warId, Long viewerId, UUID mapDifficultyId,
            Pageable pageable) {
        ClanWar war = warRepository.findById(warId).orElseThrow(() -> new ResourceNotFoundException("ClanWar", warId));
        List<ClanWarSide> sides = sideRepository.findByWarId(warId);
        Map<UUID, PublicClanResponse> clans = cosmeticService.publicRefs(sides.stream().map(ClanWarSide::getClan).toList());
        Page<ClanWarParticipant> page = participantRepository.findPageByWarId(warId, pageable);
        List<Long> userIds = page.getContent().stream().map(p -> p.getUser().getId()).collect(Collectors.toList());
        if (viewerId != null) {
            userIds.add(viewerId);
        }
        Map<Long, Double> skills = userIds.isEmpty() ? Map.of() : skills(userIds);
        Map<Long, ClanWarStrikePreviewResponse> previews = strikePreviews(war, sides,
                new ClanWarDamage.Viewer(warId, viewerId, mapDifficultyId), page.getContent(), skills);
        return page.map(p -> ClanMapper.toWarParticipantResponse(p, clans.get(p.getClan().getId()),
                skills.getOrDefault(p.getUser().getId(), 0.0), previews.get(p.getUser().getId())));
    }

    private Map<Long, ClanWarStrikePreviewResponse> strikePreviews(ClanWar war, List<ClanWarSide> sides,
            ClanWarDamage.Viewer viewer, List<ClanWarParticipant> rows, Map<Long, Double> skills) {
        Map<UUID, ClanWarSide> sideByClan = sides.stream()
                .collect(Collectors.toMap(side -> side.getClan().getId(), Function.identity()));
        ClanWarSide viewerSide = viewer.userId() == null || !war.rostered() ? null
                : participantRepository.findOpenClanId(war.getId(), viewer.userId()).map(sideByClan::get)
                        .filter(ClanWarSide::fighting).orElse(null);
        if (viewerSide == null) {
            return Map.of();
        }
        return warDamage.previews(viewer, ClanWarDamage.enemies(rows, sideByClan, viewerSide.getTeam()), skills);
    }

    public List<Long> fighterIds(UUID warId) {
        return participantRepository.findOpenUserIds(warId);
    }

    public record Pairing(User target, boolean picked) {
    }

    public Map<UUID, Pairing> pairings(ClanWar war, Long viewerId, Collection<UUID> difficultyIds) {
        if (viewerId == null || difficultyIds.isEmpty() || !war.rostered() || war.getRuleset() != ClanRuleset.duel) {
            return Map.of();
        }
        List<ClanWarParticipant> roster = participantRepository.findActiveByWarId(war.getId());
        List<ClanWarSide> sides = sideRepository.findByWarId(war.getId());
        Map<UUID, ClanWarSide> sideByClan = sides.stream()
                .collect(Collectors.toMap(side -> side.getClan().getId(), Function.identity()));
        ClanWarParticipant viewer = roster.stream().filter(p -> p.getUser().getId().equals(viewerId)).findFirst()
                .orElse(null);
        ClanWarSide viewerSide = viewer == null ? null : sideByClan.get(viewer.getClan().getId());
        if (viewerSide == null || !viewerSide.fighting()) {
            return Map.of();
        }
        List<ClanWarParticipant> enemies = ClanWarDamage.enemies(roster, sideByClan, viewerSide.getTeam());
        Long pickedId = viewer.getTarget() == null ? null : viewer.getTarget().getId();
        Optional<User> picked = enemies.stream().map(ClanWarParticipant::getUser)
                .filter(user -> user.getId().equals(pickedId)).findFirst();
        if (picked.isPresent()) {
            return difficultyIds.stream()
                    .collect(Collectors.toMap(Function.identity(), id -> new Pairing(picked.get(), true)));
        }
        List<Long> fighters = Stream.concat(enemies.stream().map(p -> p.getUser().getId()), Stream.of(viewerId))
                .toList();
        ClanWarDamage.Field field = new ClanWarDamage.Field(war.getId(), viewerId, enemies, skills(fighters),
                enemy -> stakeService.breakAmount(sides, sideByClan.get(enemy.getClan().getId()), enemy));
        return warDamage.bestPairings(field, difficultyIds).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> new Pairing(entry.getValue().getUser(), false)));
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
        clearIllegalTargets(existing, teams);
        participantRepository.saveAll(existing.values());
        scoreGate.refreshAfterCommit();
    }

    private void clearIllegalTargets(Map<Long, ClanWarParticipant> participants, Map<UUID, ClanWarTeam> teams) {
        for (ClanWarParticipant participant : participants.values()) {
            if (participant.getTarget() == null) {
                continue;
            }
            ClanWarParticipant target = participants.get(participant.getTarget().getId());
            boolean legal = participant.getLeftAt() == null && target != null && target.getLeftAt() == null
                    && teams.get(target.getClan().getId()) != teams.get(participant.getClan().getId());
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
