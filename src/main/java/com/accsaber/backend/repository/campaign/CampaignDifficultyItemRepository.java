package com.accsaber.backend.repository.campaign;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.campaign.CampaignDifficultyItem;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyItem.CampaignDifficultyItemId;

public interface CampaignDifficultyItemRepository
                extends JpaRepository<CampaignDifficultyItem, CampaignDifficultyItemId> {

        @Query("""
                        SELECT cdi FROM CampaignDifficultyItem cdi
                        JOIN FETCH cdi.item
                        WHERE cdi.campaignDifficulty.id = :campaignDifficultyId
                        """)
        List<CampaignDifficultyItem> findByCampaignDifficulty_Id(
                        @Param("campaignDifficultyId") UUID campaignDifficultyId);

        @Query("""
                        SELECT cdi FROM CampaignDifficultyItem cdi
                        JOIN FETCH cdi.item
                        WHERE cdi.campaignDifficulty.id IN :campaignDifficultyIds
                        """)
        List<CampaignDifficultyItem> findByCampaignDifficulty_IdIn(
                        @Param("campaignDifficultyIds") Collection<UUID> campaignDifficultyIds);

        void deleteByCampaignDifficulty_IdAndItem_Id(UUID campaignDifficultyId, UUID itemId);

        void deleteByCampaignDifficulty_Id(UUID campaignDifficultyId);
}
