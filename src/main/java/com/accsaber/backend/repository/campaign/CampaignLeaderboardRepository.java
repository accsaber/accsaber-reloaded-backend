package com.accsaber.backend.repository.campaign;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.campaign.UserCampaign;

public interface CampaignLeaderboardRepository extends JpaRepository<UserCampaign, UUID> {

    @Query(value = """
            SELECT u.id, u.name, u.country, u.avatar_url, u.cdn_avatar_url, uc.completed_at
            FROM user_campaigns uc
            JOIN users u ON u.id = uc.user_id
            WHERE uc.campaign_id = :campaignId AND uc.status = 'completed' AND uc.active = true
              AND u.active = true AND u.banned = false
            ORDER BY uc.completed_at ASC
            """,
            countQuery = """
            SELECT count(*) FROM user_campaigns uc
            JOIN users u ON u.id = uc.user_id
            WHERE uc.campaign_id = :campaignId AND uc.status = 'completed' AND uc.active = true
              AND u.active = true AND u.banned = false
            """,
            nativeQuery = true)
    Page<Object[]> completions(@Param("campaignId") UUID campaignId, Pageable pageable);

    @Query(value = """
            SELECT u.id, u.name, u.country, u.avatar_url, u.cdn_avatar_url,
                   AVG(s.score_no_mods::numeric / md.max_score) AS avg_acc,
                   AVG(s.ap) AS avg_ap,
                   COUNT(*) AS nodes
            FROM user_campaigns uc
            JOIN users u ON u.id = uc.user_id
            JOIN user_campaign_scores ucs
              ON ucs.user_id = uc.user_id AND ucs.campaign_id = uc.campaign_id AND ucs.active = true
            JOIN campaign_difficulties cd
              ON ucs.campaign_difficulty_id = cd.id AND cd.active = true AND cd.barrier = false
            JOIN scores s ON s.id = ucs.score_id
            JOIN map_difficulties md ON md.id = s.map_difficulty_id
            WHERE uc.campaign_id = :campaignId AND uc.status = 'completed' AND uc.active = true
              AND u.active = true AND u.banned = false
              AND md.max_score IS NOT NULL AND md.max_score > 0
            GROUP BY u.id, u.name, u.country, u.avatar_url, u.cdn_avatar_url
            ORDER BY avg_acc DESC
            """,
            countQuery = """
            SELECT count(DISTINCT uc.user_id)
            FROM user_campaigns uc
            JOIN users u ON u.id = uc.user_id
            JOIN user_campaign_scores ucs
              ON ucs.user_id = uc.user_id AND ucs.campaign_id = uc.campaign_id AND ucs.active = true
            JOIN campaign_difficulties cd
              ON ucs.campaign_difficulty_id = cd.id AND cd.active = true AND cd.barrier = false
            JOIN scores s ON s.id = ucs.score_id
            JOIN map_difficulties md ON md.id = s.map_difficulty_id
            WHERE uc.campaign_id = :campaignId AND uc.status = 'completed' AND uc.active = true
              AND u.active = true AND u.banned = false
              AND md.max_score IS NOT NULL AND md.max_score > 0
            """,
            nativeQuery = true)
    Page<Object[]> averagesByAccuracy(@Param("campaignId") UUID campaignId, Pageable pageable);

