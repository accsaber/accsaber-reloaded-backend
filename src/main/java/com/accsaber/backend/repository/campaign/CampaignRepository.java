package com.accsaber.backend.repository.campaign;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignCollaboratorStatus;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.campaign.UserCampaignStatus;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {

        Optional<Campaign> findByIdAndActiveTrue(UUID id);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT c FROM Campaign c WHERE c.id = :id AND c.active = true")
        Optional<Campaign> findByIdAndActiveTrueForUpdate(@Param("id") UUID id);

        @Query("SELECT c.creator.id FROM Campaign c WHERE c.id = :id AND c.active = true")
        Optional<Long> findCreatorIdByIdAndActiveTrue(@Param("id") UUID id);

        List<Campaign> findByIdInAndActiveTrue(Collection<UUID> ids);

        Optional<Campaign> findBySlugAndActiveTrue(String slug);

        boolean existsBySlug(String slug);

        @Query("SELECT COUNT(c) > 0 FROM Campaign c WHERE c.slug = :slug AND c.id <> :excludeId")
        boolean existsBySlugAndIdNot(@Param("slug") String slug, @Param("excludeId") UUID excludeId);

        @EntityGraph(attributePaths = { "creator" })
        @Query("""
                        SELECT c FROM Campaign c
                        LEFT JOIN CampaignRewardTotals rt ON rt.campaignId = c.id
                        LEFT JOIN UserCampaign puc ON puc.campaign = c AND puc.user.id = :participantId AND puc.active = true
                        WHERE c.active = true
                          AND (:hasStatus = false OR c.status IN :statuses)
                          AND (:creatorId IS NULL OR c.creator.id = :creatorId)
                          AND (:memberId IS NULL OR c.creator.id = :memberId OR EXISTS (
                              SELECT 1 FROM CampaignCollaborator mc
                              WHERE mc.campaign = c AND mc.user.id = :memberId
                                AND mc.active = true AND mc.status = :collaboratorStatus))
                          AND (:privileged = true OR c.status <> :draftStatus OR c.creator.id = :viewerId
                              OR EXISTS (
                                  SELECT 1 FROM CampaignCollaborator cc
                                  WHERE cc.campaign = c AND cc.user.id = :viewerId
                                    AND cc.active = true AND cc.status = :collaboratorStatus))
                          AND (:hasTags = false OR EXISTS (
                              SELECT 1 FROM CampaignTagLink ctl
                              WHERE ctl.campaign = c AND ctl.campaignTag.id IN :tagIds))
                          AND (:participantId IS NULL OR puc.status IN :progressStatuses)
                          AND (:official IS NULL OR c.official = :official)
                          AND (:loved IS NULL OR c.loved = :loved)
                          AND (CAST(:search AS string) IS NULL
                              OR search_normalize(c.name) LIKE CONCAT('%', search_normalize(CAST(:search AS string)), '%')
                              OR c.creator.id IN (SELECT sn.userId FROM UserSearchName sn WHERE sn.searchName LIKE CONCAT('%', search_normalize(CAST(:search AS string)), '%'))
                              OR EXISTS (
                                  SELECT 1 FROM CampaignCollaborator sc
                                  WHERE sc.campaign = c AND sc.active = true AND sc.status = :collaboratorStatus
                                    AND sc.user.id IN (SELECT sn.userId FROM UserSearchName sn WHERE sn.searchName LIKE CONCAT('%', search_normalize(CAST(:search AS string)), '%'))))
                        """)
        Page<Campaign> findFiltered(
                        @Param("hasStatus") boolean hasStatus,
                        @Param("statuses") Collection<CampaignStatus> statuses,
                        @Param("creatorId") Long creatorId,
                        @Param("memberId") Long memberId,
                        @Param("hasTags") boolean hasTags,
                        @Param("tagIds") Collection<UUID> tagIds,
                        @Param("draftStatus") CampaignStatus draftStatus,
                        @Param("viewerId") Long viewerId,
                        @Param("privileged") boolean privileged,
                        @Param("collaboratorStatus") CampaignCollaboratorStatus collaboratorStatus,
                        @Param("search") String search,
                        @Param("official") Boolean official,
                        @Param("loved") Boolean loved,
                        @Param("participantId") Long participantId,
                        @Param("progressStatuses") Collection<UserCampaignStatus> progressStatuses,
                        Pageable pageable);
}
