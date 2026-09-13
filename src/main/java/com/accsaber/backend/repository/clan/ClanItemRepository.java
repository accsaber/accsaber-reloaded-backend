package com.accsaber.backend.repository.clan;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.clan.ClanItem;

public interface ClanItemRepository extends JpaRepository<ClanItem, ClanItem.Key> {

    boolean existsByClan_IdAndItem_Id(UUID clanId, UUID itemId);

    @Query(value = """
            SELECT ci FROM ClanItem ci JOIN FETCH ci.item
            WHERE ci.clan.id = :clanId
            ORDER BY ci.acquiredAt DESC
            """,
            countQuery = "SELECT COUNT(ci) FROM ClanItem ci WHERE ci.clan.id = :clanId")
    Page<ClanItem> findPageByClanId(@Param("clanId") UUID clanId, Pageable pageable);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO clan_items (clan_id, item_id, source, source_id)
            SELECT :clanId, li.item_id, 'level', CAST(li.level AS text)
            FROM clan_level_items li
            WHERE li.level > :fromLevel AND li.level <= :toLevel
            ON CONFLICT (clan_id, item_id) DO NOTHING
            """, nativeQuery = true)
    int grantLevelItems(@Param("clanId") UUID clanId, @Param("fromLevel") int fromLevel,
            @Param("toLevel") int toLevel);
}
