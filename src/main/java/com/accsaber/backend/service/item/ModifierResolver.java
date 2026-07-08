package com.accsaber.backend.service.item;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Component;

import com.accsaber.backend.model.entity.item.CrateModifier;
import com.accsaber.backend.model.entity.item.ItemModifier;

@Component
public class ModifierResolver {

    public static final long FOUNDERS_THRESHOLD = 5L;

    public Set<String> resolveFounders(long serial) {
        Set<String> layers = new LinkedHashSet<>();
        if (serial > 0 && serial <= FOUNDERS_THRESHOLD) {
            layers.add(ItemModifier.FOUNDERS);
        }
        return layers;
    }

    public boolean inSeason(ItemModifier modifier, LocalDate today) {
        String start = modifier.getSeasonStart();
        String end = modifier.getSeasonEnd();
        if (start == null || end == null) {
            return true;
        }
        MonthDay from = parseSeasonBound(start);
        MonthDay to = parseSeasonBound(end);
        MonthDay md = MonthDay.from(today);
        if (!from.isAfter(to)) {
            return !md.isBefore(from) && !md.isAfter(to);
        }
        return !md.isBefore(from) || !md.isAfter(to);
    }

    static Set<ItemModifier> rollModifiers(List<CrateModifier> attached,
            Collection<ItemModifier> globalCandidates, RandomGenerator rng) {
        Set<ItemModifier> winners = new LinkedHashSet<>();
        attached.stream()
                .sorted(Comparator.comparing(cm -> cm.getModifier().getKey()))
                .forEach(cm -> {
                    if (rng.nextDouble() < cm.getDropChance().doubleValue()) {
                        winners.add(cm.getModifier());
                    }
                });
        globalCandidates.stream()
                .sorted(Comparator.comparing(ItemModifier::getKey))
                .forEach(m -> {
                    if (rng.nextDouble() < m.getGlobalDropChance().doubleValue()) {
                        winners.add(m);
                    }
                });
        return winners;
    }

    public static MonthDay parseSeasonBound(String value) {
        String[] parts = value.split("-");
        if (parts.length != 2) {
            throw new IllegalArgumentException("season bound must be in MM-DD format");
        }
        return MonthDay.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }
}
