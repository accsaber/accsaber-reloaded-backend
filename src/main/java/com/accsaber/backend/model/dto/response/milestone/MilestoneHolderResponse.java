package com.accsaber.backend.model.dto.response.milestone;

import java.time.Instant;

import com.accsaber.backend.model.dto.response.common.PlayerRef;

public record MilestoneHolderResponse(PlayerRef player, Instant completedAt) {
}
