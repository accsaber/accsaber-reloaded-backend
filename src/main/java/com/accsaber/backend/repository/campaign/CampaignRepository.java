package com.accsaber.backend.repository.campaign;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {

        List<Campaign> findByActiveTrue();

        @EntityGraph(attributePaths = { "creator", "campaignDifficulties" })
        Page<Campaign> findByActiveTrue(Pageable pageable);

        @EntityGraph(attributePaths = { "creator", "campaignDifficulties" })
        Page<Campaign> findByActiveTrueAndStatusIn(Collection<CampaignStatus> statuses, Pageable pageable);

        Optional<Campaign> findByIdAndActiveTrue(UUID id);

        List<Campaign> findByIdInAndActiveTrue(Collection<UUID> ids);

        Optional<Campaign> findBySlugAndActiveTrue(String slug);

        boolean existsBySlug(String slug);

        @Query("SELECT COUNT(c) > 0 FROM Campaign c WHERE c.slug = :slug AND c.id <> :excludeId")
        boolean existsBySlugAndIdNot(@Param("slug") String slug, @Param("excludeId") UUID excludeId);

        @EntityGraph(attributePaths = { "creator", "campaignDifficulties" })
        Page<Campaign> findByActiveTrueAndSeekingCurationTrue(Pageable pageable);

        @EntityGraph(attributePaths = { "creator", "campaignDifficulties" })
        @Query("""
                        SELECT DISTINCT c FROM Campaign c
                        WHERE c.active = true
                          AND (:hasStatus = false OR c.status IN :statuses)
                          AND (:creatorId IS NULL OR c.creator.id = :creatorId)
                          AND (:privileged = true OR c.status <> :draftStatus OR c.creator.id = :viewerId)
                          AND (:hasTags = false OR EXISTS (
                              SELECT 1 FROM CampaignTagLink ctl
                              WHERE ctl.campaign = c AND ctl.campaignTag.id IN :tagIds))
                        """)
        Page<Campaign> findFiltered(
                        @Param("hasStatus") boolean hasStatus,
                        @Param("statuses") Collection<CampaignStatus> statuses,
                        @Param("creatorId") Long creatorId,
                        @Param("hasTags") boolean hasTags,
                        @Param("tagIds") Collection<UUID> tagIds,
                        @Param("draftStatus") CampaignStatus draftStatus,
                        @Param("viewerId") Long viewerId,
                        @Param("privileged") boolean privileged,
                        Pageable pageable);
}
