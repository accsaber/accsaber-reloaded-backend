package com.accsaber.backend.repository.clan;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.clan.ClanStandingEvent;

public interface ClanStandingEventRepository extends JpaRepository<ClanStandingEvent, UUID> {

    Page<ClanStandingEvent> findBySeason_IdAndClan_IdOrderByCreatedAtDesc(UUID seasonId, UUID clanId,
            Pageable pageable);

    @Modifying
    @Query(value = """
            INSERT INTO clan_standing_events (season_id, clan_id, amount, source, source_id)
            VALUES (:seasonId, :clanId, :amount, :source, :sourceId)
            ON CONFLICT (season_id, clan_id, source, source_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("seasonId") UUID seasonId, @Param("clanId") UUID clanId, @Param("amount") double amount,
            @Param("source") String source, @Param("sourceId") String sourceId);
}
