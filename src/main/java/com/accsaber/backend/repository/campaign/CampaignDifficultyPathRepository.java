package com.accsaber.backend.repository.campaign;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.campaign.CampaignDifficultyPath;

public interface CampaignDifficultyPathRepository extends JpaRepository<CampaignDifficultyPath, UUID> {

    List<CampaignDifficultyPath> findByCampaignDifficulty_IdAndActiveTrue(UUID campaignDifficultyId);

    List<CampaignDifficultyPath> findByCampaignDifficulty_Campaign_IdAndActiveTrue(UUID campaignId);

    List<CampaignDifficultyPath> findByCampaignDifficulty_Campaign_IdInAndActiveTrue(
            Collection<UUID> campaignIds);

    @Modifying
    @Query("delete from CampaignDifficultyPath p where p.campaignDifficulty.id = :difficultyId")
    int deleteAllByCampaignDifficultyId(@Param("difficultyId") UUID difficultyId);

    @Modifying
    @Query("delete from CampaignDifficultyPath p where p.campaignDifficulty.id = :difficultyId or p.comesFromCampaignDifficulty.id = :difficultyId")
    int deleteAllTouching(@Param("difficultyId") UUID difficultyId);
}
