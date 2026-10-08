package com.accsaber.backend.repository.item;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.accsaber.backend.model.entity.item.ItemType;

@Repository
public interface ItemTypeRepository extends JpaRepository<ItemType, UUID> {

    List<ItemType> findByActiveTrue();

    @Query("""
            SELECT t FROM ItemType t LEFT JOIN t.parentType p
            WHERE t.active = true AND t.key <> 'clan_cosmetic' AND (p IS NULL OR p.key <> 'clan_cosmetic')
            """)
    List<ItemType> findActivePlayerTypes();

    Optional<ItemType> findByIdAndActiveTrue(UUID id);

    Optional<ItemType> findByKey(String key);

    boolean existsByKey(String key);
}
