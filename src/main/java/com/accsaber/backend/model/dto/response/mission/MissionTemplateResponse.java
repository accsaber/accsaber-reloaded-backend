package com.accsaber.backend.model.dto.response.mission;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.dto.EventMissionTargets;
import com.accsaber.backend.model.entity.mission.MissionTemplate;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MissionTemplateResponse {

    private UUID id;
    private String code;
    private String name;
    private String description;
    private String type;
    private String pool;
    private Integer weight;
    private boolean guaranteedDoable;
    private UUID xpCurveId;
    private UUID awardsItemId;
    private String awardsItemName;
    private BigDecimal xpMultiplier;
    private BigDecimal bandEasy;
    private BigDecimal bandMedium;
    private BigDecimal bandHard;
    private Integer targetCountMin;
    private Integer targetCountMax;
    private boolean active;

    private UUID eventId;
    private Instant unlocksAt;
    private Instant completableUntil;
    private Boolean repeatable;
    private Integer maxCompletions;
    private Integer fixedXp;
    private EventMissionTargets targets;

    public static MissionTemplateResponse from(MissionTemplate t) {
        return MissionTemplateResponse.builder()
                .id(t.getId())
                .code(t.getCode())
                .name(t.getName())
                .description(t.getDescription())
                .type(t.getType().name())
                .pool(t.getPool().name())
                .weight(t.getWeight())
                .guaranteedDoable(t.isGuaranteedDoable())
                .xpCurveId(t.getXpCurve() != null ? t.getXpCurve().getId() : null)
                .awardsItemId(t.getAwardsItem() != null ? t.getAwardsItem().getId() : null)
                .awardsItemName(t.getAwardsItem() != null ? t.getAwardsItem().getName() : null)
                .xpMultiplier(t.getXpMultiplier())
                .bandEasy(t.getBandEasy())
                .bandMedium(t.getBandMedium())
                .bandHard(t.getBandHard())
                .targetCountMin(t.getTargetCountMin())
                .targetCountMax(t.getTargetCountMax())
                .active(t.isActive())
                .eventId(t.getEvent() != null ? t.getEvent().getId() : null)
                .unlocksAt(t.getUnlocksAt())
                .completableUntil(t.getCompletableUntil())
                .repeatable(t.getEvent() != null ? t.isRepeatable() : null)
                .maxCompletions(t.getMaxCompletions())
                .fixedXp(t.getFixedXp())
                .targets(t.getEventTargets())
                .build();
    }
}
