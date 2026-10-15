package com.accsaber.backend.service.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.repository.milestone.LevelThresholdRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.milestone.LevelService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LevelRewardServiceTest {

        @Mock
        private UserRepository userRepository;
        @Mock
        private ItemRepository itemRepository;
        @Mock
        private LevelThresholdRepository levelThresholdRepository;
        @Mock
        private LevelService levelService;
        @Mock
        private LevelUpAwardService levelUpAwardService;

        private LevelRewardService service;

        @BeforeEach
        void setUp() {
                service = new LevelRewardService(userRepository, itemRepository,
                                levelThresholdRepository, levelService, levelUpAwardService);
                set("maxGrantsPerRun", 400);
                set("maxUsersPerRun", 2000);
                when(levelService.xpForLevel(anyInt())).thenReturn(100.0);
                when(levelThresholdRepository.findLevelsWithItemAwards()).thenReturn(List.of());
        }

        private void set(String field, int value) {
                try {
                        var f = LevelRewardService.class.getDeclaredField(field);
                        f.setAccessible(true);
                        f.set(service, value);
                } catch (Exception e) {
                        throw new RuntimeException(e);
                }
        }

        private Object[] event(String iso, double xp) {
                return new Object[] { Timestamp.from(Instant.parse(iso)), xp };
        }

        private List<Object[]> timeline(Object[]... events) {
                return List.of(events);
        }

        private void missing(List<Long> userIds) {
                when(userRepository.findUsersMissingLevelReward(anyInt(), anyDouble())).thenReturn(userIds);
        }

        @Nested
        class GrantOrder {

                @Test
                void grantsInTrueCrossingOrderNotUserIdOrder() {
                        when(itemRepository.findDistinctUnlockLevels()).thenReturn(List.of(10));
                        missing(List.of(1L, 2L));
                        when(userRepository.findXpTimeline(1L))
                                        .thenReturn(timeline(event("2026-05-01T00:00:00Z", 5000.0)));
                        when(userRepository.findXpTimeline(2L))
                                        .thenReturn(timeline(event("2020-01-01T00:00:00Z", 5000.0)));

                        service.grantMissingLevelRewards();

                        InOrder order = inOrder(levelUpAwardService);
                        order.verify(levelUpAwardService).grantLevelRewards(2L, 10);
                        order.verify(levelUpAwardService).grantLevelRewards(1L, 10);
                }

                @Test
                void grantsEachUsersLevelsInAscendingOrder() {
                        when(itemRepository.findDistinctUnlockLevels()).thenReturn(List.of(10, 20));
                        missing(List.of(7L));
                        when(userRepository.findXpTimeline(7L)).thenReturn(timeline(
                                        event("2021-01-01T00:00:00Z", 1200.0),
                                        event("2022-01-01T00:00:00Z", 1200.0)));

                        service.grantMissingLevelRewards();

                        InOrder order = inOrder(levelUpAwardService);
                        order.verify(levelUpAwardService).grantLevelRewards(7L, 10);
                        order.verify(levelUpAwardService).grantLevelRewards(7L, 20);
                }

                @Test
                void datesEachLevelByTheEventThatCrossedIt() {
                        when(itemRepository.findDistinctUnlockLevels()).thenReturn(List.of(10, 20));
                        missing(List.of(5L, 6L));
                        when(userRepository.findXpTimeline(5L)).thenReturn(timeline(
                                        event("2019-01-01T00:00:00Z", 1000.0),
                                        event("2026-01-01T00:00:00Z", 1000.0)));
                        when(userRepository.findXpTimeline(6L)).thenReturn(timeline(
                                        event("2021-01-01T00:00:00Z", 2000.0)));

                        service.grantMissingLevelRewards();

                        InOrder order = inOrder(levelUpAwardService);
                        order.verify(levelUpAwardService).grantLevelRewards(5L, 10);
                        order.verify(levelUpAwardService).grantLevelRewards(6L, 10);
                        order.verify(levelUpAwardService).grantLevelRewards(6L, 20);
                        order.verify(levelUpAwardService).grantLevelRewards(5L, 20);
                }
        }

        @Nested
        class Bounds {

                @Test
                void skipsEntirelyWhenMoreUsersThanTheCapAreMissingRewards() {
                        set("maxUsersPerRun", 2);
                        when(itemRepository.findDistinctUnlockLevels()).thenReturn(List.of(10));
                        missing(List.of(1L, 2L, 3L));

                        service.grantMissingLevelRewards();

                        verify(levelUpAwardService, never()).grantLevelRewards(anyLong(), anyInt());
                        verify(userRepository, never()).findXpTimeline(anyLong());
                }

                @Test
                void defersTheRemainderOnceTheGrantCapIsReached() {
                        set("maxGrantsPerRun", 1);
                        when(itemRepository.findDistinctUnlockLevels()).thenReturn(List.of(10));
                        missing(List.of(1L, 2L));
                        when(userRepository.findXpTimeline(1L))
                                        .thenReturn(timeline(event("2026-05-01T00:00:00Z", 5000.0)));
                        when(userRepository.findXpTimeline(2L))
                                        .thenReturn(timeline(event("2020-01-01T00:00:00Z", 5000.0)));

                        service.grantMissingLevelRewards();

                        verify(levelUpAwardService).grantLevelRewards(2L, 10);
                        verify(levelUpAwardService, never()).grantLevelRewards(1L, 10);
                }

                @Test
                void doesNothingWhenNoRewardLevelsExist() {
                        when(itemRepository.findDistinctUnlockLevels()).thenReturn(List.of());

                        service.grantMissingLevelRewards();

                        verify(userRepository, never()).findUsersMissingLevelReward(anyInt(), anyDouble());
                }

                @Test
                void doesNothingWhenNobodyIsMissingRewards() {
                        when(itemRepository.findDistinctUnlockLevels()).thenReturn(List.of(10));
                        missing(List.of());

                        service.grantMissingLevelRewards();

                        verify(levelUpAwardService, never()).grantLevelRewards(anyLong(), anyInt());
                }

                @Test
                void oneFailedGrantDoesNotStopTheRest() {
                        when(itemRepository.findDistinctUnlockLevels()).thenReturn(List.of(10));
                        missing(List.of(1L, 2L));
                        when(userRepository.findXpTimeline(1L))
                                        .thenReturn(timeline(event("2026-05-01T00:00:00Z", 5000.0)));
                        when(userRepository.findXpTimeline(2L))
                                        .thenReturn(timeline(event("2020-01-01T00:00:00Z", 5000.0)));
                        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                                        .when(levelUpAwardService).grantLevelRewards(2L, 10);

                        service.grantMissingLevelRewards();

                        verify(levelUpAwardService).grantLevelRewards(1L, 10);
                }
        }

        @Nested
        class Thresholds {

                @Test
                void usesCumulativeXpAcrossLevelsNotPerLevelCost() {
                        when(itemRepository.findDistinctUnlockLevels()).thenReturn(List.of(10, 20));
                        List<Double> captured = new ArrayList<>();
                        when(userRepository.findUsersMissingLevelReward(anyInt(), anyDouble()))
                                        .thenAnswer(inv -> {
                                                captured.add(inv.getArgument(1));
                                                return List.of();
                                        });

                        service.grantMissingLevelRewards();

                        assertThat(captured).containsExactly(1000.0, 2000.0);
                }

                @Test
                void mergesItemUnlockLevelsWithThresholdAwardLevels() {
                        when(itemRepository.findDistinctUnlockLevels()).thenReturn(List.of(20, 10));
                        when(levelThresholdRepository.findLevelsWithItemAwards()).thenReturn(List.of(15, 10));
                        List<Integer> levels = new ArrayList<>();
                        when(userRepository.findUsersMissingLevelReward(anyInt(), anyDouble()))
                                        .thenAnswer(inv -> {
                                                levels.add(inv.getArgument(0));
                                                return List.of();
                                        });

                        service.grantMissingLevelRewards();

                        assertThat(levels).containsExactly(10, 15, 20);
                }
        }
}
