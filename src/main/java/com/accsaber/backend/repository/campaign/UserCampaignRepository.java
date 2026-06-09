package com.accsaber.backend.repository.campaign;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.campaign.UserCampaign;
import com.accsaber.backend.model.entity.campaign.UserCampaignStatus;

public interface UserCampaignRepository extends JpaRepository<UserCampaign, UUID> {

    Optional<UserCampaign> findByUser_IdAndCampaign_IdAndActiveTrue(Long userId, UUID campaignId);

    List<UserCampaign> findByUser_IdAndCampaign_IdInAndActiveTrue(Long userId, Collection<UUID> campaignIds);

    Page<UserCampaign> findByUser_IdAndActiveTrue(Long userId, Pageable pageable);

    List<UserCampaign> findByUser_IdAndStatusAndActiveTrue(Long userId, UserCampaignStatus status);
}
