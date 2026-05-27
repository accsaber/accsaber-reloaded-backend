package com.accsaber.backend.repository.supporter;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.accsaber.backend.model.entity.supporter.SupporterTier;

@Repository
public interface SupporterTierRepository extends JpaRepository<SupporterTier, String> {

    List<SupporterTier> findAllByOrderBySortOrderAsc();

    Optional<SupporterTier> findByDisplayNameIgnoreCase(String displayName);
}
