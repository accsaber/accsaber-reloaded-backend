package com.accsaber.backend.service.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.accsaber.backend.model.dto.projection.SimulationScoreRow;
import com.accsaber.backend.model.dto.request.map.ComplexityRaterSpec;

public final class PayFairBoard {

    public static final Reading NONE = new Reading(0.0, 0.0, 0, 0);

    public record Reading(double complexity, double payRatio, int plays, int scores) {
    }

    private record Play(UUID difficultyId, double ap) {
    }

    private record Top(double ratio, int plays) {
    }

    private PayFairBoard() {
    }

    static Map<UUID, Reading> read(List<SimulationScoreRow> rows, Map<UUID, Double> shifts,
            Map<UUID, Double> complexities, ComplexityRaterSpec.PayFair settings) {
        Map<UUID, List<SimulationScoreRow>> byCategory = rows.stream()
                .filter(row -> row.maxScore() != null && row.maxScore() > 0 && row.score() != null && row.score() > 0)
                .collect(Collectors.groupingBy(SimulationScoreRow::categoryId));
        Map<UUID, Reading> result = new HashMap<>();
        byCategory.forEach((categoryId, categoryRows) -> result.putAll(
                category(categoryRows, shifts.get(categoryId), complexities, settings)));
        return result;
    }

    private static Map<UUID, Reading> category(List<SimulationScoreRow> rows, Double shift,
            Map<UUID, Double> complexities, ComplexityRaterSpec.PayFair settings) {
        Map<Long, List<Play>> byUser = new HashMap<>();
        for (SimulationScoreRow row : rows) {
            byUser.computeIfAbsent(row.userId(), k -> new ArrayList<>()).add(new Play(row.mapDifficultyId(), row.ap()));
        }
        byUser.values().forEach(plays -> plays.sort(Comparator.comparingDouble(Play::ap).reversed()));
        Map<UUID, List<SimulationScoreRow>> byMap = rows.stream()
                .collect(Collectors.groupingBy(SimulationScoreRow::mapDifficultyId));
        Map<UUID, Top> tops = new HashMap<>();
        byMap.forEach((mapId, mapRows) -> tops.put(mapId, top(mapRows, byUser, settings)));
        double anchor = median(tops.values().stream().filter(top -> top.plays() >= settings.getMinPlays())
                .map(Top::ratio).toList());
        Map<UUID, Reading> result = new HashMap<>();
        byMap.forEach((mapId, mapRows) -> {
            Top top = tops.get(mapId);
            Double complexity = complexities.get(mapId);
            if (top.plays() < settings.getMinPlays() || complexity == null || shift == null || anchor <= 0.0) {
                result.put(mapId, new Reading(0.0, 0.0, top.plays(), mapRows.size()));
                return;
            }
            double payRatio = top.ratio() / anchor;
            result.put(mapId, new Reading((complexity - shift) / payRatio + shift, payRatio, top.plays(), mapRows.size()));
        });
        return result;
    }

    private static Top top(List<SimulationScoreRow> mapRows, Map<Long, List<Play>> byUser, ComplexityRaterSpec.PayFair settings) {
        List<SimulationScoreRow> best = mapRows.stream()
                .sorted(Comparator.comparingDouble((SimulationScoreRow row) -> (double) row.score() / row.maxScore()).reversed())
                .toList();
        List<Double> ratios = new ArrayList<>();
        for (SimulationScoreRow row : best) {
            double usual = usual(byUser.get(row.userId()), row.mapDifficultyId(), settings);
            if (usual > 0.0) {
                ratios.add(row.ap() / usual);
            }
            if (ratios.size() == settings.getTopPlays()) {
                break;
            }
        }
        return new Top(median(ratios), ratios.size());
    }

    private static double usual(List<Play> plays, UUID skip, ComplexityRaterSpec.PayFair settings) {
        if (plays.size() < settings.getMinPlayerPlays()) {
            return 0.0;
        }
        List<Double> others = new ArrayList<>();
        for (Play play : plays) {
            if (!play.difficultyId().equals(skip)) {
                others.add(play.ap());
            }
            if (others.size() == settings.getUsualPlays()) {
                break;
            }
        }
        return median(others);
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        List<Double> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }
}
