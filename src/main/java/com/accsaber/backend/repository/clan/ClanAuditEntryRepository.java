package com.accsaber.backend.repository.clan;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.clan.ClanAuditEntry;

public interface ClanAuditEntryRepository extends JpaRepository<ClanAuditEntry, UUID> {

    @Query(value = """
            SELECT a FROM ClanAuditEntry a
            LEFT JOIN FETCH a.actor LEFT JOIN FETCH a.targetUser
            WHERE a.clan.id = :clanId
            ORDER BY a.createdAt DESC
            """,
            countQuery = "SELECT COUNT(a) FROM ClanAuditEntry a WHERE a.clan.id = :clanId")
    Page<ClanAuditEntry> findPageByClanId(@Param("clanId") UUID clanId, Pageable pageable);
}
