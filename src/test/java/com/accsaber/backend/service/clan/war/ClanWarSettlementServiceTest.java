package com.accsaber.backend.service.clan.war;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanXpSource;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarOutcome;
import com.accsaber.backend.model.entity.clan.war.ClanWarParticipant;
import com.accsaber.backend.model.entity.clan.war.ClanWarRewardItem;
import com.accsaber.backend.model.entity.clan.war.ClanWarSide;
import com.accsaber.backend.model.entity.clan.war.ClanWarStatus;
import com.accsaber.backend.model.entity.clan.war.ClanWarTeam;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.item.ItemType;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.clan.ClanItemRepository;
import com.accsaber.backend.repository.clan.war.ClanWarParticipantRepository;
import com.accsaber.backend.repository.clan.war.ClanWarRepository;
import com.accsaber.backend.repository.clan.war.ClanWarRewardItemRepository;
import com.accsaber.backend.repository.clan.war.ClanWarSideRepository;
import com.accsaber.backend.service.clan.ClanLevelService;
import com.accsaber.backend.service.clan.ClanXpAward;
import com.accsaber.backend.service.item.ItemAwardService;
import com.accsaber.backend.service.item.LevelUpAwardService;

@ExtendWith(MockitoExtension.class)
class ClanWarSettlementServiceTest {

    @Mock
    private ClanWarRepository warRepository;
    @Mock
    private ClanWarParticipantRepository participantRepository;
    @Mock
    private ClanWarSideRepository sideRepository;
    @Mock
    private ClanWarStakeService stakeService;
    @Mock
    private ClanWarRewardItemRepository rewardItemRepository;
    @Mock
    private ClanItemRepository clanItemRepository;
    @Mock
    private ClanLevelService levelService;
    @Mock
    private LevelUpAwardService levelUpAwardService;
    @Mock
    private ItemAwardService itemAwardService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private final ClanProperties clanProperties = new ClanProperties();
    private ClanWarSettlementService service;

    private final Clan red = Clan.builder().id(UUID.randomUUID()).tag("RED").active(true).build();
    private final Clan blue = Clan.builder().id(UUID.randomUUID()).tag("BLU").active(true).build();
    private final Clan ally = Clan.builder().id(UUID.randomUUID()).tag("ALY").active(true).build();
    private ClanWar war;
    private final List<ClanWarSide> sides = new ArrayList<>();

