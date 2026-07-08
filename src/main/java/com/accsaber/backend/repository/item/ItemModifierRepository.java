package com.accsaber.backend.repository.item;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.accsaber.backend.model.entity.item.ItemModifier;

@Repository
public interface ItemModifierRepository extends JpaRepository<ItemModifier, UUID> {

    List<ItemModifier> findByActiveTrue();

    List<ItemModifier> findByActiveTrueAndGlobalDropChanceIsNotNull();

    Optional<ItemModifier> findByKey(String key);
}
