package com.accsaber.backend.repository.campaign;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.campaign.CampaignVote;
import com.accsaber.backend.model.entity.campaign.CampaignVoteDirection;

public interface CampaignVoteRepository extends JpaRepository<CampaignVote, UUID> {

    Optional<CampaignVote> findByCampaign_IdAndUser_Id(UUID campaignId, Long userId);

    List<CampaignVote> findByUser_IdAndCampaign_IdIn(Long userId, Collection<UUID> campaignIds);

    long countByCampaign_IdAndVote(UUID campaignId, CampaignVoteDirection vote);

    @Modifying
    @Query("DELETE FROM CampaignVote v WHERE v.campaign.id = :campaignId AND v.user.id = :userId")
    int deleteByCampaign_IdAndUser_Id(@Param("campaignId") UUID campaignId, @Param("userId") Long userId);
}
