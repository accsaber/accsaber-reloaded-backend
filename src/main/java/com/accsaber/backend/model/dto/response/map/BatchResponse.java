package com.accsaber.backend.model.dto.response.map;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.entity.map.BatchStatus;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BatchResponse {

    UUID id;
    String name;
    String description;
    BatchStatus status;
    List<MapDifficultyResponse> difficulties;
    Instant releasedAt;
    Instant createdAt;
    Instant updatedAt;
}
