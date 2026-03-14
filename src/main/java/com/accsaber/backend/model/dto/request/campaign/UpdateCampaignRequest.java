package com.accsaber.backend.model.dto.request.campaign;

import lombok.Data;

@Data
public class UpdateCampaignRequest {

    private String name;
    private String description;
    private String difficulty;
    private Boolean verified;
}
