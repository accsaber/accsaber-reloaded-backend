package com.accsaber.backend.service.clan.war;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.clan.ClanTrustResponse;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanCapacity;
import com.accsaber.backend.model.entity.clan.ClanMember;
import com.accsaber.backend.model.entity.clan.ClanRole;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarLoan;
import com.accsaber.backend.model.entity.clan.war.ClanWarLoanStatus;
import com.accsaber.backend.model.entity.clan.war.ClanWarStatus;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.event.ClanMembershipChangedEvent;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.war.ClanWarLoanRepository;
import com.accsaber.backend.repository.clan.war.ClanWarRepository;
import com.accsaber.backend.service.clan.ClanAccessService;
import com.accsaber.backend.service.clan.ClanAllianceService;
import com.accsaber.backend.service.clan.ClanCosmeticService;
import com.accsaber.backend.service.clan.ClanLevelService;
import com.accsaber.backend.service.clan.ClanNotifier;
import com.accsaber.backend.service.clan.ClanPermission;
import com.accsaber.backend.service.clan.ClanRoster;

@ExtendWith(MockitoExtension.class)
class ClanWarLoanServiceTest {

    @Mock
    private ClanNotifier notifier;
    @Mock
    private ClanWarLoanRepository loanRepository;
    @Mock
    private ClanWarRepository warRepository;
    @Mock
    private ClanMemberRepository memberRepository;
    @Mock
    private ClanRoster roster;
    @Mock
    private ClanAccessService accessService;
    @Mock
    private ClanAllianceService allianceService;
    @Mock
    private ClanLevelService levelService;
    @Mock
    private ClanCosmeticService cosmeticService;
    @Mock
    private ClanWarRosterService rosterService;

    private final ClanProperties clanProperties = new ClanProperties();
    private ClanWarLoanService service;

    private final Clan attacker = Clan.builder().id(UUID.randomUUID()).build();
    private final Clan defender = Clan.builder().id(UUID.randomUUID()).build();
    private final Clan ally = Clan.builder().id(UUID.randomUUID()).build();
    private final User commander = User.builder().id(1L).build();
    private final User lent = User.builder().id(2L).build();
    private ClanWar war;

