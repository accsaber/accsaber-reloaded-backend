package com.accsaber.backend.repository.item;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemRarity;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.item.UserItemLink;

import jakarta.persistence.LockModeType;

public interface UserItemLinkRepository extends JpaRepository<UserItemLink, UUID> {

        List<UserItemLink> findByUser_IdAndEscrowedFalse(Long userId);

        List<UserItemLink> findByUser_IdAndItem_Type_KeyAndEscrowedFalse(Long userId, String typeKey);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("""
                        SELECT l FROM UserItemLink l
                        WHERE l.user.id = :userId AND l.item.id = :itemId AND l.escrowed = false
                        ORDER BY l.id
                        """)
        List<UserItemLink> findStacksForUpdate(@Param("userId") Long userId, @Param("itemId") UUID itemId);

        @Query(value = """
                        SELECT i, l
                        FROM Item i
                        JOIN i.type t
                        LEFT JOIN t.parentType pt
                        LEFT JOIN UserItemLink l
                                ON l.item = i AND l.user.id = :userId AND l.escrowed = FALSE
                        LEFT JOIN UserItemCrateSource cs
                                ON cs.sourceId = l.sourceId
                                AND l.source = com.accsaber.backend.model.entity.item.ItemSource.crate_drop
                        LEFT JOIN cs.crateItem ci
                        WHERE (l.id IS NOT NULL OR (:includeUnowned = TRUE AND i.visible = TRUE AND i.active = TRUE))
                        AND (:typeKeys IS NULL OR t.key IN :typeKeys OR pt.key IN :typeKeys)
                        AND (:rarities IS NULL OR i.rarity IN :rarities)
                        AND (:modifierKeys IS NULL OR EXISTS (
                                SELECT m FROM l.modifiers m WHERE m.key IN :modifierKeys))
                        AND (:tradeable IS NULL OR i.tradeable = :tradeable)
                        AND (:sources IS NULL OR l.source IN :sources)
                        AND (:crateItemIds IS NULL OR ci.id IN :crateItemIds)
                        AND (:deprecated IS NULL OR i.deprecated = :deprecated)
                        AND (CAST(:search AS string) IS NULL
                                OR LOWER(i.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
                        """, countQuery = """
                        SELECT COUNT(i)
                        FROM Item i
                        JOIN i.type t
                        LEFT JOIN t.parentType pt
                        LEFT JOIN UserItemLink l
                                ON l.item = i AND l.user.id = :userId AND l.escrowed = FALSE
                        LEFT JOIN UserItemCrateSource cs
                                ON cs.sourceId = l.sourceId
                                AND l.source = com.accsaber.backend.model.entity.item.ItemSource.crate_drop
                        LEFT JOIN cs.crateItem ci
                        WHERE (l.id IS NOT NULL OR (:includeUnowned = TRUE AND i.visible = TRUE AND i.active = TRUE))
                        AND (:typeKeys IS NULL OR t.key IN :typeKeys OR pt.key IN :typeKeys)
                        AND (:rarities IS NULL OR i.rarity IN :rarities)
                        AND (:modifierKeys IS NULL OR EXISTS (
                                SELECT m FROM l.modifiers m WHERE m.key IN :modifierKeys))
                        AND (:tradeable IS NULL OR i.tradeable = :tradeable)
                        AND (:sources IS NULL OR l.source IN :sources)
                        AND (:crateItemIds IS NULL OR ci.id IN :crateItemIds)
                        AND (:deprecated IS NULL OR i.deprecated = :deprecated)
                        AND (CAST(:search AS string) IS NULL
                                OR LOWER(i.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
                        """)
        Page<Object[]> findInventoryFiltered(
                        @Param("userId") Long userId,
                        @Param("includeUnowned") boolean includeUnowned,
                        @Param("typeKeys") Collection<String> typeKeys,
                        @Param("rarities") Collection<ItemRarity> rarities,
                        @Param("modifierKeys") Collection<String> modifierKeys,
                        @Param("tradeable") Boolean tradeable,
                        @Param("sources") Collection<ItemSource> sources,
                        @Param("crateItemIds") Collection<UUID> crateItemIds,
                        @Param("deprecated") Boolean deprecated,
                        @Param("search") String search,
                        Pageable pageable);

        boolean existsByUser_IdAndItem_Id(Long userId, UUID itemId);

        long countByUser_IdAndItem_IdAndSourceAndSourceId(Long userId, UUID itemId, ItemSource source,
                        String sourceId);

        long countByUser_IdAndItem_Type_KeyAndSourceAndSourceId(Long userId, String typeKey, ItemSource source,
                        String sourceId);

        @Query("""
                        SELECT DISTINCT l.user.id FROM UserItemLink l
                        WHERE l.item.id = :itemId AND l.source = :source AND l.sourceId = :sourceId
                        """)
        List<Long> findUserIdsByItemAndSource(@Param("itemId") UUID itemId,
                        @Param("source") ItemSource source, @Param("sourceId") String sourceId);

        @Query("""
                        SELECT l FROM UserItemLink l
                        JOIN FETCH l.item i
                        JOIN FETCH i.type t
                        WHERE l.user.id = :userId AND t.key IN :typeKeys AND l.escrowed = FALSE
                        """)
        List<UserItemLink> findOwnedByTypeKeys(
                        @Param("userId") Long userId,
                        @Param("typeKeys") Collection<String> typeKeys);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT l FROM UserItemLink l WHERE l.id = :id")
        Optional<UserItemLink> findByIdForUpdate(@Param("id") UUID id);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT l FROM UserItemLink l WHERE l.id IN :ids ORDER BY l.id")
        List<UserItemLink> findAllByIdForUpdate(@Param("ids") Collection<UUID> ids);

