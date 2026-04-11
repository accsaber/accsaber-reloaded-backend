package com.accsaber.backend.model.dto.response.player;

import java.time.Instant;

import lombok.Value;

@Value
public class RankingHistoryResponse {

    Integer ranking;
    Integer countryRanking;
    Instant recordedAt;
}
