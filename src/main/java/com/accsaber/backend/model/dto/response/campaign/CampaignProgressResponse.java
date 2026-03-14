package com.accsaber.backend.model.dto.response.campaign;

import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignProgressResponse {

    private UUID campaignId;
    private String campaignName;
    private int totalMaps;
    private int completedMaps;
    private List<CampaignMapProgressResponse> maps;
}
