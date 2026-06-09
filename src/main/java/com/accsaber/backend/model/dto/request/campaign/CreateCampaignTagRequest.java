package com.accsaber.backend.model.dto.request.campaign;

import java.util.UUID;

import com.accsaber.backend.model.entity.campaign.CampaignTagKind;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateCampaignTagRequest {

    @NotNull
    private CampaignTagKind kind;

    @NotBlank
    private String name;

    private UUID categoryId;
}
