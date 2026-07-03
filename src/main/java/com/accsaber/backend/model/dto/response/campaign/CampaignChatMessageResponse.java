package com.accsaber.backend.model.dto.response.campaign;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignChatMessageResponse {

    private UUID id;
    private UUID campaignId;
    private Long authorId;
    private String authorName;
    private String authorAvatarUrl;
    private String authorCdnAvatarUrl;
    private String content;
    private Instant createdAt;
}
