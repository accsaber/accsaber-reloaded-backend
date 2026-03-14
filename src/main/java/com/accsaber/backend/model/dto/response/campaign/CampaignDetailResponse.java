package com.accsaber.backend.model.dto.response.campaign;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignDetailResponse {

    private UUID id;
    private Long creatorId;
    private String creatorName;
    private String name;
    private String description;
    private String difficulty;
    private boolean verified;
    private int mapCount;
    private Instant createdAt;
    private List<CampaignMapResponse> maps;
}
