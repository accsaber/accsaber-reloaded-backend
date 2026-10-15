package com.accsaber.backend.repository.item;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.item.CrateModifier;
import com.accsaber.backend.model.entity.item.CrateModifier.CrateModifierId;

public interface CrateModifierRepository extends JpaRepository<CrateModifier, CrateModifierId> {

    List<CrateModifier> findByCrateItem_Id(UUID crateItemId);
}
