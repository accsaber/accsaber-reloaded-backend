package com.accsaber.backend.service.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import com.accsaber.backend.model.entity.item.CrateContent;
import com.accsaber.backend.model.entity.item.CrateUnusualEffect;
import com.accsaber.backend.model.entity.item.UnusualEffect;

class CrateServiceTest {

    @Test
    void rollIsDeterministicForAGivenSeed() {
        List<CrateContent> contents = List.of(weighted(1), weighted(1), weighted(1));

        CrateContent first = CrateService.roll(contents, new SplittableRandom(42L));
        CrateContent second = CrateService.roll(contents, new SplittableRandom(42L));

        assertThat(first).isSameAs(second);
    }

    @Test
    void rollRespectsWeightBoundaries() {
        CrateContent light = weighted(1);
        CrateContent heavy = weighted(9);
        List<CrateContent> contents = List.of(light, heavy);

        int heavyHits = 0;
        for (long seed = 0; seed < 1000; seed++) {
            if (CrateService.roll(contents, new SplittableRandom(seed)) == heavy) {
                heavyHits++;
            }
        }

        assertThat(heavyHits).isBetween(850, 950);
    }

    private static CrateContent weighted(int weight) {
        return CrateContent.builder().dropWeight(weight).build();
    }

    @Test
    void pickUnusualEffectReturnsNullWhenNoneAttached() {
        assertThat(CrateService.pickUnusualEffect(List.of(), new SplittableRandom(1L))).isNull();
    }

    @Test
    void pickUnusualEffectSelectsByIndexInKeyOrder() {
        List<CrateUnusualEffect> attached = List.of(attach("fiery"), attach("angelic"));

        assertThat(CrateService.pickUnusualEffect(attached, fixedInt(0)).getKey()).isEqualTo("angelic");
        assertThat(CrateService.pickUnusualEffect(attached, fixedInt(1)).getKey()).isEqualTo("fiery");
    }

    private static CrateUnusualEffect attach(String key) {
        return CrateUnusualEffect.builder()
                .effect(UnusualEffect.builder().id(UUID.randomUUID()).key(key).name(key).build())
                .build();
    }

    private static RandomGenerator fixedInt(int value) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0L;
            }

            @Override
            public int nextInt(int bound) {
                return value;
            }
        };
    }
}
