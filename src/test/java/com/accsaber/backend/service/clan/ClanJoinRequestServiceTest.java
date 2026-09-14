package com.accsaber.backend.service.clan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.entity.chat.ChatEvent;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanJoinDirection;
import com.accsaber.backend.model.entity.clan.ClanJoinRequest;
import com.accsaber.backend.model.entity.clan.ClanJoinStatus;
import com.accsaber.backend.model.entity.clan.ClanMember;
import com.accsaber.backend.model.entity.clan.ClanRole;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.clan.ClanJoinRequestRepository;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.ClanRepository;

@ExtendWith(MockitoExtension.class)
class ClanJoinRequestServiceTest {

    private static final UUID CLAN_ID = UUID.randomUUID();

    @Mock
    private ClanJoinRequestRepository joinRequestRepository;
    @Mock
    private ClanMemberRepository memberRepository;
    @Mock
    private ClanRepository clanRepository;
    @Mock
    private ClanRoster roster;
    @Mock
    private ClanAccessService accessService;
    @Mock
    private ClanCosmeticService cosmeticService;
    @Mock
    private ClanChatChannel chatChannel;

    @InjectMocks
    private ClanJoinRequestService service;

    private final Clan clan = Clan.builder().id(CLAN_ID).name("Night Owls").tag("NOW").slug("night-owls").build();
    private final User officer = User.builder().id(1L).name("Officer").build();
    private final User player = User.builder().id(2L).name("Player").build();

    @BeforeEach
    void setUp() {
        lenient().when(accessService.player(1L)).thenReturn(officer);
        lenient().when(accessService.player(2L)).thenReturn(player);
        lenient().when(clanRepository.findByIdAndActiveTrue(CLAN_ID)).thenReturn(Optional.of(clan));
        lenient().when(joinRequestRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    class Create {

        @Test
        void aPlayerAsksToJoinAClanTakingRequests() {
            var response = service.create(CLAN_ID, 2L, null);

            verify(roster).assertCanJoin(2L);
            assertThat(response.direction()).isEqualTo(ClanJoinDirection.request);
            assertThat(response.player().id()).isEqualTo("2");
        }

        @Test
        void aClanNotTakingRequestsTurnsThePlayerAway() {
            clan.setAcceptingRequests(false);

            assertThatThrownBy(() -> service.create(CLAN_ID, 2L, null)).isInstanceOf(ValidationException.class);
        }

        @Test
        void invitingNeedsOfficerRank() {
            when(accessService.require(CLAN_ID, 1L, ClanPermission.INVITE)).thenThrow(new ForbiddenException());

            assertThatThrownBy(() -> service.create(CLAN_ID, 1L, 2L)).isInstanceOf(ForbiddenException.class);
        }

        @Test
        void anOfficerInvitesAPlayer() {
            var response = service.create(CLAN_ID, 1L, 2L);

            assertThat(response.direction()).isEqualTo(ClanJoinDirection.invite);
            assertThat(response.createdBy().id()).isEqualTo("1");
            verify(roster, never()).assertCanJoin(any());
        }

        @Test
        void invitingSomeoneAlreadyInTheClanIsAConflict() {
            when(memberRepository.findOpenByClanIdAndUserId(CLAN_ID, 2L))
                    .thenReturn(Optional.of(ClanMember.builder().build()));

            assertThatThrownBy(() -> service.create(CLAN_ID, 1L, 2L)).isInstanceOf(ConflictException.class);
        }

        @Test
        void aSecondPendingRequestIsAConflict() {
            when(joinRequestRepository.existsByClan_IdAndUser_IdAndStatus(CLAN_ID, 2L, ClanJoinStatus.pending))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.create(CLAN_ID, 2L, null)).isInstanceOf(ConflictException.class);
        }
    }

    @Nested
    class Resolve {

        private ClanJoinRequest pending(ClanJoinDirection direction) {
            ClanJoinRequest request = ClanJoinRequest.builder().id(UUID.randomUUID()).clan(clan).user(player)
                    .direction(direction).createdBy(direction == ClanJoinDirection.invite ? officer : player).build();
            when(joinRequestRepository.findWithRefsById(request.getId())).thenReturn(Optional.of(request));
            return request;
        }

        @Test
        void anOfficerAcceptingARequestAdmitsThePlayer() {
            ClanJoinRequest request = pending(ClanJoinDirection.request);
            when(roster.lock(CLAN_ID)).thenReturn(clan);

            var response = service.resolve(request.getId(), 1L, ClanJoinStatus.accepted);

            verify(accessService).require(CLAN_ID, 1L, ClanPermission.RESOLVE_REQUESTS);
            verify(roster).admit(clan, player, ClanRole.member);
            verify(chatChannel).announce(clan, ChatNotice.ofPlayer(ChatEvent.member_joined, player, null));
            assertThat(response.status()).isEqualTo(ClanJoinStatus.accepted);
            assertThat(response.resolvedBy().id()).isEqualTo("1");
        }

        @Test
        void onlyTheInvitedPlayerAnswersAnInvite() {
            ClanJoinRequest request = pending(ClanJoinDirection.invite);

            assertThatThrownBy(() -> service.resolve(request.getId(), 1L, ClanJoinStatus.accepted))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void theInvitedPlayerAcceptingJoins() {
            ClanJoinRequest request = pending(ClanJoinDirection.invite);
            when(roster.lock(CLAN_ID)).thenReturn(clan);

            service.resolve(request.getId(), 2L, ClanJoinStatus.accepted);

            verify(roster).admit(clan, player, ClanRole.member);
        }

        @Test
        void thePlayerWhoAskedCanCancelTheirRequest() {
            ClanJoinRequest request = pending(ClanJoinDirection.request);

            service.resolve(request.getId(), 2L, ClanJoinStatus.cancelled);

            assertThat(request.getStatus()).isEqualTo(ClanJoinStatus.cancelled);
            verify(roster, never()).admit(any(), any(), any());
        }

        @Test
        void somebodyElseCannotCancelARequest() {
            ClanJoinRequest request = pending(ClanJoinDirection.request);

            assertThatThrownBy(() -> service.resolve(request.getId(), 1L, ClanJoinStatus.cancelled))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void anAnsweredRequestCannotBeAnsweredAgain() {
            ClanJoinRequest request = pending(ClanJoinDirection.request);
            request.setStatus(ClanJoinStatus.declined);

            assertThatThrownBy(() -> service.resolve(request.getId(), 1L, ClanJoinStatus.accepted))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void expiryIsNotSomethingAPlayerCanSend() {
            ClanJoinRequest request = pending(ClanJoinDirection.request);

            assertThatThrownBy(() -> service.resolve(request.getId(), 2L, ClanJoinStatus.expired))
                    .isInstanceOf(ValidationException.class);
        }
    }
}
