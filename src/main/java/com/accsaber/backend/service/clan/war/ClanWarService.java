package com.accsaber.backend.service.clan.war;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.ClanArenaSpec;
import com.accsaber.backend.model.dto.request.clan.DeclareClanWarRequest;
import com.accsaber.backend.model.dto.response.clan.ClanWarDetailResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarHitResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarParticipantResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarResponse;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.dto.response.map.PublicMapDifficultyResponse;
import com.accsaber.backend.model.entity.chat.ChatEvent;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanMember;
import com.accsaber.backend.model.entity.clan.ClanRole;
import com.accsaber.backend.model.entity.clan.ClanSeason;
import com.accsaber.backend.model.entity.clan.ClanWarModeAxis;
import com.accsaber.backend.model.entity.clan.war.ClanArena;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarHit;
import com.accsaber.backend.model.entity.clan.war.ClanWarOutcome;
import com.accsaber.backend.model.entity.clan.war.ClanWarParticipant;
import com.accsaber.backend.model.entity.clan.war.ClanWarSide;
import com.accsaber.backend.model.entity.clan.war.ClanWarStatus;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.event.ClanWarEndedEvent;
import com.accsaber.backend.repository.clan.ClanAllianceRepository;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.war.ClanWarHitRepository;
import com.accsaber.backend.repository.clan.war.ClanWarLoanRepository;
import com.accsaber.backend.repository.clan.war.ClanWarParticipantRepository;
import com.accsaber.backend.repository.clan.war.ClanWarRepository;
import com.accsaber.backend.repository.clan.war.ClanWarSideRepository;
import com.accsaber.backend.service.clan.ChatNotice;
import com.accsaber.backend.service.clan.ClanAccessService;
import com.accsaber.backend.service.clan.ClanChatChannel;
import com.accsaber.backend.service.clan.ClanCosmeticService;
import com.accsaber.backend.service.clan.ClanLevelService;
import com.accsaber.backend.service.clan.ClanNotifier;
import com.accsaber.backend.service.clan.ClanPermission;
import com.accsaber.backend.service.clan.ClanRoster;
import com.accsaber.backend.service.clan.ClanStandingService;
import com.accsaber.backend.service.map.MapService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClanWarService {

    private final ClanWarRepository warRepository;
    private final ClanWarSideRepository sideRepository;
    private final ClanWarParticipantRepository participantRepository;
    private final ClanWarHitRepository hitRepository;
    private final MapService mapService;
    private final ClanAllianceRepository allianceRepository;
    private final ClanMemberRepository memberRepository;
    private final ClanRoster roster;
    private final ClanAccessService accessService;
    private final ClanLevelService levelService;
    private final ClanStandingService standingService;
    private final ClanCosmeticService cosmeticService;
    private final ClanWarPoolService poolService;
    private final ClanWarResponses warResponses;
    private final ClanWarFeed feed;
    private final ClanNotifier notifier;
    private final ClanChatChannel chatChannel;
    private final ClanWarScoreGate scoreGate;
    private final ClanWarLoanRepository loanRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ClanProperties clanProperties;

    @Transactional
    public ClanWarDetailResponse declare(UUID clanId, Long playerId, DeclareClanWarRequest request) {
        User actor = accessService.player(playerId);
        accessService.require(clanId, actor.getId(), ClanPermission.DECLARE_WAR);
        UUID defenderId = request.getClanId();
        if (clanId.equals(defenderId)) {
            throw new ValidationException("clanId", "must be another clan");
        }
        ClanSeason season = standingService.currentSeason()
                .orElseThrow(() -> new ConflictException("Wars wait for the next season to start"));
        List<Clan> pair = roster.lockPair(clanId, defenderId);
        Clan attacker = pair.get(0).getId().equals(clanId) ? pair.get(0) : pair.get(1);
        Clan defender = attacker == pair.get(0) ? pair.get(1) : pair.get(0);
        assertCanDeclare(attacker, defender, request);
        double attackerStanding = standingService.currentStanding(attacker);
        double defenderStanding = standingService.currentStanding(defender);
        assertStandings(attacker, defender, attackerStanding, defenderStanding);
        ClanArenaSpec spec = poolService.spec(request, attackerStanding, defenderStanding);
        ClanWar war = warRepository.saveAndFlush(ClanWar.builder()
                .season(season)
                .attackerClan(attacker)
                .defenderClan(defender)
                .declaredBy(actor)
                .arena(request.getArena())
                .arenaSpec(spec)
                .ruleset(request.getRuleset())
                .picksDueAt(request.getArena() == ClanArena.random ? null
                        : Instant.now().plus(clanProperties.getWar().getPickWindow()))
                .build());
        double stake = Math.min(attackerStanding, defenderStanding);
        sideRepository.saveAllAndFlush(List.of(side(war, attacker, actor, stake, attackerStanding),
                side(war, defender, null, stake, defenderStanding)));
        poolService.seed(war, request.getMapDifficultyIds());
        if (war.getStatus() == ClanWarStatus.picking) {
            feed.war(war);
        }
        chatChannel.announce(attacker, ChatNotice.ofWar(ChatEvent.war_declared, actor, defender, war));
        chatChannel.announce(defender, ChatNotice.ofWar(ChatEvent.war_received, actor, attacker, war));
        notifier.warDeclared(war);
        return detail(war, clanId);
    }

    @Transactional
    public ClanWarResponse retreat(UUID warId, Long playerId) {
        User actor = accessService.player(playerId);
        ClanWar war = warRepository.findByIdForUpdate(warId)
                .orElseThrow(() -> new ResourceNotFoundException("ClanWar", warId));
        ClanMember membership = accessService.require(war.getAttackerClan().getId(), actor.getId(),
                ClanPermission.DECLARE_WAR);
        ClanWarSide side = sideRepository.findByWar_IdAndClan_Id(warId, war.getAttackerClan().getId())
                .orElseThrow(() -> new ResourceNotFoundException("ClanWarSide", warId));
        boolean lead = side.getLeadUser() != null && side.getLeadUser().getId().equals(actor.getId());
        if (!lead && membership.getRole() != ClanRole.founder) {
            throw new ForbiddenException("Only the commander leading this war or the founder can call a retreat");
        }
        if (war.getStatus() == ClanWarStatus.ended) {
            throw new ConflictException("This war is already over");
        }
        end(war, ClanWarOutcome.retreated, actor);
        return warResponses.of(war);
    }

    @Transactional
    public void end(UUID warId, ClanWarOutcome outcome) {
        warRepository.findByIdForUpdate(warId)
                .filter(war -> war.getStatus() != ClanWarStatus.ended)
                .ifPresent(war -> end(war, outcome, null));
    }

    @Transactional
    public void forfeitAll(UUID clanId) {
        warRepository.findOpenIdsByClanId(clanId).forEach(warId -> end(warId, ClanWarOutcome.forfeited));
    }

    @Transactional
    public void endSeason(UUID seasonId) {
        warRepository.findOpenIdsBySeasonId(seasonId).forEach(warId -> end(warId, ClanWarOutcome.season_ended));
    }

    public Page<ClanWarResponse> list(UUID clanId, boolean open, Pageable pageable) {
        Page<ClanWar> page = warRepository.findPage(clanId, open, pageable);
        return new PageImpl<>(warResponses.of(page.getContent()), pageable, page.getTotalElements());
    }

    public ClanWarDetailResponse get(UUID warId, Long viewerId) {
        ClanWar war = warRepository.findWithRefsById(warId)
                .orElseThrow(() -> new ResourceNotFoundException("ClanWar", warId));
        UUID viewerClanId = viewerId == null ? null : memberRepository.findOpenByUserId(viewerId)
                .map(member -> member.getClan().getId())
                .orElse(null);
        return detail(war, viewerClanId);
    }

    public Page<ClanWarParticipantResponse> participants(UUID warId, Pageable pageable) {
        ClanWar war = warRepository.findWithRefsById(warId)
                .orElseThrow(() -> new ResourceNotFoundException("ClanWar", warId));
        Map<UUID, PublicClanResponse> clans = cosmeticService.publicRefs(
                List.of(war.getAttackerClan(), war.getDefenderClan()));
        Page<ClanWarParticipant> page = participantRepository.findPageByWarId(warId, pageable);
        return page.map(p -> ClanWarParticipantResponse.of(p, clans.get(p.getClan().getId())));
    }

    public Page<ClanWarHitResponse> hits(UUID warId, Pageable pageable) {
        if (!warRepository.existsById(warId)) {
            throw new ResourceNotFoundException("ClanWar", warId);
        }
        Page<ClanWarHit> page = hitRepository.findPageByWarId(warId, pageable);
        Map<UUID, PublicMapDifficultyResponse> difficulties = mapService.getDifficultyResponsesPublic(
                page.getContent().stream().map(hit -> hit.getMapDifficulty().getId()).toList());
        return page.map(hit -> ClanWarHitResponse.of(hit, difficulties.get(hit.getMapDifficulty().getId())));
    }

    private void end(ClanWar war, ClanWarOutcome outcome, User actor) {
        war.setStatus(ClanWarStatus.ended);
        war.setOutcome(outcome);
        war.setEndedAt(Instant.now());
        warRepository.saveAndFlush(war);
        loanRepository.closeOpenForWar(war.getId(), war.getEndedAt());
        feed.war(war);
        notifier.warEnded(war);
        scoreGate.refreshAfterCommit();
        eventPublisher.publishEvent(new ClanWarEndedEvent(war.getId()));
        chatChannel.announce(war.getAttackerClan(),
                ChatNotice.ofWar(ChatEvent.war_ended, actor, war.getDefenderClan(), war));
        chatChannel.announce(war.getDefenderClan(),
                ChatNotice.ofWar(ChatEvent.war_ended, actor, war.getAttackerClan(), war));
    }

    private void assertCanDeclare(Clan attacker, Clan defender, DeclareClanWarRequest request) {
        if (warRepository.existsOpenAttack(attacker.getId(), null)) {
            throw new ConflictException("Your clan is already attacking somebody");
        }
        if (allianceRepository.existsActiveBetween(attacker.getId(), defender.getId())) {
            throw new ConflictException("You cannot declare war on an ally");
        }
        if (!levelService.hasWarMode(attacker, ClanWarModeAxis.arena, request.getArena().name())
                || !levelService.hasWarMode(attacker, ClanWarModeAxis.ruleset, request.getRuleset().name())) {
            throw new ValidationException("arena", "Your clan has not unlocked that arena and ruleset yet");
        }
    }

    private void assertStandings(Clan attacker, Clan defender, double attackerStanding, double defenderStanding) {
        if (attackerStanding <= 0 || defenderStanding <= 0) {
            throw new ValidationException("Both clans need some Standing to put up before a war can start");
        }
        boolean retaliation = warRepository.existsOpenAttack(defender.getId(), attacker.getId());
        if (!retaliation && defenderStanding < attackerStanding * clanProperties.getWar().getPunchDownFloor()) {
            throw new ValidationException("That clan is too far below yours to attack");
        }
    }

    private ClanWarSide side(ClanWar war, Clan clan, User lead, double stake, double standing) {
        return ClanWarSide.builder()
                .war(war)
                .clan(clan)
                .leadUser(lead)
                .stake(stake)
                .stakeRemaining(stake)
                .standingAtDeclare(standing)
                .build();
    }

    private ClanWarDetailResponse detail(ClanWar war, UUID viewerClanId) {
        return new ClanWarDetailResponse(warResponses.of(war), poolService.pool(war, viewerClanId));
    }
}
