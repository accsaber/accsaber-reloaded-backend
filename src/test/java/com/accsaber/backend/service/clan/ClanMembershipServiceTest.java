package com.accsaber.backend.service.clan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.exception.ValidationException;
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

@ExtendWith(MockitoExtension.class)
class ClanMembershipServiceTest {

    private static final UUID CLAN_ID = UUID.randomUUID();

    @Mock
    private ClanNotifier notifier;
    @Mock
    private ClanRepository clanRepository;
    @Mock
    private ClanMemberRepository memberRepository;
    @Mock
    private ClanJoinRequestRepository joinRequestRepository;
    @Mock
    private ClanAuditEntryRepository auditRepository;
    @Mock
    private ScoreRepository scoreRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ClanRoster roster;
    @Mock
    private ClanAccessService accessService;
    @Mock
    private ClanLevelService levelService;
    @Mock
    private ClanService clanService;
    @Mock
    private NotificationWebSocketHandler notificationHandler;
    @Mock
    private ClanChatChannel chatChannel;

    private final ClanProperties clanProperties = new ClanProperties();
    private ClanMembershipService service;

    private final Clan clan = Clan.builder().id(CLAN_ID).build();

    @BeforeEach
    void setUp() {
        service = new ClanMembershipService(clanRepository, memberRepository, joinRequestRepository, auditRepository,
                scoreRepository, userRepository, roster, accessService, levelService, clanService, notificationHandler,
                chatChannel, notifier, clanProperties);
        lenient().when(roster.lock(CLAN_ID)).thenReturn(clan);
        lenient().when(notificationHandler.onlineAmong(anyCollection())).thenReturn(Set.of());
    }

    private User user(long id) {
        User user = User.builder().id(id).name("P" + id).build();
        lenient().when(accessService.player(id)).thenReturn(user);
        return user;
    }

    private ClanMember member(User user, ClanRole role, Instant joinedAt) {
        ClanMember member = ClanMember.builder().id(UUID.randomUUID()).clan(clan).user(user).role(role)
                .joinedAt(joinedAt).build();
        lenient().when(memberRepository.findOpenByClanIdAndUserId(CLAN_ID, user.getId()))
                .thenReturn(Optional.of(member));
        return member;
    }

    private void grants(User user, ClanMember membership) {
        lenient().when(accessService.require(eq(CLAN_ID), eq(user.getId()), any())).thenReturn(membership);
    }

    @Nested
    class ChangeRole {

