package com.accsaber.backend.service.clan.war;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.clan.ClanWarRewardItemRequest;
import com.accsaber.backend.model.entity.clan.war.ClanWarRewardItem;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemType;
import com.accsaber.backend.repository.clan.war.ClanWarRewardItemRepository;
import com.accsaber.backend.repository.item.ItemRepository;

@ExtendWith(MockitoExtension.class)
class ClanWarRewardServiceTest {

    @Mock
    private ClanWarRewardItemRepository rewardItemRepository;
    @Mock
    private ItemRepository itemRepository;

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
}
