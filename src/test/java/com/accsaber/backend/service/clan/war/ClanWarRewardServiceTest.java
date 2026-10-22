package com.accsaber.backend.service.clan.war;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.clan.ClanWarRewardItemRequest;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarOutcome;
import com.accsaber.backend.model.entity.clan.war.ClanWarParticipant;
import com.accsaber.backend.model.entity.clan.war.ClanWarRewardItem;
import com.accsaber.backend.model.entity.clan.war.ClanWarSide;
import com.accsaber.backend.model.entity.clan.war.ClanWarStatus;
import com.accsaber.backend.model.entity.clan.war.ClanWarTeam;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemType;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.clan.ClanStandingEventRepository;
import com.accsaber.backend.repository.clan.ClanXpGrantRepository;
import com.accsaber.backend.repository.clan.war.ClanWarHitRepository;
import com.accsaber.backend.repository.clan.war.ClanWarParticipantRepository;
import com.accsaber.backend.repository.clan.war.ClanWarRepository;
import com.accsaber.backend.repository.clan.war.ClanWarRewardItemRepository;
import com.accsaber.backend.repository.clan.war.ClanWarSideRepository;
import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.service.clan.ClanCosmeticService;

@ExtendWith(MockitoExtension.class)
class ClanWarRewardServiceTest {

    @Mock
    private ClanWarRewardItemRepository rewardItemRepository;
    @Mock
    private ItemRepository itemRepository;
    @Mock
    private ClanWarRepository warRepository;
    @Mock
    private ClanWarSideRepository sideRepository;
    @Mock
    private ClanWarParticipantRepository participantRepository;
    @Mock
    private ClanWarHitRepository hitRepository;
    @Mock
    private ClanXpGrantRepository xpGrantRepository;
    @Mock
    private ClanStandingEventRepository standingEventRepository;
    @Mock
    private ClanCosmeticService cosmeticService;
    @Spy
    private ClanProperties clanProperties = new ClanProperties();

    @InjectMocks
    private ClanWarRewardService service;

    private final Item crate = Item.builder().id(UUID.randomUUID()).name("War Crate")
            .type(ItemType.builder().id(UUID.randomUUID()).key("crate").build()).build();

    private ClanWarRewardItemRequest request(UUID itemId, Integer quantity, Integer topContributors) {
        ClanWarRewardItemRequest request = new ClanWarRewardItemRequest();
        request.setItemId(itemId);
        request.setQuantity(quantity);
        request.setTopContributors(topContributors);
        return request;
    }

