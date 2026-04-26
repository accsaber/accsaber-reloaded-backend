package com.accsaber.backend.model.dto.response.news;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PublicNewsResponse {

    UUID id;
    String authorName;
    String title;
    String slug;
    String description;
    String content;
    String imageUrl;
    boolean pinned;
    UUID batchId;
    UUID campaignId;
    UUID milestoneSetId;
    UUID curveId;
    Instant publishedAt;
}
