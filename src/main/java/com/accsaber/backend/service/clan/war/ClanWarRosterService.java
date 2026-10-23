package com.accsaber.backend.service.clan.war;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
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
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.response.clan.ClanWarStrikePreviewResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarParticipantResponse;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.entity.chat.ChatEvent;
import com.accsaber.backend.model.entity.clan.war.ClanRuleset;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarOrder;
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
        long spread = config.getPrepMax().minus(config.getPrepMin()).toSeconds();
        war.setStartsAt(Instant.now().plus(config.getPrepMin()).plusSeconds(ThreadLocalRandom.current().nextLong(spread + 1)));
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
    public void restoreGuards(UUID warId, Instant brokenBefore, Instant wipedBefore) {
        ClanWar war = warRepository.findByIdForUpdate(warId).orElse(null);
        if (war != null && war.getStatus() == ClanWarStatus.active) {
            participantRepository.restoreGuards(warId, brokenBefore, wipedBefore, clanProperties.getWar().getGuard());
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
        UUID viewerClanId = viewerId == null ? null : participantRepository.findOpenClanId(warId, viewerId).orElse(null);
        Map<Long, ClanWarStrikePreviewResponse> previews = strikePreviews(war, sides,
                new ClanWarDamage.Viewer(warId, viewerId, viewerClanId, mapDifficultyId), page.getContent(), skills);
        return page.map(p -> ClanMapper.toWarParticipantResponse(p, clans.get(p.getClan().getId()),
                new ClanMapper.WarParticipantStats(skills.getOrDefault(p.getUser().getId(), 0.0),
                        previews.get(p.getUser().getId()), p.getClan().getId().equals(viewerClanId))));
    }

    private Map<Long, ClanWarStrikePreviewResponse> strikePreviews(ClanWar war, List<ClanWarSide> sides,
            ClanWarDamage.Viewer viewer, List<ClanWarParticipant> rows, Map<Long, Double> skills) {
        Map<UUID, ClanWarSide> sideByClan = sides.stream()
                .collect(Collectors.toMap(side -> side.getClan().getId(), Function.identity()));
        ClanWarSide viewerSide = viewer.clanId() == null || !war.rostered() ? null
                : Optional.ofNullable(sideByClan.get(viewer.clanId())).filter(ClanWarSide::fighting).orElse(null);
        if (viewerSide == null) {
            return Map.of();
        }
        ClanWarDamage.Field field = new ClanWarDamage.Field(war.getId(), viewer.userId(),
                ClanWarDamage.enemies(rows, sideByClan, viewerSide.getTeam()), skills,
                enemy -> stakeService.breakAmount(sides, sideByClan.get(enemy.getClan().getId()), enemy));
        return warDamage.previews(viewer, field, stakeService.engagement(war, viewerSide.getTeam().opponent()));
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
        if (viewerSide == null || !viewerSide.fighting() || viewer.halted()) {
            return Map.of();
        }
        List<ClanWarParticipant> enemies = ClanWarDamage.enemies(roster, sideByClan, viewerSide.getTeam());
        Long pickedId = viewer.aim() == null ? null : viewer.aim().getId();
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
        Map<ClanWarTeam, Double> teamHeft = new EnumMap<>(ClanWarTeam.class);
        for (ClanWarTeam team : ClanWarTeam.values()) {
            List<UUID> clanIds = sides.stream()
                    .filter(side -> side.getTeam() == team && side.fighting())
                    .map(side -> side.getClan().getId())
                    .toList();
            List<MemberStrength> members = strengthService.memberStrengths(clanIds);
            members.forEach(member -> roster.put(member.userId(), member));
            teamHeft.put(team, members.stream().mapToDouble(ClanWarRosterService::heft).sum());
        }
        Map<ClanWarTeam, Double> divisors = divisors(teamHeft);
        ToDoubleFunction<MemberStrength> weight = member -> heft(member) / divisors.get(teams.get(member.clanId()));
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
                participant.setStandingWeight(weight.applyAsDouble(member));
            }
        }
        for (MemberStrength member : roster.values()) {
            if (!existing.containsKey(member.userId())) {
                existing.put(member.userId(), ClanWarParticipant.builder()
                        .war(war)
                        .user(userRepository.getReferenceById(member.userId()))
                        .clan(sideByClan.get(member.clanId()).getClan())
                        .standingWeight(weight.applyAsDouble(member))
                        .guard(clanProperties.getWar().getGuard())
                        .build());
            }
        }
        clearIllegalTargets(existing, teams);
        participantRepository.saveAll(existing.values());
        scoreGate.refreshAfterCommit();
    }

    private Map<ClanWarTeam, Double> divisors(Map<ClanWarTeam, Double> teamHeft) {
        double pull = clanProperties.getWar().getBreakWeightPull();
        double heaviest = Collections.max(teamHeft.values());
        Map<ClanWarTeam, Double> divisors = new EnumMap<>(ClanWarTeam.class);
        teamHeft.forEach((team, heft) -> divisors.put(team, Math.pow(heft, 1 - pull) * Math.pow(heaviest, pull)));
        return divisors;
    }

    private static double heft(MemberStrength member) {
        double skill = ClanWarDamage.skill(member.skill());
        return skill * skill;
    }

    private void clearIllegalTargets(Map<Long, ClanWarParticipant> participants, Map<UUID, ClanWarTeam> teams) {
        for (ClanWarParticipant participant : participants.values()) {
            if (!legal(participant, participant.getTarget(), participants, teams)) {
                participant.setTarget(null);
            }
            if (!legal(participant, participant.getOrderedTarget(), participants, teams)) {
                participant.setOrder(ClanWarOrder.auto);
                participant.setOrderedTarget(null);
            }
        }
    }

    private static boolean legal(ClanWarParticipant participant, User aim, Map<Long, ClanWarParticipant> participants,
            Map<UUID, ClanWarTeam> teams) {
        if (aim == null) {
            return true;
        }
        ClanWarParticipant target = participants.get(aim.getId());
        return participant.getLeftAt() == null && target != null && target.getLeftAt() == null
                && teams.get(target.getClan().getId()) != teams.get(participant.getClan().getId());
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
