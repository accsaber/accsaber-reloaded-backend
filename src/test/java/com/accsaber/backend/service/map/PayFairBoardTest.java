package com.accsaber.backend.service.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.accsaber.backend.model.dto.projection.SimulationScoreRow;
import com.accsaber.backend.model.dto.request.map.ComplexityRaterSpec;

class PayFairBoardTest {

    private final UUID category = UUID.randomUUID();
    private final UUID usualA = UUID.randomUUID();
    private final UUID usualB = UUID.randomUUID();
    private final UUID generous = UUID.randomUUID();

    private ComplexityRaterSpec.PayFair settings(int minPlays) {
        ComplexityRaterSpec.PayFair settings = new ComplexityRaterSpec.PayFair();
        settings.setMinPlayerPlays(3);
        settings.setUsualPlays(2);
        settings.setTopPlays(10);
        settings.setMinPlays(minPlays);
        return settings;
    }

    private List<SimulationScoreRow> rows() {
        List<SimulationScoreRow> rows = new ArrayList<>();
        for (long player = 1; player <= 3; player++) {
            rows.add(new SimulationScoreRow(player, usualA, category, 900_000, 1_000_000, 100.0 * player));
            rows.add(new SimulationScoreRow(player, usualB, category, 900_000, 1_000_000, 100.0 * player));
            rows.add(new SimulationScoreRow(player, generous, category, 900_000, 1_000_000, 110.0 * player));
        }
        rows.add(new SimulationScoreRow(9L, usualA, category, 990_000, 1_000_000, 5000.0));
        return rows;
    }

    @Test
    void aMapThatPaysItsBestPlaysMoreThanTheirUsualComesDown() {
        Map<UUID, Double> live = Map.of(usualA, 10.0, usualB, 10.0, generous, 10.0);

        Map<UUID, PayFairBoard.Reading> readings = PayFairBoard.read(rows(), Map.of(category, -18.0), live, settings(3));

        double generousRatio = 1.1 / (100.0 / 105.0);
        assertThat(readings.get(usualA).complexity()).isCloseTo(10.0, within(1e-9));
        assertThat(readings.get(usualA).payRatio()).isCloseTo(1.0, within(1e-9));
        assertThat(readings.get(generous).payRatio()).isCloseTo(generousRatio, within(1e-9));
        assertThat(readings.get(generous).complexity()).isCloseTo(28.0 / generousRatio - 18.0, within(1e-9));
        assertThat(readings.get(usualA).plays()).isEqualTo(3);
        assertThat(readings.get(usualA).scores()).isEqualTo(4);
    }

    @Test
    void aBoardWithTooFewPlayersWhoHaveAUsualGivesNoReading() {
        Map<UUID, Double> live = Map.of(usualA, 10.0, usualB, 10.0, generous, 10.0);

        Map<UUID, PayFairBoard.Reading> readings = PayFairBoard.read(rows(), Map.of(category, -18.0), live, settings(4));

        assertThat(readings.get(generous).plays()).isEqualTo(3);
        assertThat(readings.get(generous).payRatio()).isZero();
        assertThat(readings.get(generous).complexity()).isZero();
    }
}
