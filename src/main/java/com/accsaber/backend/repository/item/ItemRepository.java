package com.accsaber.backend.repository.item;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.accsaber.backend.model.entity.item.Item;

import jakarta.persistence.LockModeType;

@Repository
public interface ItemRepository extends JpaRepository<Item, UUID> {

    List<Item> findByActiveTrue();

    List<Item> findByActiveTrueAndVisibleTrue();

    List<Item> findByType_IdAndActiveTrue(UUID typeId);

    List<Item> findByType_IdAndActiveTrueAndVisibleTrue(UUID typeId);

    List<Item> findByType_Id(UUID typeId);

    Optional<Item> findByIdAndActiveTrue(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Item i WHERE i.id = :id")
    Optional<Item> findByIdForUpdate(@Param("id") UUID id);

    List<Item> findByUnlockLevelAndActiveTrue(Integer unlockLevel);

    List<Item> findByWelcomeGrantTrueAndActiveTrueAndDeprecatedFalse();

    List<Item> findByType_Key(String typeKey);
}
