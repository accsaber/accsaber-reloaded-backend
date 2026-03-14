package com.accsaber.backend.model.dto.response.campaign;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignResponse {

    private UUID id;
    private Long creatorId;
    private String creatorName;
    private String name;
    private String description;
    private String difficulty;
    private boolean verified;
    private int mapCount;
    private Instant createdAt;
}
