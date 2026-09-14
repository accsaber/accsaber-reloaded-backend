package com.accsaber.backend.repository.clan;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.clan.ClanEquippedItem;

public interface ClanEquippedItemRepository extends JpaRepository<ClanEquippedItem, ClanEquippedItem.Key> {

    @Query("SELECT e FROM ClanEquippedItem e JOIN FETCH e.item i JOIN FETCH i.type WHERE e.clan.id IN :clanIds")
    List<ClanEquippedItem> findByClanIds(@Param("clanIds") Collection<UUID> clanIds);

    @Query("SELECT e FROM ClanEquippedItem e JOIN FETCH e.item i JOIN FETCH i.type WHERE e.clan.active")
    List<ClanEquippedItem> findAllOfActiveClans();

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM ClanEquippedItem e WHERE e.clan.id = :clanId AND e.itemType.key = :itemTypeKey")
    int deleteSlot(@Param("clanId") UUID clanId, @Param("itemTypeKey") String itemTypeKey);
}
