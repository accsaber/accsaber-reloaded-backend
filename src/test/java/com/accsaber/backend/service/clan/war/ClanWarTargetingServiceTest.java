package com.accsaber.backend.service.clan.war;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.clan.ChooseClanWarTargetRequest;
import com.accsaber.backend.model.dto.request.clan.OrderClanWarTargetRequest;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.war.ClanRuleset;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarOrder;
import com.accsaber.backend.model.entity.clan.war.ClanWarParticipant;
import com.accsaber.backend.model.entity.clan.war.ClanWarSide;
import com.accsaber.backend.model.entity.clan.war.ClanWarStatus;
import com.accsaber.backend.model.entity.clan.war.ClanWarTargeting;
import com.accsaber.backend.model.entity.clan.war.ClanWarTeam;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.clan.war.ClanWarParticipantRepository;
import com.accsaber.backend.repository.clan.war.ClanWarRepository;
import com.accsaber.backend.repository.clan.war.ClanWarSideRepository;
import com.accsaber.backend.service.clan.ClanAccessService;
import com.accsaber.backend.service.clan.ClanFeed;
import com.accsaber.backend.service.clan.ClanPermission;

@ExtendWith(MockitoExtension.class)
class ClanWarTargetingServiceTest {

    @Mock
    private ClanWarRepository warRepository;
    @Mock
    private ClanWarSideRepository sideRepository;
    @Mock
    private ClanWarParticipantRepository participantRepository;
    @Mock
    private ClanAccessService accessService;
    @Mock
    private ClanFeed feed;

    @InjectMocks
    private ClanWarTargetingService service;

    private final Clan attacker = Clan.builder().id(UUID.randomUUID()).build();
    private final Clan defender = Clan.builder().id(UUID.randomUUID()).build();
    private final ClanWar war = ClanWar.builder().id(UUID.randomUUID()).attackerClan(attacker).defenderClan(defender)
            .ruleset(ClanRuleset.duel).status(ClanWarStatus.active).build();
    private ClanWarParticipant me;
    private ClanWarParticipant enemy;

    @BeforeEach
    void setUp() {
        me = participant(1L, attacker);
        enemy = participant(11L, defender);
        when(warRepository.lockOrThrow(war.getId())).thenReturn(war);
        when(participantRepository.findActiveByWarId(war.getId())).thenReturn(List.of(me, enemy));
        lenient().when(sideRepository.findByWarId(war.getId())).thenReturn(new ArrayList<>(List.of(
                ClanWarSide.builder().war(war).clan(attacker).team(ClanWarTeam.attack).build(),
                ClanWarSide.builder().war(war).clan(defender).team(ClanWarTeam.defend).build())));
        lenient().when(participantRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ClanWarParticipant participant(Long userId, Clan clan) {
        return ClanWarParticipant.builder().war(war).user(User.builder().id(userId).build()).clan(clan).guard(100).build();
    }

    @Test
    void aFighterFollowsOrdersUntilTheyPickSomeoneByHand() {
        assertThat(me.getTargeting()).isEqualTo(ClanWarTargeting.follow);

        service.choose(war.getId(), 1L, new ChooseClanWarTargetRequest(ClanWarTargeting.manual, "11"));

        assertThat(me.getTargeting()).isEqualTo(ClanWarTargeting.manual);
        assertThat(me.aim()).isSameAs(enemy.getUser());
    }

    @Test
    void goingBackToFollowKeepsTheHandPick() {
        me.setTargeting(ClanWarTargeting.manual);
        me.setTarget(enemy.getUser());

        service.choose(war.getId(), 1L, new ChooseClanWarTargetRequest(ClanWarTargeting.follow, null));

        assertThat(me.getTarget()).isSameAs(enemy.getUser());
        assertThat(me.aim()).isNull();
    }

    @Test
    void aTeammateIsNotATargetAndOnlyFightersCanChoose() {
        assertThatThrownBy(() -> service.choose(war.getId(), 1L,
                new ChooseClanWarTargetRequest(ClanWarTargeting.manual, "1"))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.choose(war.getId(), 99L,
                new ChooseClanWarTargetRequest(ClanWarTargeting.auto, null))).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aCommanderSendsAFollowerAtAnEnemyOrHaltsThem() {
        service.order(war.getId(), 5L, new OrderClanWarTargetRequest("1", ClanWarOrder.target, "11"));

        verify(accessService).require(attacker.getId(), 5L, ClanPermission.ORDER_TARGETS);
        assertThat(me.aim()).isSameAs(enemy.getUser());
        verify(feed).war(war);

        service.order(war.getId(), 5L, new OrderClanWarTargetRequest("1", ClanWarOrder.halt, null));

        assertThat(me.getOrderedTarget()).isNull();
        assertThat(me.halted()).isTrue();
    }

    @Test
    void anOrderNeedsTheRank() {
        when(accessService.require(attacker.getId(), 5L, ClanPermission.ORDER_TARGETS))
                .thenThrow(new ForbiddenException("Your rank in this clan cannot do that"));

        assertThatThrownBy(() -> service.order(war.getId(), 5L,
                new OrderClanWarTargetRequest("1", ClanWarOrder.halt, null))).isInstanceOf(ForbiddenException.class);
        assertThat(me.getOrder()).isEqualTo(ClanWarOrder.auto);
    }
}
