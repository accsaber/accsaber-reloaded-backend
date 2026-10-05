package com.accsaber.backend.model.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class EventMissionTargetsTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void deserializesSeedShapeIncludingInstantAndBoolean() throws Exception {
        String json = """
                {"count":5,"rankedBefore":"2023-01-01T00:00:00Z","curatedOnly":true,"ap":5,"requirePass":true}
                """;

        EventMissionTargets targets = mapper.readValue(json, EventMissionTargets.class);

        assertThat(targets.count()).isEqualTo(5);
        assertThat(targets.rankedBefore()).isEqualTo(Instant.parse("2023-01-01T00:00:00Z"));
        assertThat(targets.curatedOnly()).isTrue();
        assertThat(targets.ap()).isEqualByComparingTo(5.0);
        assertThat(targets.requirePass()).isTrue();
    }

    @Test
    void roundTripsThroughJson() throws Exception {
        EventMissionTargets original = new EventMissionTargets(UUID.randomUUID(), null, "76561198000000001",
                null, 5.0, null, 10, null, null, null,
                Instant.parse("2023-01-01T00:00:00Z"), true, true, 5);

        EventMissionTargets round = mapper.readValue(mapper.writeValueAsString(original),
                EventMissionTargets.class);

        assertThat(round.rankedBefore()).isEqualTo(original.rankedBefore());
        assertThat(round.curatedOnly()).isEqualTo(original.curatedOnly());
        assertThat(round.playerIdAsLong()).isEqualTo(76561198000000001L);
        assertThat(round.categoryId()).isEqualTo(original.categoryId());
        assertThat(round.requirePass()).isEqualTo(original.requirePass());
        assertThat(round.maxPerUser()).isEqualTo(original.maxPerUser());
    }
}
