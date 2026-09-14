package com.accsaber.backend.service.clan.war;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.model.entity.chat.ChatEvent;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarLoan;
import com.accsaber.backend.model.entity.clan.war.ClanWarLoanStatus;
import com.accsaber.backend.model.entity.clan.war.ClanWarParticipant;
import com.accsaber.backend.model.entity.clan.war.ClanWarStatus;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.event.ClanMembershipChangedEvent;
import com.accsaber.backend.repository.clan.war.ClanWarLoanRepository;
import com.accsaber.backend.repository.clan.war.ClanWarParticipantRepository;
import com.accsaber.backend.repository.clan.war.ClanWarRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.clan.ChatNotice;
import com.accsaber.backend.service.clan.ClanChatChannel;
import com.accsaber.backend.service.clan.ClanNotifier;
import com.accsaber.backend.service.clan.ClanStrengthService;
import com.accsaber.backend.service.clan.ClanStrengthService.MemberStrength;

@ExtendWith(MockitoExtension.class)
class ClanWarRosterServiceTest {

    @Mock
    private ClanWarFeed feed;
    @Mock
    private ClanNotifier notifier;
    @Mock
    private ClanWarRepository warRepository;
    @Mock
    private ClanWarParticipantRepository participantRepository;
    @Mock
    private ClanWarLoanRepository loanRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ClanStrengthService strengthService;
    @Mock
    private ClanChatChannel chatChannel;
    @Mock
    private ClanWarScoreGate scoreGate;

    private final ClanProperties clanProperties = new ClanProperties();
    private ClanWarRosterService service;

    private final Clan attacker = Clan.builder().id(UUID.randomUUID()).build();
    private final Clan defender = Clan.builder().id(UUID.randomUUID()).build();
    private ClanWar war;

    @BeforeEach
    void setUp() {
        service = new ClanWarRosterService(warRepository, participantRepository, loanRepository, userRepository, strengthService,
                chatChannel, scoreGate, feed, notifier, clanProperties);
        war = ClanWar.builder().id(UUID.randomUUID()).attackerClan(attacker).defenderClan(defender)
                .status(ClanWarStatus.preparing).startsAt(Instant.now().minusSeconds(1)).build();
        lenient().when(warRepository.findByIdForUpdate(war.getId())).thenReturn(Optional.of(war));
        lenient().when(userRepository.getReferenceById(anyLong()))
                .thenAnswer(inv -> User.builder().id(inv.getArgument(0)).build());
    }

    private void rosters(List<MemberStrength> attackers, List<MemberStrength> defenders) {
        when(strengthService.memberStrengths(attacker.getId())).thenReturn(attackers);
        when(strengthService.memberStrengths(defender.getId())).thenReturn(defenders);
    }

    @SuppressWarnings("unchecked")
    private Map<Long, ClanWarParticipant> saved() {
        ArgumentCaptor<Collection<ClanWarParticipant>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(participantRepository).saveAll(captor.capture());
        return captor.getValue().stream().collect(Collectors.toMap(p -> p.getUser().getId(), Function.identity()));
    }

    @Test
    void startingEnrolsBothRostersWithTheirWeightsAndPairsDuelsByRank() {
        rosters(List.of(new MemberStrength(1L, 90, 0.6), new MemberStrength(2L, 70, 0.3),
                new MemberStrength(3L, 10, 0.1)),
                List.of(new MemberStrength(11L, 80, 0.7), new MemberStrength(12L, 20, 0.3)));

        service.start(war.getId());

        assertThat(war.getStatus()).isEqualTo(ClanWarStatus.active);
        verify(scoreGate).refreshAfterCommit();
        Map<Long, ClanWarParticipant> participants = saved();
        assertThat(participants).hasSize(5);
        assertThat(participants.get(1L).getStandingWeight()).isEqualTo(0.6);
        assertThat(participants.get(1L).getGuard()).isEqualTo(clanProperties.getWar().getGuard());
        assertThat(participants.get(1L).getClan()).isSameAs(attacker);
        assertThat(participants.get(1L).getDuelTarget().getId()).isEqualTo(11L);
        assertThat(participants.get(3L).getDuelTarget().getId()).isEqualTo(12L);
        assertThat(participants.get(11L).getDuelTarget().getId()).isEqualTo(1L);
        assertThat(participants.get(12L).getDuelTarget().getId()).isEqualTo(3L);
        verify(chatChannel).announce(attacker, ChatNotice.ofWar(ChatEvent.war_started, null, defender, war));
        verify(chatChannel).announce(defender, ChatNotice.ofWar(ChatEvent.war_started, null, attacker, war));
        verify(feed).war(war);
        verify(notifier).warStarted(war);
    }

