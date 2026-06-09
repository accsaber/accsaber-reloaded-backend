package com.accsaber.backend.repository.campaign;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.campaign.CampaignTag;
import com.accsaber.backend.model.entity.campaign.CampaignTagKind;

public interface CampaignTagRepository extends JpaRepository<CampaignTag, UUID> {

    List<CampaignTag> findByActiveTrue();

    List<CampaignTag> findByKindAndActiveTrue(CampaignTagKind kind);

    Optional<CampaignTag> findByIdAndActiveTrue(UUID id);

    @Query("SELECT t FROM CampaignTag t WHERE t.kind = :kind AND LOWER(t.name) = LOWER(:name) AND t.active = true")
    Optional<CampaignTag> findByKindAndNameIgnoreCaseAndActiveTrue(@Param("kind") CampaignTagKind kind,
            @Param("name") String name);
}
