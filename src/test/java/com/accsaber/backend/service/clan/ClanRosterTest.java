package com.accsaber.backend.service.clan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.exception.ConflictException;
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

@ExtendWith(MockitoExtension.class)
class ClanRosterTest {

    private static final Long USER = 7L;

    @Mock
    private ClanRepository clanRepository;
    @Mock
    private ClanMemberRepository memberRepository;
    @Mock
    private ClanJoinRequestRepository joinRequestRepository;
    @Mock
    private UserMissionRepository userMissionRepository;
    @Mock
    private ClanLevelService levelService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final ClanProperties clanProperties = new ClanProperties();
    private ClanRoster roster;

    @BeforeEach
    void setUp() {
        roster = new ClanRoster(clanRepository, memberRepository, joinRequestRepository, userMissionRepository,
                levelService, clanProperties, eventPublisher);
    }

    private void latestStint(Instant joinedAt, Instant leftAt, ClanLeaveReason reason) {
        when(memberRepository.findFirstByUser_IdOrderByJoinedAtDesc(USER)).thenReturn(Optional.of(
                ClanMember.builder().joinedAt(joinedAt).leftAt(leftAt).leaveReason(reason).build()));
    }

    @Nested
    class AssertCanJoin {

        @Test
        void aPlayerWithNoHistoryCanJoin() {
            assertThatCode(() -> roster.assertCanJoin(USER)).doesNotThrowAnyException();
        }

        @Test
        void aPlayerStillInAClanCannotJoinAnother() {
            latestStint(Instant.now().minus(Duration.ofDays(40)), null, null);

            assertThatThrownBy(() -> roster.assertCanJoin(USER)).isInstanceOf(ConflictException.class);
        }

        @Test
        void leavingInsideTheCooldownKeepsThePlayerOut() {
            latestStint(Instant.now().minus(Duration.ofDays(3)), Instant.now(), ClanLeaveReason.left);

            assertThatThrownBy(() -> roster.assertCanJoin(USER)).isInstanceOf(ValidationException.class);
        }

        @Test
        void theCooldownRunsFromTheJoinNotTheLeave() {
            latestStint(Instant.now().minus(Duration.ofDays(15)), Instant.now(), ClanLeaveReason.left);

            assertThatCode(() -> roster.assertCanJoin(USER)).doesNotThrowAnyException();
        }

        @Test
        void aKickClearsTheCooldown() {
            latestStint(Instant.now().minus(Duration.ofDays(1)), Instant.now(), ClanLeaveReason.kicked);

            assertThatCode(() -> roster.assertCanJoin(USER)).doesNotThrowAnyException();
        }

        @Test
        void aDisbandClearsTheCooldown() {
            latestStint(Instant.now().minus(Duration.ofDays(1)), Instant.now(), ClanLeaveReason.disbanded);

            assertThatCode(() -> roster.assertCanJoin(USER)).doesNotThrowAnyException();
        }
    }

    @Nested
    class Admit {

        private final Clan clan = Clan.builder().id(UUID.randomUUID()).build();
        private final User user = User.builder().id(USER).build();

        @Test
        void aFullClanRejectsTheJoin() {
            when(memberRepository.countByClan_IdAndLeftAtIsNull(clan.getId())).thenReturn(10L);
            when(levelService.capacityOf(clan, ClanCapacity.member_slots)).thenReturn(10);

            assertThatThrownBy(() -> roster.admit(clan, user, ClanRole.member)).isInstanceOf(ConflictException.class);
            verify(memberRepository, never()).saveAndFlush(any());
        }

        @Test
        void aFreeSlotSeatsThePlayerAndExpiresTheirOtherRequests() {
            when(memberRepository.countByClan_IdAndLeftAtIsNull(clan.getId())).thenReturn(4L);
            when(levelService.capacityOf(clan, ClanCapacity.member_slots)).thenReturn(10);
            when(memberRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

            ClanMember member = roster.admit(clan, user, ClanRole.member);

            assertThat(member.getClan()).isSameAs(clan);
            assertThat(member.getRole()).isEqualTo(ClanRole.member);
            verify(joinRequestRepository).expirePendingForUser(eq(USER), any());
            verify(eventPublisher).publishEvent(new ClanMembershipChangedEvent(clan.getId()));
        }
    }

    @Test
    void closingAStintStampsTheTimeAndReasonAndVoidsTheirClanMissionRows() {
        ClanMember member = ClanMember.builder().clan(Clan.builder().id(UUID.randomUUID()).build())
                .user(User.builder().id(USER).build()).build();

        roster.close(member, ClanLeaveReason.kicked);

        assertThat(member.getLeftAt()).isNotNull();
        assertThat(member.getLeaveReason()).isEqualTo(ClanLeaveReason.kicked);
        verify(memberRepository).saveAndFlush(member);
        verify(userMissionRepository).voidActiveClanRowsForUser(USER);
    }
}
