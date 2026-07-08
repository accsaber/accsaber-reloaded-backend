package com.accsaber.backend.repository.item;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.accsaber.backend.model.entity.item.CrateUnusualEffect;
import com.accsaber.backend.model.entity.item.CrateUnusualEffect.CrateUnusualEffectId;

@Repository
public interface CrateUnusualEffectRepository extends JpaRepository<CrateUnusualEffect, CrateUnusualEffectId> {

    List<CrateUnusualEffect> findByCrateItem_Id(UUID crateItemId);
}
