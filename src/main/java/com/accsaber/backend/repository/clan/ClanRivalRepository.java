package com.accsaber.backend.repository.clan;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.clan.ClanRival;

public interface ClanRivalRepository extends JpaRepository<ClanRival, UUID> {

    Optional<ClanRival> findByClan_IdAndRivalClan_Id(UUID clanId, UUID rivalClanId);

    @Query("""
            SELECT COUNT(r) > 0 FROM ClanRival r
            WHERE r.active
              AND ((r.clan.id = :first AND r.rivalClan.id = :second)
                OR (r.clan.id = :second AND r.rivalClan.id = :first))
            """)
    boolean existsActiveBetween(@Param("first") UUID first, @Param("second") UUID second);

    @Query(value = """
            SELECT r FROM ClanRival r
            JOIN FETCH r.rivalClan c LEFT JOIN FETCH r.declaredBy
            WHERE r.clan.id = :clanId AND r.active AND c.active
            ORDER BY r.updatedAt DESC
            """,
            countQuery = """
            SELECT COUNT(r) FROM ClanRival r
            WHERE r.clan.id = :clanId AND r.active AND r.rivalClan.active
            """)
    Page<ClanRival> findDeclaredBy(@Param("clanId") UUID clanId, Pageable pageable);

    @Query(value = """
            SELECT r FROM ClanRival r
            JOIN FETCH r.clan c LEFT JOIN FETCH r.declaredBy
            WHERE r.rivalClan.id = :clanId AND r.active AND c.active
            ORDER BY r.updatedAt DESC
            """,
            countQuery = """
            SELECT COUNT(r) FROM ClanRival r
            WHERE r.rivalClan.id = :clanId AND r.active AND r.clan.active
            """)
    Page<ClanRival> findDeclaredAgainst(@Param("clanId") UUID clanId, Pageable pageable);
}
