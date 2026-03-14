package com.accsaber.backend.repository.campaign;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.campaign.Campaign;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {

    List<Campaign> findByActiveTrue();

    @EntityGraph(attributePaths = {"creator", "campaignMaps"})
    Page<Campaign> findByActiveTrue(Pageable pageable);

    Optional<Campaign> findByIdAndActiveTrue(UUID id);
}
