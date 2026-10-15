package com.accsaber.backend.model.dto.request.milestone;

import java.util.List;

import com.accsaber.backend.model.dto.request.OnCreate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import lombok.Data;

@Data
public class MilestoneSetRequest {

    @NotBlank(groups = OnCreate.class)
    private String title;

    private String description;

    private Double setBonusXp;

    @Valid
    private List<MilestoneRewardRequest> rewards;
}
