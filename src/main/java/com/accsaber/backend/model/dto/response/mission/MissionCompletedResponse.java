package com.accsaber.backend.model.dto.response.mission;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MissionCompletedResponse {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String userName;
    private String userCountry;
    private String userAvatarUrl;
    private String userCdnAvatarUrl;
    private Instant completedAt;

    private UUID missionId;
    private UUID templateId;
    private String templateCode;
    private String templateName;
    private String templateDescription;
    private String type;
    private String pool;
    private String band;
    private UUID categoryId;
    private String categoryCode;
    private UUID targetMapDifficultyId;
    private BigDecimal xpAwarded;
    private UUID itemAwardedId;
}
