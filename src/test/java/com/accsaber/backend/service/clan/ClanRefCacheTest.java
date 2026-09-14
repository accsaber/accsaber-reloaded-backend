package com.accsaber.backend.service.clan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanEquippedItem;
import com.accsaber.backend.model.entity.clan.ClanMember;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemType;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.clan.ClanEquippedItemRepository;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.ClanRepository;

@ExtendWith(MockitoExtension.class)
class ClanRefCacheTest {

    @Mock
    private ClanRepository clanRepository;
    @Mock
    private ClanMemberRepository memberRepository;
    @Mock
    private ClanEquippedItemRepository equippedRepository;

    @InjectMocks
    private ClanRefCache cache;

    private final Clan owls = Clan.builder().id(new UUID(1L, 1L)).name("Night Owls").tag("NOW").slug("night-owls")
            .build();
    private final Clan lapiz = Clan.builder().id(new UUID(2L, 1L)).name("El Lapiz").tag("LPZ").slug("el-lapiz").build();

    @AfterEach
    void emptyTheCache() {
        lenient().when(memberRepository.findAllOpenInActiveClans()).thenReturn(List.of());
        lenient().when(equippedRepository.findAllOfActiveClans()).thenReturn(List.of());
        cache.reload();
    }

    private ClanMember member(Clan clan, long userId) {
        return ClanMember.builder().clan(clan).user(User.builder().id(userId).build()).build();
    }

    private ClanEquippedItem equipped(Clan clan, String typeKey) {
        ItemType type = ItemType.builder().id(UUID.randomUUID()).key(typeKey).build();
        return ClanEquippedItem.builder().clan(clan).itemType(type)
                .item(Item.builder().id(UUID.randomUUID()).name(typeKey).type(type).build()).build();
    }

    @Test
    void everyMemberSharesTheirClansRefWithItsNameCosmeticsButNotTheBanner() {
        when(equippedRepository.findAllOfActiveClans())
                .thenReturn(List.of(equipped(owls, "clan_emblem"), equipped(owls, "clan_banner")));
        when(memberRepository.findAllOpenInActiveClans())
                .thenReturn(List.of(member(owls, 1L), member(owls, 2L), member(lapiz, 3L)));

        cache.reload();

        PublicClanResponse first = ClanRefCache.forUser(1L);
        assertThat(first.tag()).isEqualTo("NOW");
        assertThat(first.equipped()).extracting(item -> item.getTypeKey()).containsExactly("clan_emblem");
        assertThat(ClanRefCache.forUser("2")).isSameAs(first);
        assertThat(ClanRefCache.forUser(3L).equipped()).isEmpty();
        assertThat(ClanRefCache.forUser(4L)).isNull();
        assertThat(ClanRefCache.forUser((Long) null)).isNull();
    }

    @Test
    void refreshingAClanDropsLeaversAddsJoinersAndLeavesOtherClansAlone() {
        when(equippedRepository.findAllOfActiveClans()).thenReturn(List.of());
        when(memberRepository.findAllOpenInActiveClans()).thenReturn(List.of(member(owls, 1L), member(lapiz, 3L)));
        cache.reload();
        owls.setTag("OWL");
        when(clanRepository.findByIdAndActiveTrue(owls.getId())).thenReturn(Optional.of(owls));
        when(equippedRepository.findByClanIds(List.of(owls.getId()))).thenReturn(List.of());
        when(memberRepository.findOpenUserIds(owls.getId())).thenReturn(List.of(2L));

        cache.refresh(owls.getId());

        assertThat(ClanRefCache.forUser(1L)).isNull();
        assertThat(ClanRefCache.forUser(2L).tag()).isEqualTo("OWL");
        assertThat(ClanRefCache.forUser(3L).tag()).isEqualTo("LPZ");
    }

    @Test
    void aDisbandedClanDisappearsFromEveryPlayer() {
        when(equippedRepository.findAllOfActiveClans()).thenReturn(List.of());
        when(memberRepository.findAllOpenInActiveClans()).thenReturn(List.of(member(owls, 1L)));
        cache.reload();
        when(clanRepository.findByIdAndActiveTrue(owls.getId())).thenReturn(Optional.empty());

        cache.refresh(owls.getId());

        assertThat(ClanRefCache.forUser(1L)).isNull();
    }

    @Test
    void aPlayerRefCarriesTheClan() {
        when(equippedRepository.findAllOfActiveClans()).thenReturn(List.of());
        when(memberRepository.findAllOpenInActiveClans()).thenReturn(List.of(member(owls, 1L)));
        cache.reload();

        assertThat(PlayerRef.of(User.builder().id(1L).name("Founder").build()).clan().tag()).isEqualTo("NOW");
        assertThat(PlayerRef.of(User.builder().id(9L).name("Clanless").build()).clan()).isNull();
    }
}
