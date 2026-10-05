package com.accsaber.backend.service.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemType;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.repository.item.UserItemLinkRepository;

@ExtendWith(MockitoExtension.class)
class ItemSerialResequenceServiceTest {

    @Mock
    private ItemRepository itemRepository;
    @Mock
    private UserItemLinkRepository userItemLinkRepository;

    @InjectMocks
    private ItemSerialResequenceService service;

    private UUID itemId;
    private Item item;

    @BeforeEach
    void setUp() {
        itemId = UUID.randomUUID();
        item = Item.builder()
                .id(itemId)
                .type(ItemType.builder().id(UUID.randomUUID()).key("badge").name("Badge").build())
                .name("Novice No More")
                .serialized(true)
                .tradeable(false)
                .build();
    }

    private void itemIsResequenceable() {
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(userItemLinkRepository.countUnorderableLinks(itemId)).thenReturn(0L);
    }

    private Object[] row(UUID linkId, Long current, long target) {
        return new Object[] { linkId, current, target };
    }

    @Nested
    class Guards {

        static Stream<Arguments> ineligibleItems() {
            return Stream.of(
                    Arguments.of("tradeable", (Consumer<Item>) i -> i.setTradeable(true)),
                    Arguments.of("not serialized", (Consumer<Item>) i -> i.setSerialized(false)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("ineligibleItems")
        void rejectsIneligibleItem(String expectedMessage, Consumer<Item> mutation) {
            mutation.accept(item);
            when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));

            assertThatThrownBy(() -> service.resequenceItem(itemId))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining(expectedMessage);

            verify(userItemLinkRepository, never()).clearSerials(any());
        }

        @Test
        void rejectsItemWithEscrowedOrForeignSourceCopies() {
            when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));
            when(userItemLinkRepository.countUnorderableLinks(itemId)).thenReturn(3L);

            assertThatThrownBy(() -> service.resequenceItem(itemId))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("escrowed");

            verify(userItemLinkRepository, never()).clearSerials(any());
        }
    }

    @Nested
    class Resequencing {

        @Test
        void writesNothingWhenSerialsAlreadyFollowEarnedOrder() {
            itemIsResequenceable();
            when(userItemLinkRepository.findSerialsInEarnedOrder(itemId)).thenReturn(List.of(
                    row(UUID.randomUUID(), 1L, 1L),
                    row(UUID.randomUUID(), 2L, 2L),
                    row(UUID.randomUUID(), 3L, 3L)));

            assertThat(service.resequenceItem(itemId)).isZero();

            verify(userItemLinkRepository, never()).clearSerials(any());
            verify(itemRepository, never()).resetNextSerial(any(), anyLong());
        }

        @Test
        void writesNothingWhenTheItemHasNoCopies() {
            itemIsResequenceable();
            when(userItemLinkRepository.findSerialsInEarnedOrder(itemId)).thenReturn(List.of());

            assertThat(service.resequenceItem(itemId)).isZero();

            verify(userItemLinkRepository, never()).clearSerials(any());
        }

        @Test
        void clearsBeforeAssigningSoTheUniqueIndexNeverCollides() {
            UUID earlyLink = UUID.randomUUID();
            UUID lateLink = UUID.randomUUID();
            itemIsResequenceable();
            when(userItemLinkRepository.findSerialsInEarnedOrder(itemId)).thenReturn(List.of(
                    row(earlyLink, 2L, 1L),
                    row(lateLink, 1L, 2L)));

            List<UserItemLink> links = new ArrayList<>(List.of(
                    UserItemLink.builder().id(earlyLink).item(item).serialNumber(2L).build(),
                    UserItemLink.builder().id(lateLink).item(item).serialNumber(1L).build()));
            when(userItemLinkRepository.findAllById(any())).thenReturn(links);

            int moved = service.resequenceItem(itemId);

            assertThat(moved).isEqualTo(2);
            assertThat(links.get(0).getSerialNumber()).isEqualTo(1L);
            assertThat(links.get(1).getSerialNumber()).isEqualTo(2L);
            verify(userItemLinkRepository).clearSerials(itemId);
            verify(userItemLinkRepository).saveAll(links);
        }

        @Test
        void movesFoundersOntoTheNewLowestFiveAndResetsTheCounter() {
            UUID linkId = UUID.randomUUID();
            itemIsResequenceable();
            when(userItemLinkRepository.findSerialsInEarnedOrder(itemId))
                    .thenReturn(List.<Object[]>of(row(linkId, 4L, 1L)));
            when(userItemLinkRepository.findAllById(any())).thenReturn(
                    List.of(UserItemLink.builder().id(linkId).item(item).serialNumber(4L).build()));

            service.resequenceItem(itemId);

            verify(userItemLinkRepository).stripFoundersAboveSerial(itemId, ModifierResolver.FOUNDERS_THRESHOLD);
            verify(userItemLinkRepository).grantFoundersUpToSerial(itemId, ModifierResolver.FOUNDERS_THRESHOLD);
            verify(userItemLinkRepository).stripRedundantNormal(itemId);
            verify(userItemLinkRepository).grantNormalWhereBare(itemId);
            verify(itemRepository).resetNextSerial(eq(itemId), eq(2L));
        }

        @Test
        void countsOnlyTheSerialsThatActuallyMove() {
            UUID stays = UUID.randomUUID();
            UUID moves = UUID.randomUUID();
            itemIsResequenceable();
            when(userItemLinkRepository.findSerialsInEarnedOrder(itemId)).thenReturn(List.of(
                    row(stays, 1L, 1L),
                    row(moves, 3L, 2L)));
            when(userItemLinkRepository.findAllById(any())).thenReturn(List.of(
                    UserItemLink.builder().id(stays).item(item).serialNumber(1L).build(),
                    UserItemLink.builder().id(moves).item(item).serialNumber(3L).build()));

            assertThat(service.resequenceItem(itemId)).isEqualTo(1);
        }
    }
}
