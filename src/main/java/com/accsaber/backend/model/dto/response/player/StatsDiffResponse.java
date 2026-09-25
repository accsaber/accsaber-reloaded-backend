package com.accsaber.backend.model.dto.response.player;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StatsDiffResponse {

    private UUID categoryId;
    private Double apDiff;
    private Double scoreXpDiff;
    private Double milestoneXpDiff;
    private Double milestoneSetBonusXpDiff;
    private Double missionXpDiff;
    private Double campaignXpDiff;
    private Double eventXpDiff;
    private Double averageAccDiff;
    private Double averageApDiff;
    private Integer rankingDiff;
    private Integer countryRankingDiff;
    private Integer rankedPlaysDiff;
    private Instant from;
    private Instant to;
}