    @Test
    void aWarWhosePreparationIsNotOverDoesNotStart() {
        war.setStartsAt(Instant.now().plusSeconds(3600));

        service.start(war.getId());

        assertThat(war.getStatus()).isEqualTo(ClanWarStatus.preparing);
        verify(participantRepository, never()).saveAll(any());
    }

    @Test
    void aMembershipChangeRetiresLeaversEnrolsJoinersAndRepairsTheDuels() {
        war.setStatus(ClanWarStatus.active);
        when(warRepository.findByIdForUpdate(war.getId())).thenReturn(Optional.of(war));
        ClanWarParticipant leaver = ClanWarParticipant.builder().war(war).user(User.builder().id(1L).build())
                .clan(attacker).standingWeight(0.5).guard(100).build();
        ClanWarParticipant stayer = ClanWarParticipant.builder().war(war).user(User.builder().id(2L).build())
                .clan(attacker).standingWeight(0.5).guard(40).build();
        ClanWarParticipant enemy = ClanWarParticipant.builder().war(war).user(User.builder().id(11L).build())
                .clan(defender).standingWeight(1.0).guard(100).duelTarget(leaver.getUser()).build();
        when(warRepository.findActiveIdsByClanId(attacker.getId())).thenReturn(List.of(war.getId()));
        when(participantRepository.findByWarId(war.getId())).thenReturn(List.of(leaver, stayer, enemy));
        rosters(List.of(new MemberStrength(4L, 95, 0.8), new MemberStrength(2L, 60, 0.2)),
                List.of(new MemberStrength(11L, 80, 1.0)));

        service.onMembershipChanged(new ClanMembershipChangedEvent(attacker.getId()));

        Map<Long, ClanWarParticipant> participants = saved();
        assertThat(participants.get(1L).getLeftAt()).isNotNull();
        assertThat(participants.get(1L).getDuelTarget()).isNull();
        assertThat(participants.get(2L).getGuard()).isEqualTo(40);
        assertThat(participants.get(4L).getStandingWeight()).isEqualTo(0.8);
        assertThat(participants.get(11L).getDuelTarget().getId()).isEqualTo(4L);
    }

    @Test
    void anAcceptedLoanFightsForTheSideItWasLentToWithItsOwnClansWeight() {
        war.setStatus(ClanWarStatus.active);
        Clan ally = Clan.builder().id(UUID.randomUUID()).build();
        ClanWarLoan loan = ClanWarLoan.builder().war(war).clan(attacker).lendingClan(ally)
                .user(User.builder().id(21L).build()).status(ClanWarLoanStatus.accepted).build();
        when(loanRepository.findAcceptedByWarId(war.getId())).thenReturn(List.of(loan));
        rosters(List.of(new MemberStrength(1L, 50, 1.0)), List.of(new MemberStrength(11L, 60, 1.0)));
        when(strengthService.memberStrengths(ally.getId())).thenReturn(List.of(new MemberStrength(20L, 90, 0.7),
                new MemberStrength(21L, 70, 0.3)));

        service.resync(war.getId());

        Map<Long, ClanWarParticipant> participants = saved();
        assertThat(participants).containsOnlyKeys(1L, 11L, 21L);
        assertThat(participants.get(21L).getClan()).isSameAs(attacker);
        assertThat(participants.get(21L).getLentByClan()).isSameAs(ally);
        assertThat(participants.get(21L).getStandingWeight()).isEqualTo(0.3);
        assertThat(participants.get(21L).getDuelTarget().getId()).isEqualTo(11L);
        assertThat(participants.get(11L).getDuelTarget().getId()).isEqualTo(21L);
    }
}
