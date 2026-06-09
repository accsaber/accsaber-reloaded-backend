package com.accsaber.backend.repository.campaign;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.campaign.CampaignCompletionItem;
import com.accsaber.backend.model.entity.campaign.CampaignCompletionItem.CampaignCompletionItemId;

public interface CampaignCompletionItemRepository
                extends JpaRepository<CampaignCompletionItem, CampaignCompletionItemId> {

        @Query("""
                        SELECT cci FROM CampaignCompletionItem cci
                        JOIN FETCH cci.item
                        WHERE cci.campaign.id = :campaignId
                        """)
        List<CampaignCompletionItem> findByCampaign_Id(@Param("campaignId") UUID campaignId);

        @Query("""
                        SELECT cci FROM CampaignCompletionItem cci
                        JOIN FETCH cci.item
                        WHERE cci.campaign.id IN :campaignIds
                        """)
        List<CampaignCompletionItem> findByCampaign_IdIn(@Param("campaignIds") Collection<UUID> campaignIds);

        void deleteByCampaign_IdAndItem_Id(UUID campaignId, UUID itemId);

        void deleteByCampaign_Id(UUID campaignId);
}
