package com.accsaber.backend.repository.item;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.accsaber.backend.model.entity.item.UnusualEffect;

@Repository
public interface UnusualEffectRepository extends JpaRepository<UnusualEffect, UUID> {

    List<UnusualEffect> findByActiveTrue();

    Optional<UnusualEffect> findByKey(String key);

    boolean existsByKey(String key);
}
