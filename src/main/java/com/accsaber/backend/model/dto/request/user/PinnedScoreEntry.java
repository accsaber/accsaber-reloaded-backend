package com.accsaber.backend.model.dto.request.user;

import java.util.UUID;

public record PinnedScoreEntry(UUID scoreId, int displayOrder, String comment) {
}
