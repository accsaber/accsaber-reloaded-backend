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

import com.accsaber.backend.model.entity.campaign.CampaignCollaborator;
import com.accsaber.backend.model.entity.campaign.CampaignCollaboratorStatus;

public interface CampaignCollaboratorRepository extends JpaRepository<CampaignCollaborator, UUID> {

        Optional<CampaignCollaborator> findByCampaign_IdAndUser_IdAndActiveTrue(UUID campaignId, Long userId);

        @EntityGraph(attributePaths = { "user", "invitedBy" })
        List<CampaignCollaborator> findByCampaign_IdAndActiveTrue(UUID campaignId);

        @EntityGraph(attributePaths = { "campaign", "user", "invitedBy" })
        @Query("""
                        SELECT c FROM CampaignCollaborator c
                        WHERE c.user.id = :userId AND c.status = :status AND c.active = true
                          AND c.campaign.active = true
                        """)
        Page<CampaignCollaborator> findByUser_IdAndStatusAndActiveTrue(
                        @Param("userId") Long userId,
                        @Param("status") CampaignCollaboratorStatus status, Pageable pageable);

        boolean existsByCampaign_IdAndUser_IdAndStatusAndActiveTrue(UUID campaignId, Long userId,
                        CampaignCollaboratorStatus status);

        long countByCampaign_IdAndActiveTrueAndStatusIn(UUID campaignId,
                        Collection<CampaignCollaboratorStatus> statuses);
}
