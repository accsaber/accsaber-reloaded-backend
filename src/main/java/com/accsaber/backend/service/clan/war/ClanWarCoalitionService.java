package com.accsaber.backend.service.clan.war;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.clan.ClanWarAidResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarResponse;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.entity.chat.ChatEvent;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanMember;
import com.accsaber.backend.model.entity.clan.war.ClanRuleset;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarAid;
import com.accsaber.backend.model.entity.clan.war.ClanWarAidDirection;
import com.accsaber.backend.model.entity.clan.war.ClanWarAidStatus;
import com.accsaber.backend.model.entity.clan.war.ClanWarOutcome;
import com.accsaber.backend.model.entity.clan.war.ClanWarSide;
import com.accsaber.backend.model.entity.clan.war.ClanWarStatus;
import com.accsaber.backend.model.entity.clan.war.ClanWarTeam;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.clan.ClanAllianceRepository;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.war.ClanWarAidRepository;
import com.accsaber.backend.repository.clan.war.ClanWarRepository;
import com.accsaber.backend.repository.clan.war.ClanWarSideRepository;
import com.accsaber.backend.service.clan.ChatNotice;
import com.accsaber.backend.service.clan.ClanAccessService;
import com.accsaber.backend.service.clan.ClanChatChannel;
import com.accsaber.backend.service.clan.ClanCosmeticService;
import com.accsaber.backend.service.clan.ClanFeed;
import com.accsaber.backend.service.clan.ClanNotifier;
import com.accsaber.backend.service.clan.ClanPermission;
import com.accsaber.backend.service.clan.ClanRoster;
import com.accsaber.backend.service.clan.ClanStandingService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClanWarCoalitionService {

    private final ClanWarAidRepository aidRepository;
    private final ClanWarRepository warRepository;
    private final ClanWarSideRepository sideRepository;
    private final ClanAllianceRepository allianceRepository;
    private final ClanMemberRepository memberRepository;
    private final ClanRoster roster;
    private final ClanAccessService accessService;
    private final ClanStandingService standingService;
    private final ClanCosmeticService cosmeticService;
    private final ClanWarStakeService stakeService;
    private final ClanWarRosterService rosterService;
    private final ClanWarService warService;
    private final ClanWarResponses warResponses;
    private final ClanChatChannel chatChannel;
    private final ClanFeed feed;
    private final ClanNotifier notifier;

    @Transactional
    public ClanWarAidResponse request(UUID warId, Long playerId, UUID clanId) {
        User actor = accessService.player(playerId);
        Clan helper = roster.lock(clanId);
        ClanWar war = lockWar(warId);
        ClanWarTeam team = war.leadTeamOf(clanOf(actor).getId());
        if (team == null) {
            throw new ForbiddenException("Only the clans that started this war can ask for help");
        }
        accessService.require(war.lead(team).getId(), actor.getId(), ClanPermission.COALITION);
        assertCanJoin(war, helper, team);
        ClanWarAid aid = save(war, helper, team, ClanWarAidDirection.request, actor);
        notifier.aidRequested(aid);
        return responses(List.of(aid)).getFirst();
    }

    @Transactional
    public ClanWarAidResponse offer(UUID warId, Long playerId, ClanWarTeam team) {
        User actor = accessService.player(playerId);
        Clan helper = roster.lock(clanOf(actor).getId());
        accessService.require(helper.getId(), actor.getId(), ClanPermission.COALITION);
        ClanWar war = lockWar(warId);
        assertCanJoin(war, helper, team);
        ClanWarAid aid = save(war, helper, team, ClanWarAidDirection.offer, actor);
        notifier.aidOffered(aid);
        return responses(List.of(aid)).getFirst();
    }

    @Transactional
    public ClanWarAidResponse resolve(UUID aidId, Long playerId, ClanWarAidStatus status) {
        User actor = accessService.player(playerId);
        ClanWarAid aid = aidRepository.findWithRefsById(aidId)
                .orElseThrow(() -> new ResourceNotFoundException("ClanWarAid", aidId));
        Clan helper = roster.lock(aid.getClan().getId());
        ClanWar war = lockWar(aid.getWar().getId());
        if (aid.getStatus() != ClanWarAidStatus.pending) {
            throw new ConflictException("This has already been answered");
        }
        UUID leadId = war.lead(aid.getTeam()).getId();
        boolean fromLead = aid.getDirection() == ClanWarAidDirection.request;
        UUID answering = fromLead ? helper.getId() : leadId;
        switch (status) {
            case accepted, declined -> accessService.require(answering, actor.getId(), ClanPermission.COALITION);
            case cancelled -> accessService.require(fromLead ? leadId : helper.getId(), actor.getId(),
                    ClanPermission.COALITION);
            case pending -> throw new ValidationException("status", "must be accepted, declined or cancelled");
        }
        if (status == ClanWarAidStatus.accepted) {
            assertCanJoin(war, helper, aid.getTeam());
        }
        aid.setStatus(status);
        aid.setResolvedAt(Instant.now());
        aidRepository.saveAndFlush(aid);
        if (status == ClanWarAidStatus.accepted) {
            join(war, helper, aid.getTeam(), actor);
        }
        notifier.aidAnswered(aid, actor);
        return responses(List.of(aid)).getFirst();
    }

    @Transactional
    public ClanWarResponse leave(UUID warId, UUID clanId, Long playerId) {
        User actor = accessService.player(playerId);
        roster.lock(clanId);
        accessService.require(clanId, actor.getId(), ClanPermission.COALITION);
        ClanWar war = lockWar(warId);
        leave(war, clanId, actor);
        return warResponses.of(war);
    }

    @Transactional
    public void leaveAll(UUID clanId) {
        Instant now = Instant.now();
        aidRepository.cancelPendingForClan(clanId, now);
        for (UUID warId : sideRepository.findOpenAllyWarIds(clanId)) {
            leave(lockWar(warId), clanId, null);
        }
    }

    public Page<ClanWarAidResponse> list(UUID warId, UUID clanId, ClanWarAidStatus status, Pageable pageable) {
        Page<ClanWarAid> page = aidRepository.findPage(warId, clanId, status, pageable);
        return new PageImpl<>(responses(page.getContent()), pageable, page.getTotalElements());
    }

    private void join(ClanWar war, Clan helper, ClanWarTeam team, User actor) {
        sideRepository.saveAndFlush(ClanWarSide.joining(war, helper, team, null,
                standingService.currentStanding(helper)));
        List<ClanWarSide> sides = sideRepository.findByWarId(war.getId());
        stakeService.rebalance(sides);
        if (war.getStatus() == ClanWarStatus.active) {
            rosterService.sync(war, sides);
        }
        announce(war, sides, ChatEvent.war_ally_joined, actor, helper);
        feed.war(war);
    }

    private void leave(ClanWar war, UUID clanId, User actor) {
        if (war.getStatus() == ClanWarStatus.ended) {
            throw new ConflictException("This war is already over");
        }
        if (war.leadTeamOf(clanId) != null) {
            throw new ConflictException("The clans that started this war cannot leave it");
        }
        List<ClanWarSide> sides = sideRepository.findByWarId(war.getId());
        ClanWarSide leaving = sides.stream()
                .filter(side -> side.getClan().getId().equals(clanId) && side.getLeftAt() == null)
                .findFirst()
                .orElseThrow(() -> new ValidationException("clanId", "is not fighting in this war"));
        Clan helper = leaving.getClan();
        stakeService.forfeit(war, sides, leaving);
        if (war.getStatus() == ClanWarStatus.active) {
            rosterService.sync(war, sides);
        }
        announce(war, sides, ChatEvent.war_ally_left, actor, helper);
        notifier.allyLeft(war, leaving);
        feed.war(war);
        if (stakeService.lost(sides, leaving.getTeam())) {
            warService.end(war.getId(), ClanWarOutcome.wonBy(leaving.getTeam().opponent()));
        }
    }

    private void assertCanJoin(ClanWar war, Clan helper, ClanWarTeam team) {
        if (war.getStatus() == ClanWarStatus.ended) {
            throw new ConflictException("This war is already over");
        }
        if (war.getRuleset() != ClanRuleset.duel) {
            throw new ConflictException("Only duels can take allies");
        }
        if (helper.isPacifistAt(Instant.now())) {
            throw new ConflictException("That clan is pacifist");
        }
        List<ClanWarSide> sides = sideRepository.findByWarId(war.getId());
        if (sides.stream().anyMatch(side -> side.getClan().getId().equals(helper.getId()))) {
            throw new ConflictException("That clan has already been in this war");
        }
        Clan lead = war.lead(team);
        if (sides.stream().anyMatch(side -> side.getClan().getId().equals(lead.getId()) && side.fallen())) {
            throw new ConflictException(lead.getName() + " has fallen and cannot call for more help");
        }
        if (!allianceRepository.existsActiveBetween(lead.getId(), helper.getId())) {
            throw new ConflictException("Only allies of " + lead.getName() + " can join on their side");
        }
        boolean alliedWithEnemy = sides.stream()
                .filter(side -> side.getTeam() != team && side.getLeftAt() == null)
                .anyMatch(side -> allianceRepository.existsActiveBetween(side.getClan().getId(), helper.getId()));
        if (alliedWithEnemy) {
            throw new ConflictException("That clan is allied with a clan on the other side");
        }
        if (sideRepository.existsOpenAllySide(helper.getId())) {
            throw new ConflictException("That clan is already helping in another war");
        }
        if (team == ClanWarTeam.attack && standingService.currentStanding(helper)
                >= standingService.currentStanding(war.lead(ClanWarTeam.defend))) {
            throw new ConflictException("Only clans below the defender in Standing can join the attack");
        }
    }

    private ClanWarAid save(ClanWar war, Clan helper, ClanWarTeam team, ClanWarAidDirection direction, User actor) {
        if (aidRepository.existsPending(war.getId(), helper.getId())) {
            throw new ConflictException("There is already an open request for that clan in this war");
        }
        return aidRepository.saveAndFlush(ClanWarAid.builder()
                .war(war)
                .clan(helper)
                .team(team)
                .direction(direction)
                .requestedBy(actor)
                .build());
    }

    private Clan clanOf(User actor) {
        return memberRepository.findOpenByUserId(actor.getId())
                .map(ClanMember::getClan)
                .orElseThrow(() -> new ForbiddenException("You are not in a clan"));
    }

    private ClanWar lockWar(UUID warId) {
        return warRepository.findByIdForUpdate(warId)
                .orElseThrow(() -> new ResourceNotFoundException("ClanWar", warId));
    }

    private void announce(ClanWar war, List<ClanWarSide> sides, ChatEvent event, User actor, Clan subject) {
        ChatNotice notice = ChatNotice.ofWar(event, actor, subject, war);
        sides.stream().filter(side -> side.getLeftAt() == null || side.getClan().getId().equals(subject.getId()))
                .forEach(side -> chatChannel.announce(side.getClan(), notice));
    }

    private List<ClanWarAidResponse> responses(List<ClanWarAid> aids) {
        Map<UUID, PublicClanResponse> clans = cosmeticService.publicRefs(aids.stream()
                .flatMap(aid -> Stream.of(aid.getClan(), aid.getWar().lead(aid.getTeam())))
                .toList());
        return aids.stream().map(aid -> ClanWarAidResponse.of(aid, clans)).toList();
    }
}
