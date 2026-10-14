package com.accsaber.backend.service.clan.war;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.entity.chat.ChatEvent;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanMember;
import com.accsaber.backend.model.entity.clan.ClanRole;
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

@ExtendWith(MockitoExtension.class)
class ClanWarCoalitionServiceTest {

    @Mock
    private ClanWarAidRepository aidRepository;
    @Mock
    private ClanWarRepository warRepository;
    @Mock
    private ClanWarSideRepository sideRepository;
    @Mock
    private ClanAllianceRepository allianceRepository;
    @Mock
    private ClanMemberRepository memberRepository;
    @Mock
    private ClanRoster roster;
    @Mock
    private ClanAccessService accessService;
    @Mock
    private ClanStandingService standingService;
    @Mock
    private ClanCosmeticService cosmeticService;
    @Mock
    private ClanWarStakeService stakeService;
    @Mock
    private ClanWarRosterService rosterService;
    @Mock
    private ClanWarService warService;
    @Mock
    private ClanWarResponses warResponses;
    @Mock
    private ClanChatChannel chatChannel;
    @Mock
    private ClanFeed feed;
    @Mock
    private ClanNotifier notifier;

    private ClanWarCoalitionService service;

    private final Clan attacker = Clan.builder().id(UUID.randomUUID()).name("Stars").build();
    private final Clan defender = Clan.builder().id(UUID.randomUUID()).name("GenX").build();
    private final Clan ally = Clan.builder().id(UUID.randomUUID()).name("Fish").build();
    private final User commander = User.builder().id(1L).build();
    private ClanWar war;
    private final List<ClanWarSide> sides = new ArrayList<>();