        @Test
        void foundingRankCannotBeHandedOutThroughARankChange() {
            User founder = user(1L);
            member(user(2L), ClanRole.member, Instant.now());

            assertThatThrownBy(() -> service.changeRole(CLAN_ID, founder.getId(), 2L, ClanRole.founder))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void promotingToCommanderAsksForTheFounderPermission() {
            User commander = user(1L);
            member(user(2L), ClanRole.officer, Instant.now());
            when(accessService.require(CLAN_ID, 1L, ClanPermission.PROMOTE_COMMANDER))
                    .thenThrow(new ForbiddenException());

            assertThatThrownBy(() -> service.changeRole(CLAN_ID, commander.getId(), 2L, ClanRole.commander))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void aCommanderCannotTouchAnotherCommander() {
            User actor = user(1L);
            ClanMember actorMember = member(actor, ClanRole.commander, Instant.now());
            member(user(2L), ClanRole.commander, Instant.now());
            grants(actor, actorMember);

            assertThatThrownBy(() -> service.changeRole(CLAN_ID, 1L, 2L, ClanRole.member))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void noFreeOfficerSlotIsAConflict() {
            User actor = user(1L);
            grants(actor, member(actor, ClanRole.commander, Instant.now()));
            member(user(2L), ClanRole.member, Instant.now());
            when(memberRepository.countByClan_IdAndRoleAndLeftAtIsNull(CLAN_ID, ClanRole.officer)).thenReturn(2L);
            when(levelService.capacityOf(clan, ClanCapacity.officer_slots)).thenReturn(2);

            assertThatThrownBy(() -> service.changeRole(CLAN_ID, 1L, 2L, ClanRole.officer))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void aPromotionIsSavedAndAudited() {
            User actor = user(1L);
            grants(actor, member(actor, ClanRole.commander, Instant.now()));
            ClanMember target = member(user(2L), ClanRole.member, Instant.now());
            when(levelService.capacityOf(clan, ClanCapacity.officer_slots)).thenReturn(1);

            var response = service.changeRole(CLAN_ID, 1L, 2L, ClanRole.officer);

            assertThat(target.getRole()).isEqualTo(ClanRole.officer);
            assertThat(response.role()).isEqualTo(ClanRole.officer);
            ArgumentCaptor<ClanAuditEntry> audit = ArgumentCaptor.forClass(ClanAuditEntry.class);
            verify(auditRepository).save(audit.capture());
            assertThat(audit.getValue().getAction()).isEqualTo(ClanAuditAction.role_changed);
            assertThat(audit.getValue().getDetails()).containsEntry("from", ClanRole.member)
                    .containsEntry("to", ClanRole.officer);
        }
    }

    @Nested
    class Remove {

        @Test
        void aMemberLeaving() {
            User player = user(2L);
            ClanMember stint = member(player, ClanRole.member, Instant.now());

            service.remove(CLAN_ID, 2L, 2L);

            verify(roster).close(stint, ClanLeaveReason.left);
            verify(chatChannel).announce(clan, ChatNotice.ofPlayer(ChatEvent.member_left, player, null));
        }

        @Test
        void aFounderWithMembersLeftHasToHandOverFirst() {
            User founder = user(1L);
            member(founder, ClanRole.founder, Instant.now());
            when(memberRepository.countByClan_IdAndLeftAtIsNull(CLAN_ID)).thenReturn(3L);

            assertThatThrownBy(() -> service.remove(CLAN_ID, 1L, 1L)).isInstanceOf(ValidationException.class);
        }

        @Test
        void aFounderLeavingAloneDisbandsTheClan() {
            User founder = user(1L);
            member(founder, ClanRole.founder, Instant.now());
            when(memberRepository.countByClan_IdAndLeftAtIsNull(CLAN_ID)).thenReturn(1L);

            service.remove(CLAN_ID, 1L, 1L);

            verify(clanService).disband(clan, founder, null);
        }

        @Test
        void anOfficerKicksAMemberAndItIsAudited() {
            User officer = user(1L);
            grants(officer, member(officer, ClanRole.officer, Instant.now()));
            ClanMember target = member(user(2L), ClanRole.member, Instant.now());

            service.remove(CLAN_ID, 1L, 2L);

            verify(accessService).require(CLAN_ID, 1L, ClanPermission.KICK);
            verify(roster).close(target, ClanLeaveReason.kicked);
            verify(auditRepository).save(any(ClanAuditEntry.class));
            verify(chatChannel).announce(clan, ChatNotice.ofPlayer(ChatEvent.member_kicked, officer, target.getUser()));
            verify(notifier).kicked(clan, target.getUser(), officer);
        }
    }

    @Nested
    class Founder {

        @Test
        void aTransferSwapsTheFounderAndHeirRanks() {
            User founder = user(1L);
            ClanMember founderStint = member(founder, ClanRole.founder, Instant.now());
            grants(founder, founderStint);
            ClanMember heir = member(user(2L), ClanRole.commander, Instant.now());

            service.transferFounder(CLAN_ID, 1L, 2L);

            assertThat(founderStint.getRole()).isEqualTo(ClanRole.commander);
            assertThat(heir.getRole()).isEqualTo(ClanRole.founder);
            verify(notifier).crowned(clan, heir.getUser(), founder);
        }

        private void roster(ClanMember... members) {
            when(memberRepository.findRoster(eq(CLAN_ID), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(members)));
        }

        @Test
        void onlyTheLongestServingCommanderCanClaim() {
            ClanMember founder = member(user(1L), ClanRole.founder, Instant.now().minus(300, ChronoUnit.DAYS));
            ClanMember veteran = member(user(2L), ClanRole.commander, Instant.now().minus(200, ChronoUnit.DAYS));
            ClanMember newcomer = member(user(3L), ClanRole.commander, Instant.now().minus(10, ChronoUnit.DAYS));
            roster(founder, veteran, newcomer);

            assertThatThrownBy(() -> service.transferFounder(CLAN_ID, 3L, 3L))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void aFounderWhoPlayedRecentlyCannotBeReplaced() {
            ClanMember founder = member(user(1L), ClanRole.founder, Instant.now().minus(300, ChronoUnit.DAYS));
            ClanMember veteran = member(user(2L), ClanRole.commander, Instant.now().minus(200, ChronoUnit.DAYS));
            roster(founder, veteran);
            lenient().when(scoreRepository.findLastActiveScoreTimes(List.of(1L)))
                    .thenReturn(List.of(lastPlayed(1L, Instant.now().minus(2, ChronoUnit.DAYS))));

            assertThatThrownBy(() -> service.transferFounder(CLAN_ID, 2L, 2L))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void theLongestServingCommanderClaimsAQuietClan() {
            ClanMember founder = member(user(1L), ClanRole.founder, Instant.now().minus(300, ChronoUnit.DAYS));
            ClanMember veteran = member(user(2L), ClanRole.commander, Instant.now().minus(200, ChronoUnit.DAYS));
            roster(founder, veteran);
            lenient().when(scoreRepository.findLastActiveScoreTimes(List.of(1L)))
                    .thenReturn(List.of(lastPlayed(1L, Instant.now().minus(90, ChronoUnit.DAYS))));

            service.transferFounder(CLAN_ID, 2L, 2L);

            assertThat(veteran.getRole()).isEqualTo(ClanRole.founder);
            assertThat(founder.getRole()).isEqualTo(ClanRole.commander);
        }

        private ScoreRepository.LastPlayedView lastPlayed(Long userId, Instant at) {
            return new ScoreRepository.LastPlayedView() {
                public Long getUserId() {
                    return userId;
                }

                public Instant getLastPlayedAt() {
                    return at;
                }
            };
        }
    }

    @Nested
    class Hooks {

        @Test
        void aBannedFounderIsReplacedByTheNextRankedMember() {
            ClanMember founder = member(user(1L), ClanRole.founder, Instant.now());
            ClanMember commander = member(user(2L), ClanRole.commander, Instant.now());
            when(memberRepository.findOpenByUserId(1L)).thenReturn(Optional.of(founder));
            when(memberRepository.findRoster(eq(CLAN_ID), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(commander)));

            service.onPlayerBanned(new PlayerBannedEvent(1L));

            verify(roster).close(founder, ClanLeaveReason.banned);
            verify(chatChannel).announce(clan, ChatNotice.ofPlayer(ChatEvent.member_left, founder.getUser(), null));
            assertThat(commander.getRole()).isEqualTo(ClanRole.founder);
            verify(notifier).crowned(clan, commander.getUser(), null);
            verify(joinRequestRepository).expirePendingForUser(eq(1L), any());
        }

        @Test
        void aBannedFounderAloneTakesTheClanDown() {
            ClanMember founder = member(user(1L), ClanRole.founder, Instant.now());
            when(memberRepository.findOpenByUserId(1L)).thenReturn(Optional.of(founder));
            when(memberRepository.findRoster(eq(CLAN_ID), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

            service.onPlayerBanned(new PlayerBannedEvent(1L));

            verify(clanService).disband(clan, null, null);
        }

        @Test
        void aMergeMovesTheStintOntoAClanlessPrimary() {
            ClanMember secondary = member(user(9L), ClanRole.officer, Instant.now());
            User primary = User.builder().id(4L).build();
            lenient().when(memberRepository.findOpenByUserId(9L)).thenReturn(Optional.of(secondary));
            when(userRepository.getReferenceById(4L)).thenReturn(primary);

            service.onPlayersMerged(new PlayersMergedEvent(4L, 9L));

            verify(roster).close(secondary, ClanLeaveReason.merged);
            verify(roster).seat(clan, primary, ClanRole.officer);
        }

        @Test
        void aMergeIntoAPrimaryAlreadyInAClanOnlyEndsTheStint() {
            ClanMember secondary = member(user(9L), ClanRole.member, Instant.now());
            lenient().when(memberRepository.findOpenByUserId(9L)).thenReturn(Optional.of(secondary));
            when(memberRepository.findOpenByUserId(4L)).thenReturn(Optional.of(ClanMember.builder().build()));

            service.onPlayersMerged(new PlayersMergedEvent(4L, 9L));

            verify(roster).close(secondary, ClanLeaveReason.merged);
            verify(roster, never()).seat(any(), any(), any());
        }
    }
}
