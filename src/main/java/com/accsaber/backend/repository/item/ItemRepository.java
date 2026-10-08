package com.accsaber.backend.repository.item;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.accsaber.backend.model.entity.item.Item;

import jakarta.persistence.LockModeType;

@Repository
public interface ItemRepository extends JpaRepository<Item, UUID> {

    List<Item> findByActiveTrue();

    @Query("""
            SELECT i FROM Item i JOIN FETCH i.type t LEFT JOIN t.parentType p
            WHERE i.active = true AND i.visible = true AND (p IS NULL OR p.key <> 'clan_cosmetic')
            """)
    List<Item> findVisiblePlayerItems();

    List<Item> findByType_IdAndActiveTrue(UUID typeId);

    List<Item> findByType_IdAndActiveTrueAndVisibleTrue(UUID typeId);

    List<Item> findByType_Id(UUID typeId);

    Optional<Item> findByIdAndActiveTrue(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Item i WHERE i.id = :id")
    Optional<Item> findByIdForUpdate(@Param("id") UUID id);


    List<Item> findByUnlockLevelAndActiveTrue(Integer unlockLevel);

    @Query("""
            SELECT DISTINCT i.unlockLevel FROM Item i
            WHERE i.unlockLevel IS NOT NULL AND i.unlockLevel > 0 AND i.active = true
            ORDER BY i.unlockLevel ASC
            """)
    List<Integer> findDistinctUnlockLevels();

    List<Item> findByWelcomeGrantTrueAndActiveTrueAndDeprecatedFalse();

    List<Item> findByType_Key(String typeKey);

    List<Item> findByType_KeyAndActiveTrueAndDeprecatedFalseAndVisibleTrue(String typeKey);

    List<Item> findByMissionPoolableTrueAndActiveTrueAndDeprecatedFalse();

    Optional<Item> findByType_KeyAndNameAndActiveTrue(String typeKey, String name);

    boolean existsByType_IdAndName(UUID typeId, String name);

    @Query(value = """
            SELECT i.id FROM items i
            WHERE i.serialized = true AND i.tradeable = false
              AND EXISTS (
                  SELECT 1 FROM user_item_links l
                  WHERE l.item_id = i.id AND l.serial_number IS NOT NULL)
            ORDER BY i.name
            """, nativeQuery = true)
    List<UUID> findResequenceCandidates();

    @Modifying
    @Query(value = "UPDATE items SET next_serial = :next WHERE id = :itemId", nativeQuery = true)
    void resetNextSerial(@Param("itemId") UUID itemId, @Param("next") long next);
}
