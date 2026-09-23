package com.accsaber.backend.model.dto.response.map;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.entity.map.Difficulty;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ReweightRoundResponse {

    UUID id;
    Instant at;
    String categoryCode;
    String reason;
    int mapCount;
    int buffs;
    int nerfs;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    List<MapChange> maps;

    @Value
    @Builder
    public static class MapChange {
        UUID mapId;
        UUID mapDifficultyId;
        String songName;
        Difficulty difficulty;
        Double from;
        Double to;
    }
}
