package com.accsaber.backend.service.clan.war;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
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
import com.accsaber.backend.model.entity.clan.war.ClanWarStatus;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.item.ItemType;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.clan.ClanItemRepository;
import com.accsaber.backend.repository.clan.war.ClanWarParticipantRepository;
import com.accsaber.backend.repository.clan.war.ClanWarRepository;
import com.accsaber.backend.repository.clan.war.ClanWarRewardItemRepository;
import com.accsaber.backend.service.clan.ClanLevelService;
import com.accsaber.backend.service.clan.ClanXpAward;
import com.accsaber.backend.service.item.ItemService;
import com.accsaber.backend.service.item.LevelUpAwardService;

@ExtendWith(MockitoExtension.class)
class ClanWarSettlementServiceTest {

    @Mock
    private ClanWarRepository warRepository;
    @Mock
    private ClanWarParticipantRepository participantRepository;
    @Mock
    private ClanWarRewardItemRepository rewardItemRepository;
    @Mock
    private ClanItemRepository clanItemRepository;
    @Mock
    private ClanLevelService levelService;
    @Mock
    private LevelUpAwardService levelUpAwardService;
    @Mock
    private ItemService itemService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private final ClanProperties clanProperties = new ClanProperties();
    private ClanWarSettlementService service;

    private final Clan red = Clan.builder().id(UUID.randomUUID()).tag("RED").active(true).build();
    private final Clan blue = Clan.builder().id(UUID.randomUUID()).tag("BLU").active(true).build();
    private ClanWar war;

    @BeforeEach
    void setUp() {
        clanProperties.getWar().setWinClanXp(1000.0);
        clanProperties.getWar().setXpPerContribution(0.5);
        service = new ClanWarSettlementService(warRepository, participantRepository, rewardItemRepository,
                clanItemRepository, levelService, levelUpAwardService, itemService, clanProperties, transactionTemplate);
        war = ClanWar.builder().id(UUID.randomUUID()).attackerClan(red).defenderClan(blue)
                .status(ClanWarStatus.ended).outcome(ClanWarOutcome.attacker_won).endedAt(Instant.now()).build();
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
        ClanWarRewardItem banner = reward("clan_banner", "clan_cosmetic", null);
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
        verify(levelUpAwardService).addXp(1L, 150.0);
        verify(levelUpAwardService).addXp(11L, 100.0);
        verify(levelUpAwardService).addXp(2L, 50.0);
        verify(levelUpAwardService, never()).addXp(eq(3L), anyDouble());
        verify(participantRepository).markRewarded(eq(war.getId()), eq(3L), eq(0.0), any());
        InOrder order = inOrder(itemService);
        order.verify(itemService).awardSystem(eq(1L), eq(crate.getItem().getId()), eq(ItemSource.clan_war),
                eq(war.getId().toString()), anyString(), eq(1));
        order.verify(itemService).awardSystem(eq(1L), eq(trophy.getItem().getId()), eq(ItemSource.clan_war),
                anyString(), anyString(), eq(1));
        order.verify(itemService).awardSystem(eq(2L), eq(crate.getItem().getId()), eq(ItemSource.clan_war),
                anyString(), anyString(), eq(1));
        verify(itemService, never()).awardSystem(eq(2L), eq(trophy.getItem().getId()), any(), anyString(),
                anyString(), anyInt());
        verify(itemService, never()).awardSystem(eq(11L), any(), any(), anyString(), anyString(), anyInt());
    }

    @Test
    void aDrawStillPaysTheFightersButNoWinnings() {
        war.setOutcome(ClanWarOutcome.drawn);
        when(participantRepository.findByWarIdInContributionOrder(war.getId())).thenReturn(List.of(
                participant(1L, red, 40)));

        service.settle(war.getId());

        verify(levelService, never()).grantXp(any(), any());
        verify(rewardItemRepository, never()).findActiveWithItems();
        verify(levelUpAwardService).addXp(1L, 20.0);
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

        assertThat(ClanWarSettlementService.winner(war)).isSameAs(red);
    }

    @Test
    void aRetreatHasNoWinner() {
        war.setOutcome(ClanWarOutcome.retreated);

        assertThat(ClanWarSettlementService.winner(war)).isNull();
    }
}
