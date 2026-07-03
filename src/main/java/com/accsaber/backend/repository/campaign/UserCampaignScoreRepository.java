package com.accsaber.backend.repository.campaign;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.campaign.UserCampaignScore;

public interface UserCampaignScoreRepository extends JpaRepository<UserCampaignScore, UUID> {

        Optional<UserCampaignScore> findByUser_IdAndCampaignDifficulty_IdAndActiveTrue(Long userId,
                        UUID campaignDifficultyId);

        List<UserCampaignScore> findByUser_IdAndCampaign_IdAndActiveTrue(Long userId, UUID campaignId);

        List<UserCampaignScore> findByCampaign_IdAndActiveTrue(UUID campaignId);

        List<UserCampaignScore> findByUser_IdAndCampaign_IdInAndActiveTrue(Long userId, Collection<UUID> campaignIds);

        @EntityGraph(attributePaths = { "score", "score.mapDifficulty" })
        List<UserCampaignScore> findWithScoreByUser_IdAndCampaign_IdInAndActiveTrue(Long userId,
                        Collection<UUID> campaignIds);

        @EntityGraph(attributePaths = { "score", "score.mapDifficulty" })
        List<UserCampaignScore> findWithScoreByUser_IdAndCampaign_IdAndActiveTrue(Long userId, UUID campaignId);

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

        @Query(value = """
                        SELECT COALESCE(SUM(xp), 0) FROM (
                            SELECT cd.xp AS xp
                            FROM user_campaign_scores ucs
                            JOIN campaign_difficulties cd ON ucs.campaign_difficulty_id = cd.id
                            JOIN campaigns c ON ucs.campaign_id = c.id
                            WHERE ucs.user_id = :userId AND ucs.submitted_at >= :since
                              AND ucs.active = true AND c.status = 'curated' AND cd.active = true
                            UNION ALL
                            SELECT c.completion_xp AS xp
                            FROM user_campaigns uc
                            JOIN campaigns c ON uc.campaign_id = c.id
                            WHERE uc.user_id = :userId AND uc.completed_at >= :since
                              AND uc.active = true AND uc.status = 'completed' AND c.status = 'curated'
                        ) parts
                        """, nativeQuery = true)
        BigDecimal sumCampaignXpGainedSince(@Param("userId") Long userId, @Param("since") Instant since);

        @Modifying
        @Query("delete from UserCampaignScore ucs where ucs.campaignDifficulty.id = :campaignDifficultyId")
        int deleteByCampaignDifficulty_Id(@Param("campaignDifficultyId") UUID campaignDifficultyId);
}
