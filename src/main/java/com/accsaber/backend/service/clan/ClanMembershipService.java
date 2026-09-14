package com.accsaber.backend.service.clan;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.clan.ClanMemberResponse;
import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.entity.chat.ChatEvent;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanAuditAction;
import com.accsaber.backend.model.entity.clan.ClanAuditEntry;
import com.accsaber.backend.model.entity.clan.ClanCapacity;
import com.accsaber.backend.model.entity.clan.ClanLeaveReason;
import com.accsaber.backend.model.entity.clan.ClanMember;
import com.accsaber.backend.model.entity.clan.ClanRole;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.event.PlayerBannedEvent;
import com.accsaber.backend.model.event.PlayersMergedEvent;
import com.accsaber.backend.repository.clan.ClanAuditEntryRepository;
import com.accsaber.backend.repository.clan.ClanJoinRequestRepository;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.ClanRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.websocket.server.NotificationWebSocketHandler;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClanMembershipService {

    private final ClanRepository clanRepository;
    private final ClanMemberRepository memberRepository;
    private final ClanJoinRequestRepository joinRequestRepository;
    private final ClanAuditEntryRepository auditRepository;
    private final ScoreRepository scoreRepository;
    private final UserRepository userRepository;
    private final ClanRoster roster;
    private final ClanAccessService accessService;
    private final ClanLevelService levelService;
    private final ClanService clanService;
    private final NotificationWebSocketHandler notificationHandler;
    private final ClanChatChannel chatChannel;
    private final ClanProperties clanProperties;

    public Page<ClanMemberResponse> roster(UUID clanId, Pageable pageable) {
        if (clanRepository.findByIdAndActiveTrue(clanId).isEmpty()) {
            throw new ResourceNotFoundException("Clan", clanId);
        }
        Page<ClanMember> page = memberRepository.findRoster(clanId, pageable);
        List<Long> userIds = page.getContent().stream().map(m -> m.getUser().getId()).toList();
        Set<Long> online = notificationHandler.onlineAmong(userIds);
        Map<Long, Instant> lastPlayed = lastPlayedAt(userIds);
        return page.map(m -> new ClanMemberResponse(PlayerRef.of(m.getUser()), m.getRole(), m.getJoinedAt(),
                online.contains(m.getUser().getId()), lastPlayed.get(m.getUser().getId())));
    }

    @Transactional
    public ClanMemberResponse changeRole(UUID clanId, Long playerId, Long targetUserId, ClanRole role) {
        User actorUser = accessService.player(playerId);
        Clan clan = roster.lock(clanId);
        ClanMember target = openMember(clanId, targetUserId);
        if (role == ClanRole.founder) {
            throw new ValidationException("role", "hand the clan over through the founder transfer instead");
        }
        ClanPermission permission = role.isAtLeast(ClanRole.commander) || target.getRole().isAtLeast(ClanRole.commander)
                ? ClanPermission.PROMOTE_COMMANDER
                : ClanPermission.PROMOTE_OFFICER;
        ClanMember actor = accessService.require(clanId, actorUser.getId(), permission);
        assertOutranks(actor, target);
        if (target.getRole() == role) {
            return toResponse(target);
        }
        assertFreeRankSlot(clan, role);
        ClanRole previous = target.getRole();
        target.setRole(role);
        memberRepository.saveAndFlush(target);
        auditRepository.save(ClanAuditEntry.builder().clan(clan).actor(actorUser).action(ClanAuditAction.role_changed)
                .targetUser(target.getUser()).details(Map.<String, Object>of("from", previous, "to", role)).build());
        return toResponse(target);
    }

    @Transactional
    public void remove(UUID clanId, Long playerId, Long targetUserId) {
        User actorUser = accessService.player(playerId);
        Clan clan = roster.lock(clanId);
        if (actorUser.getId().equals(targetUserId)) {
            leave(clan, openMember(clanId, targetUserId));
            return;
        }
        ClanMember actor = accessService.require(clanId, actorUser.getId(), ClanPermission.KICK);
        ClanMember target = openMember(clanId, targetUserId);
        assertOutranks(actor, target);
        roster.close(target, ClanLeaveReason.kicked);
        chatChannel.announce(clan, ChatNotice.ofPlayer(ChatEvent.member_kicked, actorUser, target.getUser()));
        auditRepository.save(ClanAuditEntry.builder().clan(clan).actor(actorUser)
                .action(ClanAuditAction.member_kicked).targetUser(target.getUser()).build());
    }

    @Transactional
    public ClanMemberResponse transferFounder(UUID clanId, Long playerId, Long targetUserId) {
        User actorUser = accessService.player(playerId);
        Clan clan = roster.lock(clanId);
        if (actorUser.getId().equals(targetUserId)) {
            return claimFounder(clan, actorUser);
        }
        ClanMember founder = accessService.require(clanId, actorUser.getId(), ClanPermission.TRANSFER);
        ClanMember heir = openMember(clanId, targetUserId);
        crown(clan, founder, heir);
        auditRepository.save(ClanAuditEntry.builder().clan(clan).actor(actorUser)
                .action(ClanAuditAction.founder_transferred).targetUser(heir.getUser()).build());
        return toResponse(heir);
    }

    @EventListener
    @Transactional
    public void onPlayerBanned(PlayerBannedEvent event) {
        joinRequestRepository.expirePendingForUser(event.userId(), Instant.now());
        memberRepository.findOpenByUserId(event.userId()).ifPresent(member -> {
            Clan clan = roster.lock(member.getClan().getId());
            roster.close(member, ClanLeaveReason.banned);
            chatChannel.announce(clan, ChatNotice.ofPlayer(ChatEvent.member_left, member.getUser(), null));
            if (member.getRole() == ClanRole.founder) {
                promoteSuccessor(clan);
            }
        });
    }

    @EventListener
    @Transactional
    public void onPlayersMerged(PlayersMergedEvent event) {
        joinRequestRepository.expirePendingForUser(event.secondaryUserId(), Instant.now());
        memberRepository.findOpenByUserId(event.secondaryUserId()).ifPresent(stint -> {
            Clan clan = roster.lock(stint.getClan().getId());
            ClanRole role = stint.getRole();
            roster.close(stint, ClanLeaveReason.merged);
            if (memberRepository.findOpenByUserId(event.primaryUserId()).isEmpty()) {
                roster.seat(clan, userRepository.getReferenceById(event.primaryUserId()), role);
            } else if (role == ClanRole.founder) {
                promoteSuccessor(clan);
            }
        });
    }

    private void leave(Clan clan, ClanMember member) {
        if (member.getRole() != ClanRole.founder) {
            roster.close(member, ClanLeaveReason.left);
            chatChannel.announce(clan, ChatNotice.ofPlayer(ChatEvent.member_left, member.getUser(), null));
            return;
        }
        if (memberRepository.countByClan_IdAndLeftAtIsNull(clan.getId()) > 1) {
            throw new ValidationException("Hand the clan to another member before leaving it");
        }
        clanService.disband(clan, member.getUser());
    }

    private ClanMemberResponse claimFounder(Clan clan, User claimant) {
        List<ClanMember> members = memberRepository.findRoster(clan.getId(), Pageable.unpaged()).getContent();
        ClanMember founder = members.stream().filter(m -> m.getRole() == ClanRole.founder).findFirst()
                .orElseThrow(() -> new ConflictException("This clan has no founder to replace"));
        ClanMember longestCommander = members.stream().filter(m -> m.getRole() == ClanRole.commander).findFirst()
                .orElseThrow(() -> new ForbiddenException("Only the longest serving commander can claim the clan"));
        if (!longestCommander.getUser().getId().equals(claimant.getId())) {
            throw new ForbiddenException("Only the longest serving commander can claim the clan");
        }
        Instant lastPlayed = lastPlayedAt(List.of(founder.getUser().getId())).get(founder.getUser().getId());
        Instant cutoff = Instant.now().minus(clanProperties.getFounderInactivityDays(), ChronoUnit.DAYS);
        if (lastPlayed != null && lastPlayed.isAfter(cutoff)) {
            throw new ValidationException("The founder has played too recently for the clan to be claimed");
        }
        crown(clan, founder, longestCommander);
        auditRepository.save(ClanAuditEntry.builder().clan(clan).actor(claimant)
                .action(ClanAuditAction.founder_claimed).targetUser(founder.getUser()).build());
        return toResponse(longestCommander);
    }

    private void promoteSuccessor(Clan clan) {
        List<ClanMember> members = memberRepository.findRoster(clan.getId(), Pageable.unpaged()).getContent();
        if (members.isEmpty()) {
            clanService.disband(clan, null);
            return;
        }
        ClanMember heir = members.get(0);
        heir.setRole(ClanRole.founder);
        memberRepository.saveAndFlush(heir);
        auditRepository.save(ClanAuditEntry.builder().clan(clan).action(ClanAuditAction.founder_transferred)
                .targetUser(heir.getUser()).build());
    }

    private void crown(Clan clan, ClanMember founder, ClanMember heir) {
        if (heir.getId().equals(founder.getId())) {
            throw new ValidationException("You already founded this clan");
        }
        founder.setRole(heir.getRole());
        memberRepository.saveAndFlush(founder);
        heir.setRole(ClanRole.founder);
        memberRepository.saveAndFlush(heir);
    }

    private void assertOutranks(ClanMember actor, ClanMember target) {
        if (actor.getId().equals(target.getId())) {
            throw new ValidationException("You cannot do that to yourself");
        }
        if (actor.getRole().compareTo(target.getRole()) <= 0) {
            throw new ForbiddenException("You can only manage members ranked below you");
        }
    }

    private void assertFreeRankSlot(Clan clan, ClanRole role) {
        ClanCapacity slots = switch (role) {
            case officer -> ClanCapacity.officer_slots;
            case commander -> ClanCapacity.commander_slots;
            default -> null;
        };
        if (slots == null) {
            return;
        }
        long holders = memberRepository.countByClan_IdAndRoleAndLeftAtIsNull(clan.getId(), role);
        if (holders >= levelService.capacityOf(clan, slots)) {
            throw new ConflictException("This clan has no free " + role + " slots at its level");
        }
    }

    private ClanMember openMember(UUID clanId, Long userId) {
        return memberRepository.findOpenByClanIdAndUserId(clanId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("ClanMember", userId));
    }

    private Map<Long, Instant> lastPlayedAt(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return scoreRepository.findLastActiveScoreTimes(userIds).stream()
                .filter(view -> view.getLastPlayedAt() != null)
                .collect(Collectors.toMap(ScoreRepository.LastPlayedView::getUserId,
                        ScoreRepository.LastPlayedView::getLastPlayedAt));
    }

    private ClanMemberResponse toResponse(ClanMember member) {
        Long userId = member.getUser().getId();
        return new ClanMemberResponse(PlayerRef.of(member.getUser()), member.getRole(), member.getJoinedAt(),
                !notificationHandler.onlineAmong(List.of(userId)).isEmpty(), lastPlayedAt(List.of(userId)).get(userId));
    }
}