    @BeforeEach
    void setUp() {
        lenient().when(warRepository.lockOrThrow(any())).thenCallRealMethod();
        service = new ClanWarCoalitionService(aidRepository, warRepository, sideRepository, allianceRepository,
                memberRepository, roster, accessService, standingService, cosmeticService, stakeService,
                rosterService, warService, warResponses, chatChannel, feed, notifier);
        war = ClanWar.builder().id(UUID.randomUUID()).attackerClan(attacker).defenderClan(defender)
                .ruleset(ClanRuleset.duel).status(ClanWarStatus.active).build();
        sides.add(ClanWarSide.builder().war(war).clan(attacker).team(ClanWarTeam.attack).stake(5000.0)
                .stakeRemaining(5000.0).build());
        sides.add(ClanWarSide.builder().war(war).clan(defender).team(ClanWarTeam.defend).stake(5000.0)
                .stakeRemaining(5000.0).build());
        lenient().when(accessService.player(1L)).thenReturn(commander);
        lenient().when(roster.lock(ally.getId())).thenReturn(ally);
        lenient().when(warRepository.findByIdForUpdate(war.getId())).thenReturn(Optional.of(war));
        lenient().when(sideRepository.findByWarId(war.getId())).thenAnswer(inv -> List.copyOf(sides));
        lenient().when(allianceRepository.existsActiveBetween(defender.getId(), ally.getId())).thenReturn(true);
        lenient().when(standingService.currentStanding(attacker)).thenReturn(7000.0);
        lenient().when(standingService.currentStanding(defender)).thenReturn(7000.0);
        lenient().when(standingService.currentStanding(ally)).thenReturn(5000.0);
        lenient().when(aidRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(cosmeticService.publicRefs(anyCollection())).thenAnswer(inv -> inv.<Collection<Clan>>getArgument(0)
                .stream().collect(Collectors.toMap(Clan::getId, clan -> PublicClanResponse.of(clan, List.of()),
                        (first, second) -> first)));
    }

    private void commandsDefender() {
        when(memberRepository.findOpenByUserId(1L))
                .thenReturn(Optional.of(ClanMember.builder().clan(defender).user(commander).role(ClanRole.commander)
                        .build()));
    }

    private void commandsAlly() {
        lenient().when(memberRepository.findOpenByUserId(1L))
                .thenReturn(Optional.of(ClanMember.builder().clan(ally).user(commander).role(ClanRole.commander)
                        .build()));
    }

    private ClanWarAid aid(ClanWarAidDirection direction, ClanWarTeam team) {
        ClanWarAid aid = ClanWarAid.builder().id(UUID.randomUUID()).war(war).clan(ally).team(team)
                .direction(direction).requestedBy(commander).build();
        when(aidRepository.findWithRefsById(aid.getId())).thenReturn(Optional.of(aid));
        return aid;
    }

    @Nested
    class Requests {

        @Test
        void aLeadCommanderAsksAnAllyToJoinTheirTeam() {
            commandsDefender();

            var response = service.request(war.getId(), 1L, ally.getId());

            verify(accessService).require(defender.getId(), 1L, ClanPermission.COALITION);
            ArgumentCaptor<ClanWarAid> saved = ArgumentCaptor.forClass(ClanWarAid.class);
            verify(aidRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getTeam()).isEqualTo(ClanWarTeam.defend);
            assertThat(saved.getValue().getDirection()).isEqualTo(ClanWarAidDirection.request);
            assertThat(saved.getValue().getStatus()).isEqualTo(ClanWarAidStatus.pending);
            assertThat(response.lead().id()).isEqualTo(defender.getId());
            verify(notifier).aidRequested(saved.getValue());
        }

        @Test
        void onlyTheClansThatStartedTheWarCanAsk() {
            commandsAlly();

            assertThatThrownBy(() -> service.request(war.getId(), 1L, ally.getId()))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void theHelperHasToBeAnAllyOfThatLead() {
            commandsDefender();
            when(allianceRepository.existsActiveBetween(defender.getId(), ally.getId())).thenReturn(false);

            assertThatThrownBy(() -> service.request(war.getId(), 1L, ally.getId()))
                    .isInstanceOf(ConflictException.class).hasMessageContaining("allies");
        }

        @Test
        void aClanAlliedWithTheOtherSideCannotJoin() {
            commandsDefender();
            when(allianceRepository.existsActiveBetween(attacker.getId(), ally.getId())).thenReturn(true);

            assertThatThrownBy(() -> service.request(war.getId(), 1L, ally.getId()))
                    .isInstanceOf(ConflictException.class).hasMessageContaining("other side");
        }

        @Test
        void aClanAlreadyHelpingInAnotherWarCannotJoin() {
            commandsDefender();
            when(sideRepository.existsOpenAllySide(ally.getId())).thenReturn(true);

            assertThatThrownBy(() -> service.request(war.getId(), 1L, ally.getId()))
                    .isInstanceOf(ConflictException.class).hasMessageContaining("another war");
        }

        @Test
        void berserkerWarsTakeNoAllies() {
            commandsDefender();
            war.setRuleset(ClanRuleset.berserker);

            assertThatThrownBy(() -> service.request(war.getId(), 1L, ally.getId()))
                    .isInstanceOf(ConflictException.class).hasMessageContaining("duels");
        }

        @Test
        void aPacifistClanCannotJoin() {
            commandsDefender();
            ally.setPacifistFrom(Instant.now().minusSeconds(60));

            assertThatThrownBy(() -> service.request(war.getId(), 1L, ally.getId()))
                    .isInstanceOf(ConflictException.class).hasMessageContaining("pacifist");
        }
    }

    @Nested
    class Offers {

        @Test
        void anAllyCommanderOffersToFightForOneTeam() {
            commandsAlly();

            service.offer(war.getId(), 1L, ClanWarTeam.defend);

            verify(accessService).require(ally.getId(), 1L, ClanPermission.COALITION);
            ArgumentCaptor<ClanWarAid> saved = ArgumentCaptor.forClass(ClanWarAid.class);
            verify(aidRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getDirection()).isEqualTo(ClanWarAidDirection.offer);
            verify(notifier).aidOffered(saved.getValue());
        }

        @Test
        void joiningTheAttackNeedsLessStandingThanTheDefender() {
            commandsAlly();
            when(allianceRepository.existsActiveBetween(attacker.getId(), ally.getId())).thenReturn(true);
            when(allianceRepository.existsActiveBetween(defender.getId(), ally.getId())).thenReturn(false);
            when(standingService.currentStanding(ally)).thenReturn(7000.0);

            assertThatThrownBy(() -> service.offer(war.getId(), 1L, ClanWarTeam.attack))
                    .isInstanceOf(ConflictException.class).hasMessageContaining("below the defender");
        }

        @Test
        void aFallenLeadCannotTakeMoreHelp() {
            commandsAlly();
            sides.get(1).setStakeRemaining(0.0);

            assertThatThrownBy(() -> service.offer(war.getId(), 1L, ClanWarTeam.defend))
                    .isInstanceOf(ConflictException.class).hasMessageContaining("has fallen");
            verify(aidRepository, never()).saveAndFlush(any());
        }

        @Test
        void aClanCanOnlyHaveOneOpenRequestPerWar() {
            commandsAlly();
            when(aidRepository.existsPending(war.getId(), ally.getId())).thenReturn(true);

            assertThatThrownBy(() -> service.offer(war.getId(), 1L, ClanWarTeam.defend))
                    .isInstanceOf(ConflictException.class);
        }
    }

    @Nested
    class Answers {

        @Test
        void anAcceptedRequestPutsTheClanInTheWar() {
            ClanWarAid aid = aid(ClanWarAidDirection.request, ClanWarTeam.defend);

            service.resolve(aid.getId(), 1L, ClanWarAidStatus.accepted);

            verify(accessService).require(ally.getId(), 1L, ClanPermission.COALITION);
            ArgumentCaptor<ClanWarSide> side = ArgumentCaptor.forClass(ClanWarSide.class);
            verify(sideRepository).saveAndFlush(side.capture());
            assertThat(side.getValue().getClan()).isSameAs(ally);
            assertThat(side.getValue().getTeam()).isEqualTo(ClanWarTeam.defend);
            assertThat(side.getValue().getStandingAtDeclare()).isEqualTo(5000.0);
            verify(stakeService).rebalance(any());
            verify(rosterService).sync(eq(war), any());
            verify(chatChannel).announce(eq(defender), eq(ChatNotice.ofWar(ChatEvent.war_ally_joined, commander, ally,
                    war)));
            verify(feed).war(war);
            assertThat(aid.getStatus()).isEqualTo(ClanWarAidStatus.accepted);
            assertThat(aid.getResolvedAt()).isNotNull();
            verify(notifier).aidAnswered(aid, commander);
        }

        @Test
        void beforeTheWarStartsJoiningWaitsForTheRosterAtStart() {
            war.setStatus(ClanWarStatus.preparing);
            ClanWarAid aid = aid(ClanWarAidDirection.request, ClanWarTeam.defend);

            service.resolve(aid.getId(), 1L, ClanWarAidStatus.accepted);

            verify(stakeService).rebalance(any());
            verify(rosterService, never()).sync(any(), any());
        }

        @Test
        void anOfferIsAnsweredByTheLeadAndCancelledByTheClanThatMadeIt() {
            ClanWarAid aid = aid(ClanWarAidDirection.offer, ClanWarTeam.defend);
            doThrow(new ForbiddenException("no")).when(accessService)
                    .require(defender.getId(), 1L, ClanPermission.COALITION);

            assertThatThrownBy(() -> service.resolve(aid.getId(), 1L, ClanWarAidStatus.accepted))
                    .isInstanceOf(ForbiddenException.class);

            service.resolve(aid.getId(), 1L, ClanWarAidStatus.cancelled);

            verify(accessService).require(ally.getId(), 1L, ClanPermission.COALITION);
            assertThat(aid.getStatus()).isEqualTo(ClanWarAidStatus.cancelled);
            verify(sideRepository, never()).saveAndFlush(any());
        }

        @Test
        void somethingAlreadyAnsweredCannotBeAnsweredAgain() {
            ClanWarAid aid = aid(ClanWarAidDirection.request, ClanWarTeam.defend);
            aid.setStatus(ClanWarAidStatus.declined);

            assertThatThrownBy(() -> service.resolve(aid.getId(), 1L, ClanWarAidStatus.accepted))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void pendingIsNotAnAnswer() {
            ClanWarAid aid = aid(ClanWarAidDirection.request, ClanWarTeam.defend);

            assertThatThrownBy(() -> service.resolve(aid.getId(), 1L, ClanWarAidStatus.pending))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class Leaving {

        private ClanWarSide allySide;

        @BeforeEach
        void anAllyInTheWar() {
            allySide = ClanWarSide.builder().war(war).clan(ally).team(ClanWarTeam.defend).stake(2000.0)
                    .stakeRemaining(1500.0).build();
            sides.add(allySide);
        }

        @Test
        void anAllyThatLeavesForfeitsItsSliceAndStopsFighting() {
            service.leave(war.getId(), ally.getId(), 1L);

            verify(accessService).require(ally.getId(), 1L, ClanPermission.COALITION);
            verify(stakeService).forfeit(eq(war), any(), eq(allySide));
            verify(rosterService).sync(eq(war), any());
            verify(chatChannel).announce(eq(defender), eq(ChatNotice.ofWar(ChatEvent.war_ally_left, commander, ally,
                    war)));
            verify(notifier).allyLeft(war, allySide);
            verify(warService, never()).end(any(), any());
        }

        @Test
        void aTeamLeftWithNothingToLoseLosesTheWar() {
            when(stakeService.lost(any(), eq(ClanWarTeam.defend))).thenReturn(true);

            service.leave(war.getId(), ally.getId(), 1L);

            verify(warService).end(war.getId(), ClanWarOutcome.attacker_won);
        }

        @Test
        void theClansThatStartedTheWarCannotLeaveIt() {
            when(roster.lock(defender.getId())).thenReturn(defender);

            assertThatThrownBy(() -> service.leave(war.getId(), defender.getId(), 1L))
                    .isInstanceOf(ConflictException.class);
            verify(stakeService, never()).forfeit(any(), any(), any());
        }

        @Test
        void aClanGoingAwayLeavesEveryWarItWasHelpingIn() {
            when(sideRepository.findOpenAllyWarIds(ally.getId())).thenReturn(List.of(war.getId()));

            service.leaveAll(ally.getId());

            verify(aidRepository).cancelPendingForClan(eq(ally.getId()), any());
            verify(stakeService).forfeit(eq(war), any(), eq(allySide));
        }
    }
}
