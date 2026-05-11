package com.accsaber.backend.repository.item;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.accsaber.backend.model.entity.item.ItemRarity;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.item.UserItemLink;

@Repository
public interface UserItemLinkRepository extends JpaRepository<UserItemLink, UUID> {

        List<UserItemLink> findByUser_Id(Long userId);

        List<UserItemLink> findByUser_IdAndItem_Type_Key(Long userId, String typeKey);

        List<UserItemLink> findByUser_IdAndItem_Id(Long userId, UUID itemId);

        @Query("""
                        SELECT l FROM UserItemLink l
                        JOIN l.item i
                        JOIN i.type t
                        LEFT JOIN t.parentType pt
                        WHERE l.user.id = :userId
                        AND (:typeKeys IS NULL OR t.key IN :typeKeys OR pt.key IN :typeKeys)
                        AND (:rarities IS NULL OR i.rarity IN :rarities)
                        AND (:modifierKeys IS NULL OR EXISTS (
                                SELECT m FROM l.modifiers m WHERE m.key IN :modifierKeys))
                        AND (:tradeable IS NULL OR i.tradeable = :tradeable)
                        AND (:sources IS NULL OR l.source IN :sources)
                        AND (:deprecated IS NULL OR i.deprecated = :deprecated)
                        AND (CAST(:search AS string) IS NULL
                                OR LOWER(i.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
                        """)
        Page<UserItemLink> findInventoryFiltered(
                        @Param("userId") Long userId,
                        @Param("typeKeys") Collection<String> typeKeys,
                        @Param("rarities") Collection<ItemRarity> rarities,
                        @Param("modifierKeys") Collection<String> modifierKeys,
                        @Param("tradeable") Boolean tradeable,
                        @Param("sources") Collection<ItemSource> sources,
                        @Param("deprecated") Boolean deprecated,
                        @Param("search") String search,
                        Pageable pageable);

        boolean existsByUser_IdAndItem_Id(Long userId, UUID itemId);

        boolean existsByUser_IdAndItem_IdAndSourceAndSourceId(Long userId, UUID itemId, ItemSource source,
                        String sourceId);

        @Query("""
                        SELECT l FROM UserItemLink l
                        JOIN FETCH l.item i
                        JOIN FETCH i.type t
                        WHERE l.user.id = :userId AND t.key IN :typeKeys
                        """)
        List<UserItemLink> findOwnedByTypeKeys(
                        @Param("userId") Long userId,
                        @Param("typeKeys") Collection<String> typeKeys);

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
}