        @Query("""
                        SELECT DISTINCT ci FROM UserItemLink l
                        JOIN UserItemCrateSource cs
                                ON cs.sourceId = l.sourceId
                                AND l.source = com.accsaber.backend.model.entity.item.ItemSource.crate_drop
                        JOIN cs.crateItem ci
                        JOIN FETCH ci.type
                        WHERE l.user.id = :userId AND l.escrowed = FALSE
                        ORDER BY ci.name
                        """)
        List<Item> findInventoryCrates(@Param("userId") Long userId);

        @Modifying
        @Query(value = """
                        INSERT INTO user_item_link_modifiers (user_item_link_id, modifier_id)
                        SELECT l.id, :modifierId
                        FROM user_item_links l
                        WHERE l.item_id = :itemId
                          AND NOT EXISTS (
                                SELECT 1 FROM user_item_link_modifiers m
                                WHERE m.user_item_link_id = l.id AND m.modifier_id = :modifierId)
                        """, nativeQuery = true)
        int addModifierToAllLinksOfItem(
                        @Param("itemId") UUID itemId,
                        @Param("modifierId") UUID modifierId);

        @Query(value = """
                        SELECT l.id, l.serial_number,
                               ROW_NUMBER() OVER (
                                   ORDER BY COALESCE(b.claimed_at, uml.completed_at, uml.created_at, l.awarded_at),
                                            l.user_id, l.id)
                        FROM user_item_links l
                        LEFT JOIN user_milestone_set_bonuses b
                               ON l.source = 'milestone_set'
                              AND b.user_id = l.user_id
                              AND CAST(b.milestone_set_id AS text) = l.source_id
                        LEFT JOIN user_milestone_links uml
                               ON l.source = 'milestone'
                              AND uml.user_id = l.user_id
                              AND CAST(uml.milestone_id AS text) = l.source_id
                              AND uml.completed = true
                        WHERE l.item_id = :itemId
                        """, nativeQuery = true)
        List<Object[]> findSerialsInEarnedOrder(@Param("itemId") UUID itemId);

        @Query(value = """
                        SELECT COUNT(*) FROM user_item_links l
                        WHERE l.item_id = :itemId
                          AND (l.escrowed = true OR l.source NOT IN ('milestone', 'milestone_set'))
                        """, nativeQuery = true)
        long countUnorderableLinks(@Param("itemId") UUID itemId);

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query(value = """
                        UPDATE user_item_links SET serial_number = NULL
                        WHERE item_id = :itemId AND serial_number IS NOT NULL
                        """, nativeQuery = true)
        int clearSerials(@Param("itemId") UUID itemId);

        @Modifying
        @Query(value = """
                        DELETE FROM user_item_link_modifiers lm
                        USING user_item_links l, item_modifiers m
                        WHERE lm.user_item_link_id = l.id AND lm.modifier_id = m.id
                          AND l.item_id = :itemId AND m.key = 'founders'
                          AND (l.serial_number IS NULL OR l.serial_number > :threshold)
                        """, nativeQuery = true)
        int stripFoundersAboveSerial(@Param("itemId") UUID itemId, @Param("threshold") long threshold);

        @Modifying
        @Query(value = """
                        INSERT INTO user_item_link_modifiers (user_item_link_id, modifier_id)
                        SELECT l.id, m.id
                        FROM user_item_links l
                        JOIN item_modifiers m ON m.key = 'founders'
                        WHERE l.item_id = :itemId
                          AND l.serial_number IS NOT NULL AND l.serial_number <= :threshold
                          AND NOT EXISTS (
                              SELECT 1 FROM user_item_link_modifiers x
                              WHERE x.user_item_link_id = l.id AND x.modifier_id = m.id)
                        """, nativeQuery = true)
        int grantFoundersUpToSerial(@Param("itemId") UUID itemId, @Param("threshold") long threshold);

        @Modifying
        @Query(value = """
                        DELETE FROM user_item_link_modifiers lm
                        USING user_item_links l, item_modifiers m
                        WHERE lm.user_item_link_id = l.id AND lm.modifier_id = m.id
                          AND l.item_id = :itemId AND m.key = 'normal'
                          AND EXISTS (
                              SELECT 1 FROM user_item_link_modifiers other
                              JOIN item_modifiers om ON om.id = other.modifier_id
                              WHERE other.user_item_link_id = l.id AND om.key <> 'normal')
                        """, nativeQuery = true)
        int stripRedundantNormal(@Param("itemId") UUID itemId);

        @Modifying
        @Query(value = """
                        INSERT INTO user_item_link_modifiers (user_item_link_id, modifier_id)
                        SELECT l.id, m.id
                        FROM user_item_links l
                        JOIN item_modifiers m ON m.key = 'normal'
                        WHERE l.item_id = :itemId
                          AND NOT EXISTS (
                              SELECT 1 FROM user_item_link_modifiers x WHERE x.user_item_link_id = l.id)
                        """, nativeQuery = true)
        int grantNormalWhereBare(@Param("itemId") UUID itemId);
}
