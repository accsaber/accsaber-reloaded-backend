package com.accsaber.backend.model.dto.response.clan;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.entity.clan.ClanAuditAction;
import com.accsaber.backend.model.entity.clan.ClanAuditEntry;

public record ClanAuditEntryResponse(
        UUID id,
        ClanAuditAction action,
        PlayerRef actor,
        PlayerRef target,
        Map<String, Object> details,
        Instant createdAt) {

    public static ClanAuditEntryResponse of(ClanAuditEntry entry) {
        return new ClanAuditEntryResponse(entry.getId(), entry.getAction(), PlayerRef.of(entry.getActor()),
                PlayerRef.of(entry.getTargetUser()), entry.getDetails(), entry.getCreatedAt());
    }
}
