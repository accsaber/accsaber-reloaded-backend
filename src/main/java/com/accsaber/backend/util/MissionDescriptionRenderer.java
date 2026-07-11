package com.accsaber.backend.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.accsaber.backend.model.entity.map.MapDifficulty;

public final class MissionDescriptionRenderer {

    private MissionDescriptionRenderer() {
    }

    public record Values(
            Integer count,
            Integer xp,
            BigDecimal acc,
            BigDecimal ap,
            Integer score,
            BigDecimal thresholdAp,
            Integer streak,
            String mapName,
            String playerName,
            String categoryName) {
    }

    public static String render(String template, Values v) {
        if (template == null || template.isBlank())
            return template;
        String out = template;
        if (out.contains("{count}"))
            out = out.replace("{count}", v.count() != null ? v.count().toString() : "?");
        if (out.contains("{xp}"))
            out = out.replace("{xp}", v.xp() != null ? v.xp().toString() : "?");
        if (out.contains("{acc}"))
            out = out.replace("{acc}", formatAcc(v.acc()));
        if (out.contains("{ap}"))
            out = out.replace("{ap}", formatAp(v.ap()));
        if (out.contains("{score}"))
            out = out.replace("{score}", v.score() != null ? v.score().toString() : "?");
        if (out.contains("{threshold}"))
            out = out.replace("{threshold}", formatAp(v.thresholdAp()));
        if (out.contains("{streak}"))
            out = out.replace("{streak}", v.streak() != null ? v.streak().toString() : "?");
        if (out.contains("{map}"))
            out = out.replace("{map}", v.mapName() != null ? v.mapName() : "a ranked map");
        if (out.contains("{player}"))
            out = out.replace("{player}", v.playerName() != null ? v.playerName() : "another player");
        if (out.contains("{category}"))
            out = out.replace("{category}", v.categoryName() != null ? v.categoryName() : "any category");
        return out;
    }

    public static String formatMap(MapDifficulty difficulty) {
        if (difficulty == null || difficulty.getMap() == null)
            return null;
        String song = difficulty.getMap().getSongName();
        String diff = difficulty.getDifficulty() != null ? difficulty.getDifficulty().name() : null;
        return diff != null ? song + " (" + diff + ")" : song;
    }

    private static String formatAcc(BigDecimal acc) {
        if (acc == null)
            return "?";
        return acc.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP) + "%";
    }

    private static String formatAp(BigDecimal ap) {
        if (ap == null)
            return "?";
        return ap.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }
}
