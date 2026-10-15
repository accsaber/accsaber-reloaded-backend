package com.accsaber.backend.model.dto.response.clan;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.entity.clan.ClanAuditAction;

public record ClanAuditEntryResponse(
        UUID id,
        ClanAuditAction action,
        PlayerRef actor,
        PlayerRef target,
        Map<String, Object> details,
        Instant createdAt) {
}
