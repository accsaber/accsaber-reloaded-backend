package com.accsaber.backend.model.dto.response.milestone;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class MilestoneSetGroupResponse {

    private UUID id;
    private String name;
    private String description;
    private Instant createdAt;
    private List<MilestoneSetLinkResponse> links;
}
