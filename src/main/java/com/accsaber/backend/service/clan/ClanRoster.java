package com.accsaber.backend.service.clan;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanCapacity;
import com.accsaber.backend.model.entity.clan.ClanLeaveReason;
import com.accsaber.backend.model.entity.clan.ClanMember;
import com.accsaber.backend.model.entity.clan.ClanRole;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.event.ClanMembershipChangedEvent;
import com.accsaber.backend.repository.clan.ClanJoinRequestRepository;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.ClanRepository;
import com.accsaber.backend.repository.mission.UserMissionRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ClanRoster {

    private final ClanRepository clanRepository;
    private final ClanMemberRepository memberRepository;
    private final ClanJoinRequestRepository joinRequestRepository;
    private final UserMissionRepository userMissionRepository;
    private final ClanLevelService levelService;
    private final ClanProperties clanProperties;
    private final ApplicationEventPublisher eventPublisher;

    public Clan lock(UUID clanId) {
        return clanRepository.findByIdAndActiveTrueForUpdate(clanId)
                .orElseThrow(() -> new ResourceNotFoundException("Clan", clanId));
    }

    public void assertCanJoin(Long userId) {
        memberRepository.findFirstByUser_IdOrderByJoinedAtDesc(userId).ifPresent(latest -> {
            if (latest.getLeftAt() == null) {
                throw new ConflictException("Leave your current clan before joining another");
            }
            if (latest.getLeaveReason() == ClanLeaveReason.kicked
                    || latest.getLeaveReason() == ClanLeaveReason.disbanded) {
                return;
            }
            Instant free = latest.getJoinedAt().plus(clanProperties.getJoinCooldown());
            if (free.isAfter(Instant.now())) {
                throw new ValidationException("You can join another clan from " + free);
            }
        });
    }

    public ClanMember admit(Clan lockedClan, User user, ClanRole role) {
        assertCanJoin(user.getId());
        long members = memberRepository.countByClan_IdAndLeftAtIsNull(lockedClan.getId());
        if (members >= levelService.capacityOf(lockedClan, ClanCapacity.member_slots)) {
            throw new ConflictException("This clan has no free member slots");
        }
        return seat(lockedClan, user, role);
    }

    public ClanMember seat(Clan clan, User user, ClanRole role) {
        ClanMember member = memberRepository.saveAndFlush(
                ClanMember.builder().clan(clan).user(user).role(role).build());
        joinRequestRepository.expirePendingForUser(user.getId(), Instant.now());
        eventPublisher.publishEvent(new ClanMembershipChangedEvent(clan.getId()));
        return member;
    }

    public void close(ClanMember member, ClanLeaveReason reason) {
        member.setLeftAt(Instant.now());
        member.setLeaveReason(reason);
        memberRepository.saveAndFlush(member);
        userMissionRepository.voidActiveClanRowsForUser(member.getUser().getId());
        eventPublisher.publishEvent(new ClanMembershipChangedEvent(member.getClan().getId()));
    }

    public void closeAll(UUID clanId, ClanLeaveReason reason) {
        Instant now = Instant.now();
        memberRepository.closeOpenByClanId(clanId, reason, now);
        joinRequestRepository.expirePendingForClan(clanId, now);
        eventPublisher.publishEvent(new ClanMembershipChangedEvent(clanId));
    }
}
