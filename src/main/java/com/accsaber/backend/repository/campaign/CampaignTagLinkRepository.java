package com.accsaber.backend.repository.campaign;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.campaign.CampaignTagLink;
import com.accsaber.backend.model.entity.campaign.CampaignTagLink.CampaignTagLinkId;

public interface CampaignTagLinkRepository extends JpaRepository<CampaignTagLink, CampaignTagLinkId> {

        @Query("""
                        SELECT ctl FROM CampaignTagLink ctl
                        JOIN FETCH ctl.campaignTag t
                        LEFT JOIN FETCH t.category
                        WHERE ctl.campaign.id = :campaignId
                        """)
        List<CampaignTagLink> findByCampaign_Id(@Param("campaignId") UUID campaignId);

        @Query("""
                        SELECT ctl FROM CampaignTagLink ctl
                        JOIN FETCH ctl.campaignTag t
                        LEFT JOIN FETCH t.category
                        WHERE ctl.campaign.id IN :campaignIds
                        """)
        List<CampaignTagLink> findByCampaign_IdIn(@Param("campaignIds") Collection<UUID> campaignIds);

        void deleteByCampaign_IdAndCampaignTag_Id(UUID campaignId, UUID campaignTagId);
}
