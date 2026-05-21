package com.accsaber.backend.model.dto.response.item;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CrateOpenResponse {

    private UUID id;
    private ItemResponse crate;
    private UUID consumedLinkId;
    private UserItemResponse reward;
    private Instant rolledAt;
}
