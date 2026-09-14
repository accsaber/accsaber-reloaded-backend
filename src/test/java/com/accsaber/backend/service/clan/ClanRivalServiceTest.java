package com.accsaber.backend.service.clan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.entity.chat.ChatEvent;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanRival;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.clan.ClanAllianceRepository;
import com.accsaber.backend.repository.clan.ClanRepository;
import com.accsaber.backend.repository.clan.ClanRivalRepository;

@ExtendWith(MockitoExtension.class)
class ClanRivalServiceTest {

    @Mock
    private ClanRivalRepository rivalRepository;
    @Mock
    private ClanRepository clanRepository;
    @Mock
    private ClanAllianceRepository allianceRepository;
    @Mock
    private ClanAccessService accessService;
    @Mock
    private ClanCosmeticService cosmeticService;
    @Mock
    private ClanChatChannel chatChannel;

    @InjectMocks
    private ClanRivalService service;

    private final Clan owls = Clan.builder().id(UUID.randomUUID()).name("Night Owls").tag("NOW").build();
    private final Clan lapiz = Clan.builder().id(UUID.randomUUID()).name("El Lapiz").tag("LPZ").build();
    private final User commander = User.builder().id(1L).name("Commander").build();

    @BeforeEach
    void setUp() {
        lenient().when(accessService.player(1L)).thenReturn(commander);
        lenient().when(clanRepository.findByIdAndActiveTrue(owls.getId())).thenReturn(Optional.of(owls));
        lenient().when(clanRepository.findByIdAndActiveTrue(lapiz.getId())).thenReturn(Optional.of(lapiz));
        lenient().when(rivalRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(cosmeticService.publicRefs(anyCollection())).thenAnswer(inv -> inv.<Collection<Clan>>getArgument(0)
                .stream().collect(Collectors.toMap(Clan::getId, clan -> PublicClanResponse.of(clan, List.of()),
                        (first, second) -> first)));
    }

    @Test
    void aCommanderDeclaresARivalAndBothChatsHearAboutIt() {
        var response = service.declare(owls.getId(), 1L, lapiz.getId());

        verify(accessService).require(owls.getId(), 1L, ClanPermission.MANAGE_RIVALS);
        assertThat(response.clan().id()).isEqualTo(lapiz.getId());
        assertThat(response.incoming()).isFalse();
        assertThat(response.declaredBy().id()).isEqualTo("1");
        verify(chatChannel).announce(owls, ChatNotice.ofClan(ChatEvent.rival_declared, commander, lapiz));
        verify(chatChannel).announce(lapiz, ChatNotice.ofClan(ChatEvent.rivaled_by, commander, owls));
    }

    @Test
    void aDroppedRivalryComesBackOnTheSameRow() {
        ClanRival dropped = ClanRival.builder().id(UUID.randomUUID()).clan(owls).rivalClan(lapiz).active(false).build();
        when(rivalRepository.findByClan_IdAndRivalClan_Id(owls.getId(), lapiz.getId())).thenReturn(Optional.of(dropped));

        service.declare(owls.getId(), 1L, lapiz.getId());

        assertThat(dropped.isActive()).isTrue();
        assertThat(dropped.getDeclaredBy()).isSameAs(commander);
    }

    @Test
    void anActiveRivalryCannotBeDeclaredTwice() {
        ClanRival live = ClanRival.builder().clan(owls).rivalClan(lapiz).build();
        when(rivalRepository.findByClan_IdAndRivalClan_Id(owls.getId(), lapiz.getId())).thenReturn(Optional.of(live));

        assertThatThrownBy(() -> service.declare(owls.getId(), 1L, lapiz.getId()))
                .isInstanceOf(ConflictException.class);
        verify(chatChannel, never()).announce(any(), any());
    }

    @Test
    void anAllyCannotBeARival() {
        when(allianceRepository.existsActiveBetween(owls.getId(), lapiz.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.declare(owls.getId(), 1L, lapiz.getId()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void aClanCannotRivalItself() {
        assertThatThrownBy(() -> service.declare(owls.getId(), 1L, owls.getId()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void droppingDeactivatesTheRow() {
        ClanRival live = ClanRival.builder().clan(owls).rivalClan(lapiz).build();
        when(rivalRepository.findByClan_IdAndRivalClan_Id(owls.getId(), lapiz.getId())).thenReturn(Optional.of(live));

        service.drop(owls.getId(), 1L, lapiz.getId());

        assertThat(live.isActive()).isFalse();
    }

    @Test
    void droppingARivalryThatIsNotThereIsNotFound() {
        assertThatThrownBy(() -> service.drop(owls.getId(), 1L, lapiz.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void theIncomingListShowsTheClansThatDeclared() {
        ClanRival rival = ClanRival.builder().clan(lapiz).rivalClan(owls).declaredBy(commander).build();
        when(rivalRepository.findDeclaredAgainst(owls.getId(), Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(rival)));

        var page = service.list(owls.getId(), true, Pageable.unpaged());

        assertThat(page.getContent()).singleElement().satisfies(response -> {
            assertThat(response.clan().id()).isEqualTo(lapiz.getId());
            assertThat(response.incoming()).isTrue();
        });
    }
}
