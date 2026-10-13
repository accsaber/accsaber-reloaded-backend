package com.accsaber.backend.repository.clan.war;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.clan.war.ClanWarAid;
import com.accsaber.backend.model.entity.clan.war.ClanWarAidStatus;

public interface ClanWarAidRepository extends JpaRepository<ClanWarAid, UUID> {

    @Query("""
            SELECT a FROM ClanWarAid a
            JOIN FETCH a.war w JOIN FETCH w.attackerClan JOIN FETCH w.defenderClan
            JOIN FETCH a.clan JOIN FETCH a.requestedBy
            WHERE a.id = :id
            """)
    Optional<ClanWarAid> findWithRefsById(@Param("id") UUID id);

    @Query("""
            SELECT COUNT(a) > 0 FROM ClanWarAid a
            WHERE a.war.id = :warId AND a.clan.id = :clanId
              AND a.status = com.accsaber.backend.model.entity.clan.war.ClanWarAidStatus.pending
            """)
    boolean existsPending(@Param("warId") UUID warId, @Param("clanId") UUID clanId);

    @Query(value = """
            SELECT a FROM ClanWarAid a
            JOIN FETCH a.war w JOIN FETCH w.attackerClan JOIN FETCH w.defenderClan
            JOIN FETCH a.clan JOIN FETCH a.requestedBy
            WHERE (:warId IS NULL OR w.id = :warId)
              AND (:clanId IS NULL OR a.clan.id = :clanId OR w.attackerClan.id = :clanId OR w.defenderClan.id = :clanId)
              AND (:status IS NULL OR a.status = :status)
            ORDER BY a.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(a) FROM ClanWarAid a JOIN a.war w
            WHERE (:warId IS NULL OR w.id = :warId)
              AND (:clanId IS NULL OR a.clan.id = :clanId OR w.attackerClan.id = :clanId OR w.defenderClan.id = :clanId)
              AND (:status IS NULL OR a.status = :status)
            """)
    Page<ClanWarAid> findPage(@Param("warId") UUID warId, @Param("clanId") UUID clanId,
            @Param("status") ClanWarAidStatus status, Pageable pageable);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE ClanWarAid a SET a.status = com.accsaber.backend.model.entity.clan.war.ClanWarAidStatus.cancelled,
                a.resolvedAt = :now
            WHERE a.war.id = :warId AND a.status = com.accsaber.backend.model.entity.clan.war.ClanWarAidStatus.pending
            """)
    int cancelPendingForWar(@Param("warId") UUID warId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE ClanWarAid a SET a.status = com.accsaber.backend.model.entity.clan.war.ClanWarAidStatus.cancelled,
                a.resolvedAt = :now
            WHERE a.clan.id = :clanId AND a.status = com.accsaber.backend.model.entity.clan.war.ClanWarAidStatus.pending
            """)
    int cancelPendingForClan(@Param("clanId") UUID clanId, @Param("now") Instant now);
}
