package com.accsaber.backend.model.dto.request.mission;

import java.math.BigDecimal;
import java.util.UUID;

import com.accsaber.backend.model.entity.mission.MissionPool;
import com.accsaber.backend.model.entity.mission.MissionType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class MissionTemplateRequest {

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    @NotBlank
    private String description;

    @NotNull
    private MissionType type;

    @NotNull
    private MissionPool pool;

    @NotNull
    @Positive
    private Integer weight;

    private boolean guaranteedDoable;

    private UUID xpCurveId;

    private UUID awardsItemId;

    private BigDecimal xpMultiplier;

    private BigDecimal bandEasy;
    private BigDecimal bandMedium;
    private BigDecimal bandHard;

    private Integer targetCountMin;
    private Integer targetCountMax;

    private Boolean active;
}
