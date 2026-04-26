package com.accsaber.backend.model.dto.request.news;

import java.util.UUID;

import com.accsaber.backend.model.entity.news.NewsStatus;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateNewsRequest {

    @Size(max = 255)
    private String title;

    @Size(max = 255)
    private String slug;

    private String description;

    private String content;

    private String imageUrl;

    private NewsStatus status;

    private Boolean pinned;

    private UUID batchId;

    private UUID campaignId;

    private UUID milestoneSetId;

    private UUID curveId;
}
