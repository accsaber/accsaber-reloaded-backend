package com.accsaber.backend.model.dto.response.map;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.accsaber.backend.model.dto.response.common.MapDifficultyRef;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ReweightDayResponse {

    LocalDate day;
    Instant at;
    List<String> categoryCodes;
    String reason;
    int mapCount;
    int buffs;
    int nerfs;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    List<MapChange> maps;

    @Value
    @Builder
    public static class MapChange {
        MapDifficultyRef map;
        Double from;
        Double to;
    }
}