    @BeforeEach
    void setUp() {
        clanProperties.getWar().setWinClanXp(1000.0);
        clanProperties.getWar().setXpPerContribution(0.5);
        service = new ClanWarSettlementService(warRepository, participantRepository, sideRepository, stakeService,
                rewardItemRepository, clanItemRepository, levelService, levelUpAwardService, itemAwardService,
                clanProperties, transactionTemplate);
        war = ClanWar.builder().id(UUID.randomUUID()).attackerClan(red).defenderClan(blue)
                .status(ClanWarStatus.ended).outcome(ClanWarOutcome.attacker_won)
                .startsAt(Instant.now().minusSeconds(86_400)).endedAt(Instant.now()).build();
        sides.add(ClanWarSide.builder().war(war).clan(red).team(ClanWarTeam.attack).stake(3000).build());
        sides.add(ClanWarSide.builder().war(war).clan(blue).team(ClanWarTeam.defend).stake(3000).build());
        lenient().when(sideRepository.findByWarId(war.getId())).thenReturn(sides);
        lenient().when(stakeService.engagement(any(), any())).thenReturn(1.0);
        lenient().when(stakeService.winShares(any())).thenCallRealMethod();
        lenient().when(warRepository.findWithRefsById(war.getId())).thenReturn(Optional.of(war));
        lenient().doAnswer(inv -> {
            inv.<Consumer<TransactionStatus>>getArgument(0).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        lenient().when(participantRepository.markRewarded(eq(war.getId()), anyLong(), anyDouble(), any())).thenReturn(1);
    }

    private ClanWarParticipant participant(Long userId, Clan clan, double contribution) {
        return ClanWarParticipant.builder().war(war).user(User.builder().id(userId).build()).clan(clan)
                .contribution(contribution).build();
    }

    private ClanWarRewardItem reward(String typeKey, String parentKey, Integer top) {
        ItemType parent = parentKey == null ? null : ItemType.builder().key(parentKey).build();
        Item item = Item.builder().id(UUID.randomUUID()).type(ItemType.builder().key(typeKey).parentType(parent).build())
                .build();
        return ClanWarRewardItem.builder().item(item).quantity(1).topContributors(top).build();
    }

    @Test
    void aWinPaysClanXpEveryoneByContributionAndItemsToTheWinnersInOrder() {
        ClanWarRewardItem banner = reward("clan_title_effect", "clan_cosmetic", null);
        ClanWarRewardItem crate = reward("crate", null, null);
        ClanWarRewardItem trophy = reward("badge", null, 1);
        when(rewardItemRepository.findActiveWithItems()).thenReturn(List.of(banner, crate, trophy));
        when(participantRepository.findByWarIdInContributionOrder(war.getId())).thenReturn(List.of(
                participant(1L, red, 300), participant(11L, blue, 200), participant(2L, red, 100),
                participant(3L, red, 0)));

        service.settle(war.getId());

        verify(levelService).grantXp(red.getId(), new ClanXpAward(1000.0, ClanXpSource.war_win,
                war.getId().toString(), true));
        verify(clanItemRepository).grantItem(red.getId(), banner.getItem().getId(), "war", war.getId().toString());
        verify(levelUpAwardService).addClanXp(1L, 150.0);
        verify(levelUpAwardService).addClanXp(11L, 100.0);
        verify(levelUpAwardService).addClanXp(2L, 50.0);
        verify(levelUpAwardService, never()).addClanXp(eq(3L), anyDouble());
        verify(participantRepository).markRewarded(eq(war.getId()), eq(3L), eq(0.0), any());
        InOrder order = inOrder(itemAwardService);
        order.verify(itemAwardService).awardSystem(eq(1L), eq(crate.getItem().getId()), eq(ItemSource.clan_war),
                eq(war.getId().toString()), anyString(), eq(1));
        order.verify(itemAwardService).awardSystem(eq(1L), eq(trophy.getItem().getId()), eq(ItemSource.clan_war),
                anyString(), anyString(), eq(1));
        order.verify(itemAwardService).awardSystem(eq(2L), eq(crate.getItem().getId()), eq(ItemSource.clan_war),
                anyString(), anyString(), eq(1));
        verify(itemAwardService, never()).awardSystem(eq(2L), eq(trophy.getItem().getId()), any(), anyString(),
                anyString(), anyInt());
        verify(itemAwardService, never()).awardSystem(eq(11L), any(), any(), anyString(), anyString(), anyInt());
        verify(itemAwardService, never()).awardSystem(eq(3L), any(), any(), anyString(), anyString(), anyInt());
    }

    @Test
    void aCoalitionSplitsTheWinBySliceAndRanksItsFightersTogether() {
        sides.add(ClanWarSide.builder().war(war).clan(ally).team(ClanWarTeam.attack).stake(1000).build());
        Clan deserter = Clan.builder().id(UUID.randomUUID()).build();
        sides.add(ClanWarSide.builder().war(war).clan(deserter).team(ClanWarTeam.attack).stake(500)
                .leftAt(Instant.now()).build());
        ClanWarRewardItem banner = reward("clan_title_effect", "clan_cosmetic", null);
        ClanWarRewardItem trophy = reward("badge", null, 2);
        when(rewardItemRepository.findActiveWithItems()).thenReturn(List.of(banner, trophy));
        when(participantRepository.findByWarIdInContributionOrder(war.getId())).thenReturn(List.of(
                participant(21L, ally, 400), participant(31L, deserter, 350), participant(1L, red, 300),
                participant(2L, red, 100)));

        service.settle(war.getId());

        String source = war.getId().toString();
        verify(levelService).grantXp(red.getId(), new ClanXpAward(750.0, ClanXpSource.war_win, source, true));
        verify(levelService).grantXp(ally.getId(), new ClanXpAward(250.0, ClanXpSource.war_win, source, true));
        verify(levelService, never()).grantXp(eq(deserter.getId()), any());
        verify(clanItemRepository).grantItem(red.getId(), banner.getItem().getId(), "war", source);
        verify(clanItemRepository).grantItem(ally.getId(), banner.getItem().getId(), "war", source);
        verify(itemAwardService).awardSystem(eq(21L), eq(trophy.getItem().getId()), any(), anyString(), anyString(),
                eq(1));
        verify(itemAwardService).awardSystem(eq(1L), eq(trophy.getItem().getId()), any(), anyString(), anyString(),
                eq(1));
        verify(itemAwardService, never()).awardSystem(eq(31L), any(), any(), anyString(), anyString(), anyInt());
        verify(itemAwardService, never()).awardSystem(eq(2L), any(), any(), anyString(), anyString(), anyInt());
    }

    @Test
    void beatingAnEnemyThatBarelyPlayedPaysLess() {
        when(stakeService.engagement(war, ClanWarTeam.defend)).thenReturn(0.5);
        when(rewardItemRepository.findActiveWithItems()).thenReturn(List.of());
        when(participantRepository.findByWarIdInContributionOrder(war.getId())).thenReturn(List.of(
                participant(1L, red, 300), participant(11L, blue, 200)));

        service.settle(war.getId());

        verify(levelService).grantXp(red.getId(), new ClanXpAward(500.0, ClanXpSource.war_win,
                war.getId().toString(), true));
        verify(stakeService).awardWin(war, List.of(sides.get(0)), 0.5);
        verify(levelUpAwardService).addClanXp(1L, 75.0);
        verify(levelUpAwardService).addClanXp(11L, 100.0);
    }

    @Test
    void aDrawStillPaysTheFightersButNoWinnings() {
        war.setOutcome(ClanWarOutcome.drawn);
        when(participantRepository.findByWarIdInContributionOrder(war.getId())).thenReturn(List.of(
                participant(1L, red, 40)));

        service.settle(war.getId());

        verify(levelService, never()).grantXp(any(), any());
        verify(stakeService, never()).awardWin(any(), any(), anyDouble());
        verify(rewardItemRepository, never()).findActiveWithItems();
        verify(levelUpAwardService).addClanXp(1L, 20.0);
    }

    @Test
    void aWarThatEndedDuringPreparationPaysNoWinnings() {
        blue.setActive(false);
        war.setOutcome(ClanWarOutcome.forfeited);
        war.setStartsAt(Instant.now().plusSeconds(3600));
        when(participantRepository.findByWarIdInContributionOrder(war.getId())).thenReturn(List.of(
                participant(1L, red, 0), participant(11L, blue, 0)));

        service.settle(war.getId());

        verify(levelService, never()).grantXp(any(), any());
        verify(rewardItemRepository, never()).findActiveWithItems();
        verify(levelUpAwardService, never()).addClanXp(anyLong(), anyDouble());
    }

    @Test
    void someoneAlreadyPaidIsSkipped() {
        war.setOutcome(ClanWarOutcome.drawn);
        ClanWarParticipant paid = participant(1L, red, 40);
        paid.setRewardedAt(Instant.now());
        when(participantRepository.findByWarIdInContributionOrder(war.getId())).thenReturn(List.of(paid));

        service.settle(war.getId());

        verify(participantRepository, never()).markRewarded(any(), anyLong(), anyDouble(), any());
    }

    @Test
    void theClanThatStayedWinsAForfeit() {
        blue.setActive(false);
        war.setOutcome(ClanWarOutcome.forfeited);

        assertThat(ClanWarSettlementService.winningTeam(war)).isEqualTo(ClanWarTeam.attack);
    }

    @Test
    void aRetreatHasNoWinner() {
        war.setOutcome(ClanWarOutcome.retreated);

        assertThat(ClanWarSettlementService.winningTeam(war)).isNull();
    }
}
