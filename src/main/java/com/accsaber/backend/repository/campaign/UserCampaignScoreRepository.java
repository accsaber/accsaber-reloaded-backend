package com.accsaber.backend.repository.campaign;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.campaign.UserCampaignScore;

public interface UserCampaignScoreRepository extends JpaRepository<UserCampaignScore, UUID> {

        Optional<UserCampaignScore> findByUser_IdAndCampaignDifficulty_IdAndActiveTrue(Long userId,
                        UUID campaignDifficultyId);

        List<UserCampaignScore> findByUser_IdAndCampaign_IdAndActiveTrue(Long userId, UUID campaignId);

        List<UserCampaignScore> findByCampaign_IdAndActiveTrue(UUID campaignId);

        List<UserCampaignScore> findByUser_IdAndCampaign_IdInAndActiveTrue(Long userId, Collection<UUID> campaignIds);

        List<UserCampaignScore> findByCampaign_IdAndActiveTrueAndRewardsPaidFalse(UUID campaignId);

        List<UserCampaignScore> findByUser_IdAndCampaign_IdAndActiveTrueAndRewardsPaidFalse(Long userId,
                        UUID campaignId);

        long countByUser_IdAndCampaign_IdAndActiveTrue(Long userId, UUID campaignId);

        @Query("""
                        SELECT ucs.campaign.id, COUNT(ucs) FROM UserCampaignScore ucs
                        WHERE ucs.user.id = :userId AND ucs.campaign.id IN :campaignIds AND ucs.active = true
                        GROUP BY ucs.campaign.id
                        """)
        List<Object[]> countActiveByUserAndCampaignIds(@Param("userId") Long userId,
                        @Param("campaignIds") Collection<UUID> campaignIds);
}
