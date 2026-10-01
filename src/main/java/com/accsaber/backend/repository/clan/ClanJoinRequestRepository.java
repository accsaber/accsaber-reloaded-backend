package com.accsaber.backend.repository.clan;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.clan.ClanJoinRequest;
import com.accsaber.backend.model.entity.clan.ClanJoinStatus;

public interface ClanJoinRequestRepository extends JpaRepository<ClanJoinRequest, UUID> {

    boolean existsByClan_IdAndUser_IdAndStatus(UUID clanId, Long userId, ClanJoinStatus status);

    @Query("""
            SELECT r FROM ClanJoinRequest r
            JOIN FETCH r.clan JOIN FETCH r.user JOIN FETCH r.createdBy LEFT JOIN FETCH r.resolvedBy
            WHERE r.id = :id
            """)
    Optional<ClanJoinRequest> findWithRefsById(@Param("id") UUID id);

    @Query(value = """
            SELECT r FROM ClanJoinRequest r
            JOIN FETCH r.clan JOIN FETCH r.user JOIN FETCH r.createdBy
            WHERE r.user.id = :userId
              AND r.status = com.accsaber.backend.model.entity.clan.ClanJoinStatus.pending
            ORDER BY r.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(r) FROM ClanJoinRequest r
            WHERE r.user.id = :userId
              AND r.status = com.accsaber.backend.model.entity.clan.ClanJoinStatus.pending
            """)
    Page<ClanJoinRequest> findPendingByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query(value = """
            SELECT r FROM ClanJoinRequest r
            JOIN FETCH r.clan JOIN FETCH r.user JOIN FETCH r.createdBy
            WHERE r.clan.id = :clanId
              AND r.status = com.accsaber.backend.model.entity.clan.ClanJoinStatus.pending
            ORDER BY r.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(r) FROM ClanJoinRequest r
            WHERE r.clan.id = :clanId
              AND r.status = com.accsaber.backend.model.entity.clan.ClanJoinStatus.pending
            """)
    Page<ClanJoinRequest> findPendingByClanId(@Param("clanId") UUID clanId, Pageable pageable);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE ClanJoinRequest r
            SET r.status = com.accsaber.backend.model.entity.clan.ClanJoinStatus.expired, r.resolvedAt = :now
            WHERE r.user.id = :userId
              AND r.status = com.accsaber.backend.model.entity.clan.ClanJoinStatus.pending
            """)
    int expirePendingForUser(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE ClanJoinRequest r
            SET r.status = com.accsaber.backend.model.entity.clan.ClanJoinStatus.expired, r.resolvedAt = :now
            WHERE r.clan.id = :clanId
              AND r.status = com.accsaber.backend.model.entity.clan.ClanJoinStatus.pending
            """)
    int expirePendingForClan(@Param("clanId") UUID clanId, @Param("now") Instant now);
}
