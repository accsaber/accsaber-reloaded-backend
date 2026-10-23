package com.accsaber.backend.service.clan.war;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.clan.ChooseClanWarTargetRequest;
import com.accsaber.backend.model.dto.request.clan.OrderClanWarTargetRequest;
import com.accsaber.backend.model.dto.response.clan.ClanWarParticipantResponse;
import com.accsaber.backend.model.entity.clan.war.ClanRuleset;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarOrder;
import com.accsaber.backend.model.entity.clan.war.ClanWarParticipant;
import com.accsaber.backend.model.entity.clan.war.ClanWarStatus;
import com.accsaber.backend.model.entity.clan.war.ClanWarTargeting;
import com.accsaber.backend.model.entity.clan.war.ClanWarTeam;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.war.ClanWarParticipantRepository;
import com.accsaber.backend.repository.clan.war.ClanWarRepository;
import com.accsaber.backend.repository.clan.war.ClanWarSideRepository;
import com.accsaber.backend.service.clan.ClanAccessService;
import com.accsaber.backend.service.clan.ClanCosmeticService;
import com.accsaber.backend.service.clan.ClanFeed;
import com.accsaber.backend.service.clan.ClanMapper;
import com.accsaber.backend.service.clan.ClanPermission;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClanWarTargetingService {

    private final ClanWarRepository warRepository;
    private final ClanWarSideRepository sideRepository;
    private final ClanWarParticipantRepository participantRepository;
    private final ClanAccessService accessService;
    private final ClanCosmeticService cosmeticService;
    private final ClanFeed feed;

    private record Roster(ClanWar war, Map<Long, ClanWarParticipant> fighters, Map<UUID, ClanWarTeam> teams) {

        User enemyOf(ClanWarParticipant player, String enemyId) {
            if (war.getRuleset() != ClanRuleset.duel) {
                throw new ConflictException("Only duels have targets");
            }
            ClanWarParticipant enemy = fighters.get(Long.valueOf(enemyId));
            if (enemy == null || teams.get(enemy.getClan().getId()) == teams.get(player.getClan().getId())) {
                throw new ValidationException("userId", "must be an enemy still fighting in this war");
            }
            return enemy.getUser();
        }
    }

    @Transactional
    public ClanWarParticipantResponse choose(UUID warId, Long playerId, ChooseClanWarTargetRequest request) {
        Roster roster = roster(warId);
        ClanWarParticipant chooser = roster.fighters().get(playerId);
        if (chooser == null) {
            throw new ForbiddenException("You are not fighting in this war");
        }
        chooser.setTargeting(request.targeting());
        if (request.targeting() == ClanWarTargeting.manual) {
            chooser.setTarget(request.userId() == null ? null : roster.enemyOf(chooser, request.userId()));
        }
        return save(chooser);
    }

    @Transactional
    public ClanWarParticipantResponse order(UUID warId, Long commanderId, OrderClanWarTargetRequest request) {
        Roster roster = roster(warId);
        ClanWarParticipant player = roster.fighters().get(Long.valueOf(request.userId()));
        if (player == null) {
            throw new ValidationException("userId", "must be a player still fighting in this war");
        }
        accessService.require(player.getClan().getId(), commanderId, ClanPermission.ORDER_TARGETS);
        if (request.order() == ClanWarOrder.target && request.targetUserId() == null) {
            throw new ValidationException("targetUserId", "a target order needs a target");
        }
        player.setOrder(request.order());
        player.setOrderedTarget(request.order() == ClanWarOrder.target ? roster.enemyOf(player, request.targetUserId())
                : null);
        ClanWarParticipantResponse response = save(player);
        feed.war(roster.war());
        return response;
    }

    private Roster roster(UUID warId) {
        ClanWar war = warRepository.lockOrThrow(warId);
        if (war.getStatus() == ClanWarStatus.ended) {
            throw new ConflictException("This war is already over");
        }
        Map<Long, ClanWarParticipant> fighters = participantRepository.findActiveByWarId(warId).stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), Function.identity()));
        return new Roster(war, fighters, ClanWarRosterService.teams(sideRepository.findByWarId(warId)));
    }

    private ClanWarParticipantResponse save(ClanWarParticipant participant) {
        participantRepository.saveAndFlush(participant);
        double skill = participantRepository.findOverallSkills(List.of(participant.getUser().getId())).stream()
                .findFirst().map(ClanMemberRepository.MemberSkillView::getSkill).orElse(0.0);
        return ClanMapper.toWarParticipantResponse(participant, cosmeticService.publicRefs(List.of(participant.getClan()))
                .get(participant.getClan().getId()), new ClanMapper.WarParticipantStats(skill, null, true));
    }
}
