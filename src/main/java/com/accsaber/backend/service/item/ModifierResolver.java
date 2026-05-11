package com.accsaber.backend.service.item;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;

import com.accsaber.backend.model.entity.item.ItemModifier;

@Component
public class ModifierResolver {

    public static final long FOUNDERS_THRESHOLD = 5L;
    public static final double SEASONAL_DROP_CHANCE = 0.05;

    private static final MonthDay HALLOWEEN_FROM = MonthDay.of(10, 25);
    private static final MonthDay HALLOWEEN_TO = MonthDay.of(11, 1);
    private static final MonthDay CHRISTMAS_FROM = MonthDay.of(12, 20);
    private static final MonthDay CHRISTMAS_TO = MonthDay.of(12, 31);

    public Set<String> resolveAutoLayers(long serial, LocalDate today) {
        Set<String> layers = new LinkedHashSet<>();
        if (serial > 0 && serial <= FOUNDERS_THRESHOLD) {
            layers.add(ItemModifier.FOUNDERS);
        }
        MonthDay md = MonthDay.from(today);
        if (within(md, HALLOWEEN_FROM, HALLOWEEN_TO) && roll()) {
            layers.add(ItemModifier.HAUNTED);
        }
        if (within(md, CHRISTMAS_FROM, CHRISTMAS_TO) && roll()) {
            layers.add(ItemModifier.JOLLY);
        }
        return layers;
    }

    public Set<String> resolveAutoLayers(long serial) {
        return resolveAutoLayers(serial, LocalDate.now(ZoneId.systemDefault()));
    }

    private boolean roll() {
        return ThreadLocalRandom.current().nextDouble() < SEASONAL_DROP_CHANCE;
    }

    private boolean within(MonthDay value, MonthDay from, MonthDay to) {
        return !value.isBefore(from) && !value.isAfter(to);
    }
}