    @BeforeEach
    void setUp() {
        service = new ClanWarLoanService(loanRepository, warRepository, memberRepository, roster, accessService,
                allianceService, levelService, cosmeticService, rosterService, notifier, clanProperties);
        war = ClanWar.builder().id(UUID.randomUUID()).attackerClan(attacker).defenderClan(defender)
                .status(ClanWarStatus.active).build();
        lenient().when(accessService.player(1L)).thenReturn(commander);
        lenient().when(accessService.player(2L)).thenReturn(lent);
        lenient().when(memberRepository.findOpenByUserId(1L))
                .thenReturn(Optional.of(ClanMember.builder().clan(ally).user(commander).role(ClanRole.commander).build()));
        lenient().when(roster.lock(ally.getId())).thenReturn(ally);
        lenient().when(warRepository.findByIdForUpdate(war.getId())).thenReturn(Optional.of(war));
        lenient().when(memberRepository.findOpenByClanIdAndUserId(ally.getId(), 2L))
                .thenReturn(Optional.of(ClanMember.builder().build()));
        lenient().when(allianceService.trustBetween(ally.getId(), defender.getId()))
                .thenReturn(Optional.of(new ClanTrustResponse(0, 1, 0.0)));
        lenient().when(allianceService.trustBetween(ally.getId(), attacker.getId()))
                .thenReturn(Optional.of(new ClanTrustResponse(1, 2, 600.0)));
        lenient().when(levelService.capacityOf(any(), any())).thenReturn(2);
        lenient().when(loanRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(cosmeticService.publicRefs(anyCollection())).thenAnswer(inv -> inv.<Collection<Clan>>getArgument(0)
                .stream().collect(Collectors.toMap(Clan::getId, clan -> PublicClanResponse.of(clan, List.of()),
                        (first, second) -> first)));
    }

    @Nested
    class Offer {

        @Test
        void anAllyCommanderOffersAMemberToOneSide() {
            var response = service.offer(war.getId(), 1L, 2L, defender.getId());

            verify(accessService).require(ally.getId(), 1L, ClanPermission.LEND);
            ArgumentCaptor<ClanWarLoan> loan = ArgumentCaptor.forClass(ClanWarLoan.class);
            verify(loanRepository).saveAndFlush(loan.capture());
            assertThat(loan.getValue().getClan()).isSameAs(defender);
            assertThat(loan.getValue().getLendingClan()).isSameAs(ally);
            assertThat(loan.getValue().getStatus()).isEqualTo(ClanWarLoanStatus.pending);
            assertThat(response.player().id()).isEqualTo("2");
            verify(notifier).loanOffered(loan.getValue());
        }

        @Test
        void aClanFightingInTheWarCannotLendIntoIt() {
            when(memberRepository.findOpenByUserId(1L)).thenReturn(Optional.of(ClanMember.builder().clan(attacker)
                    .user(commander).role(ClanRole.commander).build()));
            when(roster.lock(attacker.getId())).thenReturn(attacker);

            assertThatThrownBy(() -> service.offer(war.getId(), 1L, 2L, defender.getId()))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void onlyAlliesCanBorrow() {
            when(allianceService.trustBetween(ally.getId(), defender.getId())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.offer(war.getId(), 1L, 2L, defender.getId()))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void aPlayerOutOnAnotherLoanCannotBeLentAgain() {
            when(loanRepository.existsOpenByUserId(2L)).thenReturn(true);

            assertThatThrownBy(() -> service.offer(war.getId(), 1L, 2L, defender.getId()))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void aPlayerStillCoolingDownCannotBeLent() {
            when(loanRepository.findLastEndedAt(2L)).thenReturn(Optional.of(Instant.now().minus(1, ChronoUnit.DAYS)));

            assertThatThrownBy(() -> service.offer(war.getId(), 1L, 2L, defender.getId()))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void theLendingClanNeedsAFreeLendSlot() {
            when(loanRepository.countOpenByLendingClanId(ally.getId())).thenReturn(2L);

            assertThatThrownBy(() -> service.offer(war.getId(), 1L, 2L, defender.getId()))
                    .isInstanceOf(ConflictException.class);
            verify(levelService).capacityOf(ally, ClanCapacity.lend_slots);
        }

        @Test
        void anAttackerCanOnlyTakeInAsManyBorrowedPlayersAsItsSlots() {
            when(loanRepository.countOpenIntoWar(war.getId(), attacker.getId())).thenReturn(2L);

            assertThatThrownBy(() -> service.offer(war.getId(), 1L, 2L, attacker.getId()))
                    .isInstanceOf(ConflictException.class);
            verify(levelService).capacityOf(attacker, ClanCapacity.receive_slots);
        }

        @Test
        void aDefenderHasNoReceiveCap() {
            service.offer(war.getId(), 1L, 2L, defender.getId());

            verify(loanRepository, never()).countOpenIntoWar(any(), any());
            verify(levelService, never()).capacityOf(defender, ClanCapacity.receive_slots);
        }

        @Test
        void aYoungAllianceIsHeldToItsTrustCap() {
            when(loanRepository.countOpenBetween(ally.getId(), defender.getId())).thenReturn(1L);

            assertThatThrownBy(() -> service.offer(war.getId(), 1L, 2L, defender.getId()))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void aPlayerOutsideTheLendingClanCannotBeLent() {
            when(memberRepository.findOpenByClanIdAndUserId(ally.getId(), 2L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.offer(war.getId(), 1L, 2L, defender.getId()))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class Resolve {

        private ClanWarLoan loan;

        @BeforeEach
        void pending() {
            loan = ClanWarLoan.builder().id(UUID.randomUUID()).war(war).clan(defender).lendingClan(ally).user(lent)
                    .offeredBy(commander).build();
            when(loanRepository.findWithRefsById(loan.getId())).thenReturn(Optional.of(loan));
        }

        @Test
        void thePlayerAcceptsAndJoinsTheRunningWar() {
            service.resolve(loan.getId(), 2L, ClanWarLoanStatus.accepted);

            assertThat(loan.getStatus()).isEqualTo(ClanWarLoanStatus.accepted);
            assertThat(loan.getResolvedAt()).isNotNull();
            verify(rosterService).resync(war.getId());
        }

        @Test
        void somebodyElseCannotAcceptForThePlayer() {
            assertThatThrownBy(() -> service.resolve(loan.getId(), 1L, ClanWarLoanStatus.accepted))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void theLendingCommanderCancelsAPendingLoan() {
            service.resolve(loan.getId(), 1L, ClanWarLoanStatus.cancelled);

            verify(accessService).require(ally.getId(), 1L, ClanPermission.LEND);
            assertThat(loan.getStatus()).isEqualTo(ClanWarLoanStatus.cancelled);
            verify(rosterService, never()).resync(any());
        }

        @Test
        void anAnsweredLoanCannotBeAnsweredAgain() {
            loan.setStatus(ClanWarLoanStatus.declined);

            assertThatThrownBy(() -> service.resolve(loan.getId(), 2L, ClanWarLoanStatus.accepted))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void aLoanIntoAFinishedWarCannotBeAccepted() {
            war.setStatus(ClanWarStatus.ended);

            assertThatThrownBy(() -> service.resolve(loan.getId(), 2L, ClanWarLoanStatus.accepted))
                    .isInstanceOf(ConflictException.class);
        }
    }

    @Test
    void aLentPlayerWhoLeavesTheLendingClanIsCalledBack() {
        ClanWarLoan accepted = ClanWarLoan.builder().war(war).clan(defender).lendingClan(ally).user(lent)
                .status(ClanWarLoanStatus.accepted).build();
        ClanWarLoan stays = ClanWarLoan.builder().war(war).clan(defender).lendingClan(ally)
                .user(User.builder().id(3L).build()).status(ClanWarLoanStatus.pending).build();
        when(loanRepository.findOpenByLendingClanId(ally.getId())).thenReturn(List.of(accepted, stays));
        when(memberRepository.findOpenUserIds(ally.getId())).thenReturn(List.of(1L, 3L));

        service.onMembershipChanged(new ClanMembershipChangedEvent(ally.getId()));

        assertThat(accepted.getStatus()).isEqualTo(ClanWarLoanStatus.ended);
        assertThat(accepted.getEndedAt()).isNotNull();
        assertThat(stays.getStatus()).isEqualTo(ClanWarLoanStatus.pending);
        verify(rosterService).resync(war.getId());
    }
}