    @Query(value = """
            SELECT u.id, u.name, u.country, u.avatar_url, u.cdn_avatar_url,
                   AVG(s.score_no_mods::numeric / md.max_score) AS avg_acc,
                   AVG(s.ap) AS avg_ap,
                   COUNT(*) AS nodes
            FROM user_campaigns uc
            JOIN users u ON u.id = uc.user_id
            JOIN user_campaign_scores ucs
              ON ucs.user_id = uc.user_id AND ucs.campaign_id = uc.campaign_id AND ucs.active = true
            JOIN campaign_difficulties cd
              ON ucs.campaign_difficulty_id = cd.id AND cd.active = true AND cd.barrier = false
            JOIN scores s ON s.id = ucs.score_id
            JOIN map_difficulties md ON md.id = s.map_difficulty_id
            WHERE uc.campaign_id = :campaignId AND uc.status = 'completed' AND uc.active = true
              AND u.active = true AND u.banned = false
              AND md.max_score IS NOT NULL AND md.max_score > 0
            GROUP BY u.id, u.name, u.country, u.avatar_url, u.cdn_avatar_url
            ORDER BY avg_ap DESC
            """,
            countQuery = """
            SELECT count(DISTINCT uc.user_id)
            FROM user_campaigns uc
            JOIN users u ON u.id = uc.user_id
            JOIN user_campaign_scores ucs
              ON ucs.user_id = uc.user_id AND ucs.campaign_id = uc.campaign_id AND ucs.active = true
            JOIN campaign_difficulties cd
              ON ucs.campaign_difficulty_id = cd.id AND cd.active = true AND cd.barrier = false
            JOIN scores s ON s.id = ucs.score_id
            JOIN map_difficulties md ON md.id = s.map_difficulty_id
            WHERE uc.campaign_id = :campaignId AND uc.status = 'completed' AND uc.active = true
              AND u.active = true AND u.banned = false
              AND md.max_score IS NOT NULL AND md.max_score > 0
            """,
            nativeQuery = true)
    Page<Object[]> averagesByAp(@Param("campaignId") UUID campaignId, Pageable pageable);

    @Query(value = """
            SELECT u.id, u.name, u.country, u.avatar_url, u.cdn_avatar_url, uc.status, uc.completed_at,
                   (SELECT count(*) FROM user_campaign_scores ucs
                    JOIN campaign_difficulties cd ON cd.id = ucs.campaign_difficulty_id
                    WHERE ucs.user_id = uc.user_id AND ucs.campaign_id = uc.campaign_id
                      AND ucs.active = true AND cd.active = true AND cd.barrier = false) AS completed_nodes
            FROM user_campaigns uc
            JOIN users u ON u.id = uc.user_id
            WHERE uc.campaign_id = :campaignId AND uc.active = true AND uc.status <> 'abandoned'
              AND u.active = true AND u.banned = false
              AND (CAST(:search AS text) IS NULL
                   OR LOWER(u.name) LIKE LOWER('%' || CAST(:search AS text) || '%'))
            ORDER BY (uc.status = 'completed') DESC, completed_nodes DESC, uc.completed_at ASC NULLS LAST
            """,
            countQuery = """
            SELECT count(*) FROM user_campaigns uc
            JOIN users u ON u.id = uc.user_id
            WHERE uc.campaign_id = :campaignId AND uc.active = true AND uc.status <> 'abandoned'
              AND u.active = true AND u.banned = false
              AND (CAST(:search AS text) IS NULL
                   OR LOWER(u.name) LIKE LOWER('%' || CAST(:search AS text) || '%'))
            """,
            nativeQuery = true)
    Page<Object[]> progress(@Param("campaignId") UUID campaignId, @Param("search") String search, Pageable pageable);

    @Query(value = """
            SELECT u.id, u.name, u.country, u.avatar_url, u.cdn_avatar_url,
                   s.score,
                   CASE WHEN md.max_score > 0 THEN s.score_no_mods::numeric / md.max_score END AS accuracy,
                   s.ap
            FROM user_campaign_scores ucs
            JOIN users u ON u.id = ucs.user_id
            JOIN scores s ON s.id = ucs.score_id
            JOIN map_difficulties md ON md.id = s.map_difficulty_id
            WHERE ucs.campaign_difficulty_id = :nodeId AND ucs.active = true
              AND u.active = true AND u.banned = false
            ORDER BY s.ap DESC NULLS LAST, s.score DESC
            """,
            countQuery = """
            SELECT count(*) FROM user_campaign_scores ucs
            JOIN users u ON u.id = ucs.user_id
            JOIN scores s ON s.id = ucs.score_id
            WHERE ucs.campaign_difficulty_id = :nodeId AND ucs.active = true
              AND u.active = true AND u.banned = false
            """,
            nativeQuery = true)
    Page<Object[]> nodeScores(@Param("nodeId") UUID nodeId, Pageable pageable);
}
