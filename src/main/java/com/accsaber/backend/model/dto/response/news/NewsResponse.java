package com.accsaber.backend.model.dto.response.news;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.entity.news.NewsStatus;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class NewsResponse {

    UUID id;
    UUID staffUserId;
    String staffUsername;
    String title;
    String slug;
    String description;
    String content;
    String imageUrl;
    NewsStatus status;
    boolean pinned;
    UUID batchId;
    UUID campaignId;
    UUID milestoneSetId;
    UUID curveId;
    Instant publishedAt;
    boolean active;
    Instant createdAt;
    Instant updatedAt;
}
