package com.accsaber.backend.repository.campaign;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.campaign.UserCampaign;
import com.accsaber.backend.model.entity.campaign.UserCampaignStatus;

public interface UserCampaignRepository extends JpaRepository<UserCampaign, UUID> {

    Optional<UserCampaign> findByUser_IdAndCampaign_IdAndActiveTrue(Long userId, UUID campaignId);

    List<UserCampaign> findByUser_IdAndCampaign_IdInAndActiveTrue(Long userId, Collection<UUID> campaignIds);

    @Query(value = """
            SELECT uc FROM UserCampaign uc
            JOIN FETCH uc.campaign c
            LEFT JOIN FETCH c.creator
            WHERE uc.user.id = :userId AND uc.active = true AND uc.status <> :excludedStatus
            """,
            countQuery = """
            SELECT COUNT(uc) FROM UserCampaign uc
            WHERE uc.user.id = :userId AND uc.active = true AND uc.status <> :excludedStatus
            """)
    Page<UserCampaign> findActiveByUserExcludingStatus(@Param("userId") Long userId,
            @Param("excludedStatus") UserCampaignStatus excludedStatus, Pageable pageable);

    List<UserCampaign> findByUser_IdAndStatusAndActiveTrue(Long userId, UserCampaignStatus status);
}