    @Test
    void aNewRewardDefaultsToOneForEveryContributor() {
        when(itemRepository.findById(crate.getId())).thenReturn(Optional.of(crate));
        when(rewardItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.create(request(crate.getId(), null, null));

        assertThat(response.quantity()).isEqualTo(1);
        assertThat(response.topContributors()).isNull();
        assertThat(response.active()).isTrue();
        assertThat(response.item().getName()).isEqualTo("War Crate");
    }

    @Test
    void aRewardNeedsAnItem() {
        assertThatThrownBy(() -> service.create(request(null, 2, 3))).isInstanceOf(ValidationException.class);
        verify(rewardItemRepository, never()).save(any());
    }

    @Test
    void anUpdateOnlyTouchesWhatWasSent() {
        ClanWarRewardItem reward = ClanWarRewardItem.builder().id(UUID.randomUUID()).item(crate).quantity(2).build();
        when(rewardItemRepository.findById(reward.getId())).thenReturn(Optional.of(reward));
        when(rewardItemRepository.save(reward)).thenReturn(reward);

        service.update(reward.getId(), request(null, null, 5));

        assertThat(reward.getQuantity()).isEqualTo(2);
        assertThat(reward.getTopContributors()).isEqualTo(5);
    }

    @Test
    void retiringARewardKeepsTheRow() {
        ClanWarRewardItem reward = ClanWarRewardItem.builder().id(UUID.randomUUID()).item(crate).build();
        when(rewardItemRepository.findById(reward.getId())).thenReturn(Optional.of(reward));

        service.deactivate(reward.getId());

        assertThat(reward.isActive()).isFalse();
        verify(rewardItemRepository, never()).delete(any());
    }

    @Test
    void anEndedWarShowsWhatEachClanAndEachFighterGotOutOfIt() {
        Clan red = Clan.builder().id(UUID.randomUUID()).build();
        Clan blue = Clan.builder().id(UUID.randomUUID()).build();
        ClanWar war = ClanWar.builder().id(UUID.randomUUID()).attackerClan(red).defenderClan(blue)
                .status(ClanWarStatus.ended).outcome(ClanWarOutcome.attacker_won)
                .startsAt(Instant.now().minusSeconds(86_400)).endedAt(Instant.now()).build();
        ClanWarRewardItem everyone = ClanWarRewardItem.builder().id(UUID.randomUUID()).item(crate).quantity(1).build();
        ClanWarRewardItem topOne = ClanWarRewardItem.builder().id(UUID.randomUUID()).quantity(1).topContributors(1)
                .item(Item.builder().id(UUID.randomUUID()).type(ItemType.builder().key("badge").build()).build()).build();
        ClanWarRewardItem cosmetic = ClanWarRewardItem.builder().id(UUID.randomUUID()).quantity(1)
                .item(Item.builder().id(UUID.randomUUID()).type(ItemType.builder().key("clan_border")
                        .parentType(ItemType.builder().key("clan_cosmetic").build()).build()).build()).build();
        when(warRepository.findById(war.getId())).thenReturn(Optional.of(war));
        when(sideRepository.findByWarId(war.getId())).thenReturn(List.of(
                ClanWarSide.builder().war(war).clan(red).team(ClanWarTeam.attack).build(),
                ClanWarSide.builder().war(war).clan(blue).team(ClanWarTeam.defend).build()));
        when(cosmeticService.publicRefs(any())).thenReturn(Map.of());
        when(rewardItemRepository.findActiveWithItems()).thenReturn(List.of(everyone, topOne, cosmetic));
        when(xpGrantRepository.warClanXp(war.getId())).thenReturn(Map.of(red.getId(), 351.0, blue.getId(), 12.0));
        when(standingEventRepository.warStanding(war.getId())).thenReturn(Map.of(red.getId(), 1240.0, blue.getId(), -240.0));
        when(participantRepository.findByWarIdInContributionOrder(war.getId())).thenReturn(List.of(
                fighter(1L, red, 300, 60.0), fighter(11L, blue, 200, 40.0), fighter(2L, red, 0, 0.0)));
        when(hitRepository.breakXp(war.getId())).thenReturn(Map.of(1L, 25.0));

        var rewards = service.rewards(war.getId());

        assertThat(rewards.pool()).hasSize(3);
        assertThat(rewards.clans()).extracting(c -> c.clanXp()).containsExactly(351.0, 12.0);
        assertThat(rewards.clans()).extracting(c -> c.standing()).containsExactly(1240.0, -240.0);
        assertThat(rewards.clans().get(0).items()).extracting(r -> r.id()).containsExactly(cosmetic.getId());
        assertThat(rewards.clans().get(1).items()).isEmpty();
        assertThat(rewards.players()).extracting(p -> p.xp()).containsExactly(85.0, 40.0, 0.0);
        assertThat(rewards.players().get(0).items()).extracting(r -> r.id())
                .containsExactly(everyone.getId(), topOne.getId());
        assertThat(rewards.players().get(1).items()).isEmpty();
        assertThat(rewards.players().get(2).items()).isEmpty();
    }

    private static ClanWarParticipant fighter(Long userId, Clan clan, double contribution, double xpAwarded) {
        return ClanWarParticipant.builder().user(User.builder().id(userId).build()).clan(clan)
                .contribution(contribution).xpAwarded(xpAwarded).build();
    }
}
