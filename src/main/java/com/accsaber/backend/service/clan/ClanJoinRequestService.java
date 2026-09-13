package com.accsaber.backend.service.clan;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.clan.ClanJoinRequestResponse;
import com.accsaber.backend.model.dto.response.item.ItemResponse;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanJoinDirection;
import com.accsaber.backend.model.entity.clan.ClanJoinRequest;
import com.accsaber.backend.model.entity.clan.ClanJoinStatus;
import com.accsaber.backend.model.entity.clan.ClanRole;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.clan.ClanJoinRequestRepository;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.ClanRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClanJoinRequestService {

    private final ClanJoinRequestRepository joinRequestRepository;
    private final ClanMemberRepository memberRepository;
    private final ClanRepository clanRepository;
    private final ClanRoster roster;
    private final ClanAccessService accessService;
    private final ClanCosmeticService cosmeticService;

    @Transactional
    public ClanJoinRequestResponse create(UUID clanId, Long playerId, Long invitedUserId) {
        User actor = accessService.player(playerId);
        Clan clan = clanRepository.findByIdAndActiveTrue(clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Clan", clanId));
        boolean invite = invitedUserId != null && !invitedUserId.equals(actor.getId());
        User subject = invite ? inviteSubject(clan, actor, invitedUserId) : requestSubject(clan, actor);
        if (joinRequestRepository.existsByClan_IdAndUser_IdAndStatus(clanId, subject.getId(), ClanJoinStatus.pending)) {
            throw new ConflictException("There is already a pending request between this player and this clan");
        }
        ClanJoinRequest request = joinRequestRepository.saveAndFlush(ClanJoinRequest.builder()
                .clan(clan)
                .user(subject)
                .direction(invite ? ClanJoinDirection.invite : ClanJoinDirection.request)
                .createdBy(actor)
                .build());
        return toResponse(request);
    }

    public Page<ClanJoinRequestResponse> mine(Long playerId, Pageable pageable) {
        User viewer = accessService.player(playerId);
        return toResponses(joinRequestRepository.findPendingByUserId(viewer.getId(), pageable));
    }

    public Page<ClanJoinRequestResponse> forClan(UUID clanId, Long playerId, Pageable pageable) {
        User viewer = accessService.player(playerId);
        accessService.require(clanId, viewer.getId(), ClanPermission.RESOLVE_REQUESTS);
        return toResponses(joinRequestRepository.findPendingByClanId(clanId, pageable));
    }

    @Transactional
    public ClanJoinRequestResponse resolve(UUID requestId, Long playerId, ClanJoinStatus status) {
        User actor = accessService.player(playerId);
        ClanJoinRequest request = joinRequestRepository.findWithRefsById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("ClanJoinRequest", requestId));
        if (request.getStatus() != ClanJoinStatus.pending) {
            throw new ConflictException("This request has already been resolved");
        }
        if (status == ClanJoinStatus.pending || status == ClanJoinStatus.expired) {
            throw new ValidationException("status", "must be accepted, declined or cancelled");
        }
        assertMayResolve(request, actor, status);
        request.setStatus(status);
        request.setResolvedBy(actor);
        request.setResolvedAt(Instant.now());
        joinRequestRepository.saveAndFlush(request);
        if (status == ClanJoinStatus.accepted) {
            roster.admit(roster.lock(request.getClan().getId()), request.getUser(), ClanRole.member);
        }
        return toResponse(request);
    }

    private ClanJoinRequestResponse toResponse(ClanJoinRequest request) {
        UUID clanId = request.getClan().getId();
        return ClanJoinRequestResponse.of(request,
                cosmeticService.equippedByClanIds(List.of(clanId)).getOrDefault(clanId, List.of()));
    }

    private Page<ClanJoinRequestResponse> toResponses(Page<ClanJoinRequest> page) {
        Map<UUID, List<ItemResponse>> equipped = cosmeticService.equippedByClanIds(
                page.getContent().stream().map(r -> r.getClan().getId()).distinct().toList());
        return page.map(r -> ClanJoinRequestResponse.of(r, equipped.getOrDefault(r.getClan().getId(), List.of())));
    }

    private User inviteSubject(Clan clan, User actor, Long invitedUserId) {
        accessService.require(clan.getId(), actor.getId(), ClanPermission.INVITE);
        User invited = accessService.player(invitedUserId);
        if (memberRepository.findOpenByClanIdAndUserId(clan.getId(), invited.getId()).isPresent()) {
            throw new ConflictException("That player is already in this clan");
        }
        return invited;
    }

    private User requestSubject(Clan clan, User actor) {
        if (!clan.isAcceptingRequests()) {
            throw new ValidationException("This clan is not taking join requests right now");
        }
        roster.assertCanJoin(actor.getId());
        return actor;
    }

    private void assertMayResolve(ClanJoinRequest request, User actor, ClanJoinStatus status) {
        boolean subject = request.getUser().getId().equals(actor.getId());
        boolean invite = request.getDirection() == ClanJoinDirection.invite;
        UUID clanId = request.getClan().getId();
        if (status == ClanJoinStatus.cancelled) {
            if (invite) {
                accessService.require(clanId, actor.getId(), ClanPermission.INVITE);
            } else if (!subject) {
                throw new ForbiddenException("Only the player who asked can cancel a join request");
            }
            return;
        }
        if (!invite) {
            accessService.require(clanId, actor.getId(), ClanPermission.RESOLVE_REQUESTS);
        } else if (!subject) {
            throw new ForbiddenException("Only the invited player can answer an invite");
        }
    }
}
