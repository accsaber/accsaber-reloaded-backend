package com.accsaber.backend.service.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import com.accsaber.backend.model.entity.item.CrateModifier;
import com.accsaber.backend.model.entity.item.ItemModifier;

class ModifierResolverTest {

    private final ModifierResolver resolver = new ModifierResolver();

    @Test
    void resolveFoundersWithinThreshold() {
        assertThat(resolver.resolveFounders(1)).containsExactly(ItemModifier.FOUNDERS);
        assertThat(resolver.resolveFounders(ModifierResolver.FOUNDERS_THRESHOLD))
                .containsExactly(ItemModifier.FOUNDERS);
    }

    @Test
    void resolveFoundersOutsideThreshold() {
        assertThat(resolver.resolveFounders(0)).isEmpty();
        assertThat(resolver.resolveFounders(ModifierResolver.FOUNDERS_THRESHOLD + 1)).isEmpty();
    }

    @Test
    void inSeasonHonoursInclusiveBounds() {
        ItemModifier haunted = seasonal("haunted", "10-25", "11-01");
        assertThat(resolver.inSeason(haunted, LocalDate.of(2026, 10, 25))).isTrue();
        assertThat(resolver.inSeason(haunted, LocalDate.of(2026, 11, 1))).isTrue();
        assertThat(resolver.inSeason(haunted, LocalDate.of(2026, 10, 24))).isFalse();
        assertThat(resolver.inSeason(haunted, LocalDate.of(2026, 11, 2))).isFalse();
    }

    @Test
    void inSeasonSupportsYearWrapAround() {
        ItemModifier newYears = seasonal("newyears", "12-20", "01-05");
        assertThat(resolver.inSeason(newYears, LocalDate.of(2026, 12, 31))).isTrue();
        assertThat(resolver.inSeason(newYears, LocalDate.of(2026, 1, 3))).isTrue();
        assertThat(resolver.inSeason(newYears, LocalDate.of(2026, 6, 1))).isFalse();
    }

    @Test
    void inSeasonTrueWhenNoWindowConfigured() {
        ItemModifier always = seasonal("always", null, null);
        assertThat(resolver.inSeason(always, LocalDate.of(2026, 3, 14))).isTrue();
    }

    @Test
    void rollModifiersLayersAttachedAndGlobalWinners() {
        ItemModifier strange = globalModifier("strange", null);
        ItemModifier haunted = globalModifier("haunted", "0.5");
        List<CrateModifier> attached = List.of(attach(strange, "0.5"));
        RandomGenerator rng = queued(0.1, 0.1);

        Set<ItemModifier> winners = ModifierResolver.rollModifiers(attached, List.of(haunted), rng);

        assertThat(winners).containsExactlyInAnyOrder(strange, haunted);
    }

    @Test
    void rollModifiersDropsLosingRolls() {
        ItemModifier strange = globalModifier("strange", null);
        ItemModifier haunted = globalModifier("haunted", "0.5");
        List<CrateModifier> attached = List.of(attach(strange, "0.5"));
        RandomGenerator rng = queued(0.1, 0.9);

        Set<ItemModifier> winners = ModifierResolver.rollModifiers(attached, List.of(haunted), rng);

        assertThat(winners).containsExactly(strange);
    }

    @Test
    void rollModifiersCombinesMultipleAttached() {
        ItemModifier strange = globalModifier("strange", null);
        ItemModifier unusual = globalModifier("unusual", null);
        List<CrateModifier> attached = List.of(attach(unusual, "0.5"), attach(strange, "0.5"));
        RandomGenerator rng = queued(0.1, 0.1);

        Set<ItemModifier> winners = ModifierResolver.rollModifiers(attached, List.of(), rng);

        assertThat(winners).containsExactlyInAnyOrder(strange, unusual);
    }

    private static ItemModifier seasonal(String key, String start, String end) {
        return ItemModifier.builder()
                .id(UUID.randomUUID())
                .key(key)
                .seasonStart(start)
                .seasonEnd(end)
                .build();
    }

    private static ItemModifier globalModifier(String key, String globalDropChance) {
        return ItemModifier.builder()
                .id(UUID.randomUUID())
                .key(key)
                .globalDropChance(globalDropChance == null ? null : new BigDecimal(globalDropChance))
                .build();
    }

    private static CrateModifier attach(ItemModifier modifier, String dropChance) {
        return CrateModifier.builder()
                .modifier(modifier)
                .dropChance(new BigDecimal(dropChance))
                .build();
    }

    private static RandomGenerator queued(double... values) {
        Deque<Double> queue = new ArrayDeque<>();
        for (double v : values) {
            queue.add(v);
        }
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0L;
            }

            @Override
            public double nextDouble() {
                return queue.pollFirst();
            }
        };
    }
}
