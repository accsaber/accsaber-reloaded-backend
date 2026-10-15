package com.accsaber.backend.model.dto.response.map;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.entity.map.BatchStatus;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BatchResponse<D> {

    UUID id;
    String name;
    String description;
    BatchStatus status;
    List<D> difficulties;
    Instant releasedAt;
    Instant createdAt;
    Instant updatedAt;
}
