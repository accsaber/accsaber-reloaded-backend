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

        List<CampaignDifficulty> findByCampaign_IdAndBarrierTrueAndActiveTrue(UUID campaignId);

        List<CampaignDifficulty> findByCampaign_IdInAndBarrierTrueAndActiveTrue(Collection<UUID> campaignIds);

        long countByCampaign_IdAndBarrierFalseAndActiveTrue(UUID campaignId);

        long countByCampaign_IdAndBarrierTrueAndActiveTrue(UUID campaignId);

        Optional<CampaignDifficulty> findByIdAndActiveTrue(UUID id);

        List<CampaignDifficulty> findByCampaign_IdAndMapDifficulty_IdAndActiveTrue(UUID campaignId,
                        UUID mapDifficultyId);

        boolean existsByCampaign_IdAndPositionXAndPositionYAndActiveTrue(UUID campaignId, Integer positionX,
                        Integer positionY);

        @Query("""
                        SELECT cd.campaign.id, COUNT(cd) FROM CampaignDifficulty cd
                        WHERE cd.campaign.id IN :campaignIds AND cd.active = true AND cd.barrier = false
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

        boolean existsByIdInAndMapDifficulty_StatusAndActiveTrue(Collection<UUID> ids,
                        com.accsaber.backend.model.entity.map.MapDifficultyStatus status);

        boolean existsByMapDifficulty_IdAndActiveTrue(UUID mapDifficultyId);

        @Query("""
                        SELECT DISTINCT md.blLeaderboardId, md.ssLeaderboardId FROM CampaignDifficulty cd
                        JOIN cd.mapDifficulty md
                        WHERE cd.active = true AND cd.barrier = false
                          AND md.active = true
                          AND md.status <> com.accsaber.backend.model.entity.map.MapDifficultyStatus.RANKED
                          AND cd.campaign.active = true
                          AND cd.campaign.status <> com.accsaber.backend.model.entity.campaign.CampaignStatus.DRAFT
                          AND EXISTS (
                                SELECT 1 FROM UserCampaign uc
                                WHERE uc.campaign = cd.campaign AND uc.active = true
                                  AND uc.status = com.accsaber.backend.model.entity.campaign.UserCampaignStatus.IN_PROGRESS)
                        """)
        List<Object[]> findCampaignIngestLeaderboardIds();
}
