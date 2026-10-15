package com.accsaber.backend.service.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemModifier;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.item.ItemType;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.item.ItemModifierRepository;
import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.repository.item.UserItemLinkRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.notification.NotificationService;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ItemServiceActiveCrateResolutionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Long USER_ID = 76561198087536397L;
    private static final UUID SENTINEL_ID = UUID.randomUUID();
    private static final String MILESTONE_ID = "b6f2c0de-0000-4000-8000-000000000001";
    private static final ItemType CRATE_TYPE = ItemType.builder().key("crate").name("Crate").build();

    @Mock
    private ItemRepository itemRepository;
    @Mock
    private UserItemLinkRepository userItemLinkRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DuplicateUserService duplicateUserService;
    @Mock
    private ItemModifierRepository itemModifierRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ItemAwardService itemAwardService;

    private final Item sentinel = Item.builder()
            .id(SENTINEL_ID)
            .type(CRATE_TYPE)
            .name("Random Active Crate")
            .value(MAPPER.createObjectNode().put("grant", "active_crate"))
            .stackable(false)
            .serialized(false)
            .active(true)
            .build();

    @BeforeEach
    void setUp() {
        when(duplicateUserService.resolvePrimaryUserId(USER_ID)).thenReturn(USER_ID);
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(User.builder().id(USER_ID).build());
        when(itemRepository.findByIdAndActiveTrue(SENTINEL_ID)).thenReturn(Optional.of(sentinel));
        when(itemModifierRepository.findByKey(ItemModifier.NORMAL))
                .thenReturn(Optional.of(ItemModifier.builder().key(ItemModifier.NORMAL).name("Normal").build()));
        when(userItemLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void picksRandomlyAmongMultipleActiveCrates() {
        Item first = crate("Spooky '26 Crate", daysAgo(30), inDays(10));
        Item second = crate("Winter '26 Crate", daysAgo(5), null);
        stubCrates(first, second, sentinel);

        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            Item resolved = itemAwardService.resolveGrantItem(sentinel);
            assertThat(resolved).isIn(first, second);
            seen.add(resolved.getId());
        }
        assertThat(seen).containsExactlyInAnyOrder(first.getId(), second.getId());
    }

    @Test
    void picksTheOnlyActiveCrate() {
        Item only = crate("Spooky '26 Crate", daysAgo(30), inDays(10));
        stubCrates(only, sentinel);

        assertThat(itemAwardService.resolveGrantItem(sentinel)).isSameAs(only);
    }

    @Test
    void fallsBackToTheMostRecentlyCreatedCrateWhenNoneIsInWindow() {
        Item older = crate("Alpha's End Crate", daysAgo(120), daysAgo(60));
        Item newer = crate("Spooky '26 Crate", daysAgo(30), daysAgo(1));
        stubCrates(older, newer, sentinel);

        assertThat(itemAwardService.resolveGrantItem(sentinel)).isSameAs(newer);
    }

    @Test
    void skipsTheGrantWhenNoCrateQualifiesAtAll() {
        stubCrates(sentinel);

        itemAwardService.awardSystem(USER_ID, SENTINEL_ID, ItemSource.milestone, MILESTONE_ID, "reward");

        verify(userItemLinkRepository, never()).save(any());
    }

    @Test
    void grantsTheResolvedCrateWithTheOriginalAttribution() {
        Item only = crate("Spooky '26 Crate", daysAgo(30), inDays(10));
        stubCrates(only, sentinel);

        itemAwardService.awardSystem(USER_ID, SENTINEL_ID, ItemSource.milestone, MILESTONE_ID,
                "Completed milestone: Crate Expectations");

        ArgumentCaptor<UserItemLink> captor = ArgumentCaptor.forClass(UserItemLink.class);
        verify(userItemLinkRepository, times(1)).save(captor.capture());
        UserItemLink link = captor.getValue();
        assertThat(link.getItem()).isSameAs(only);
        assertThat(link.getSource()).isEqualTo(ItemSource.milestone);
        assertThat(link.getSourceId()).isEqualTo(MILESTONE_ID);
        assertThat(link.getReason()).isEqualTo("Completed milestone: Crate Expectations");
    }

    @Test
    void doesNotRegrantWhenTheSameSourceAlreadyPaidOutACrate() {
        Item only = crate("Spooky '26 Crate", daysAgo(30), inDays(10));
        stubCrates(only, sentinel);
        when(userItemLinkRepository.countByUser_IdAndItem_Type_KeyAndSourceAndSourceId(
                USER_ID, "crate", ItemSource.milestone, MILESTONE_ID)).thenReturn(1L);

        itemAwardService.awardSystem(USER_ID, SENTINEL_ID, ItemSource.milestone, MILESTONE_ID, "reward");

        verify(userItemLinkRepository, never()).save(any());
    }

    @Test
    void leavesNonSentinelItemsUntouched() {
        Item plain = crate("Spooky '26 Crate", daysAgo(30), inDays(10));

        assertThat(itemAwardService.resolveGrantItem(plain)).isSameAs(plain);
        verify(itemRepository, never()).findByType_KeyAndActiveTrueAndDeprecatedFalseAndVisibleTrue(any());
    }

    private void stubCrates(Item... crates) {
        when(itemRepository.findByType_KeyAndActiveTrueAndDeprecatedFalseAndVisibleTrue("crate"))
                .thenReturn(List.of(crates));
    }

    private static Item crate(String name, Instant createdAt, Instant obtainableUntil) {
        return Item.builder()
                .id(UUID.randomUUID())
                .type(CRATE_TYPE)
                .name(name)
                .stackable(false)
                .serialized(false)
                .active(true)
                .createdAt(createdAt)
                .obtainableUntil(obtainableUntil)
                .build();
    }

    private static Instant daysAgo(int days) {
        return Instant.now().minus(days, ChronoUnit.DAYS);
    }

    private static Instant inDays(int days) {
        return Instant.now().plus(days, ChronoUnit.DAYS);
    }
}
