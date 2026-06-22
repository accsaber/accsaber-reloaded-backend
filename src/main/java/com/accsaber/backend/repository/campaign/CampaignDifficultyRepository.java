package com.accsaber.backend.repository.campaign;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.campaign.CampaignDifficulty;

public interface CampaignDifficultyRepository extends JpaRepository<CampaignDifficulty, UUID> {

        List<CampaignDifficulty> findByCampaign_IdAndActiveTrue(UUID campaignId);

        List<CampaignDifficulty> findByCampaign_IdAndActiveTrueAndRequirementDirtyTrue(UUID campaignId);

        List<CampaignDifficulty> findByCampaign_IdInAndActiveTrue(Collection<UUID> campaignIds);

        Optional<CampaignDifficulty> findByIdAndActiveTrue(UUID id);

        Optional<CampaignDifficulty> findByCampaign_IdAndMapDifficulty_IdAndActiveTrue(UUID campaignId,
                        UUID mapDifficultyId);

        boolean existsByCampaign_IdAndPositionXAndPositionYAndActiveTrue(UUID campaignId, Integer positionX,
                        Integer positionY);

        long countByCampaign_IdAndActiveTrue(UUID campaignId);

        @Query("""
                        SELECT cd.campaign.id, COUNT(cd) FROM CampaignDifficulty cd
                        WHERE cd.campaign.id IN :campaignIds AND cd.active = true
                        GROUP BY cd.campaign.id
                        """)
        List<Object[]> countActiveByCampaignIds(@Param("campaignIds") Collection<UUID> campaignIds);

        @Query("""
                        SELECT cd FROM CampaignDifficulty cd
                        JOIN FETCH cd.mapDifficulty md
                        JOIN FETCH md.map
                        WHERE cd.campaign.id = :campaignId AND cd.active = true
                        """)
        List<CampaignDifficulty> findActiveWithMapByCampaignId(@Param("campaignId") UUID campaignId);

        @Query("""
                        SELECT cd FROM CampaignDifficulty cd
                        JOIN FETCH cd.mapDifficulty md
                        JOIN FETCH md.map
                        WHERE cd.campaign.id IN :campaignIds AND cd.active = true
                        """)
        List<CampaignDifficulty> findActiveWithMapByCampaignIds(@Param("campaignIds") Collection<UUID> campaignIds);
}
