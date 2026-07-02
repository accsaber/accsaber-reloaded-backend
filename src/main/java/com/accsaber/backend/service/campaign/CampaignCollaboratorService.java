package com.accsaber.backend.service.campaign;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.campaign.CampaignCollaboratorResponse;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignCollaborator;
import com.accsaber.backend.model.entity.campaign.CampaignCollaboratorStatus;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.campaign.CampaignCollaboratorRepository;
import com.accsaber.backend.repository.campaign.CampaignRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.DuplicateUserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampaignCollaboratorService {

    private static final int MAX_COLLABORATORS_PER_CAMPAIGN = 15;

    private final CampaignCollaboratorRepository collaboratorRepository;
    private final CampaignRepository campaignRepository;
    private final UserRepository userRepository;
    private final DuplicateUserService duplicateUserService;

    @Transactional
    public CampaignCollaboratorResponse invite(Long ownerId, UUID campaignId, Long targetUserId) {
        Long resolvedOwnerId = duplicateUserService.resolvePrimaryUserId(ownerId);
        Campaign campaign = loadCampaign(campaignId);
        assertOwner(campaign, resolvedOwnerId);

        Long resolvedTargetId = duplicateUserService.resolvePrimaryUserId(targetUserId);
        if (resolvedTargetId.equals(resolvedOwnerId)) {
            throw new ValidationException("The campaign owner is already on the campaign");
        }
        if (collaboratorRepository.countByCampaign_IdAndActiveTrueAndStatusIn(campaignId,
                List.of(CampaignCollaboratorStatus.PENDING, CampaignCollaboratorStatus.ACCEPTED))
                >= MAX_COLLABORATORS_PER_CAMPAIGN) {
            throw new ValidationException("Campaign has reached the maximum of "
                    + MAX_COLLABORATORS_PER_CAMPAIGN + " collaborators");
        }
        User target = userRepository.findByIdAndActiveTrue(resolvedTargetId)
                .orElseThrow(() -> new ResourceNotFoundException("User", targetUserId));

        CampaignCollaborator existing = collaboratorRepository
                .findByCampaign_IdAndUser_IdAndActiveTrue(campaignId, resolvedTargetId)
                .orElse(null);
        if (existing != null) {
            if (existing.getStatus() == CampaignCollaboratorStatus.DECLINED) {
                existing.setStatus(CampaignCollaboratorStatus.PENDING);
                existing.setInvitedBy(campaign.getCreator());
                return toResponse(collaboratorRepository.save(existing));
            }
            throw new ValidationException("User is already invited to this campaign");
        }

        CampaignCollaborator collaborator = CampaignCollaborator.builder()
                .campaign(campaign)
                .user(target)
                .status(CampaignCollaboratorStatus.PENDING)
                .invitedBy(campaign.getCreator())
                .build();
        return toResponse(collaboratorRepository.save(collaborator));
    }

    @Transactional
    public CampaignCollaboratorResponse respond(Long userId, UUID campaignId, boolean accept) {
        Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(userId);
        CampaignCollaborator collaborator = collaboratorRepository
                .findByCampaign_IdAndUser_IdAndActiveTrue(campaignId, resolvedUserId)
                .orElseThrow(() -> new ResourceNotFoundException("CampaignCollaborator", campaignId));
        if (collaborator.getStatus() != CampaignCollaboratorStatus.PENDING) {
            throw new ValidationException("This invite has already been answered");
        }
        collaborator.setStatus(accept
                ? CampaignCollaboratorStatus.ACCEPTED
                : CampaignCollaboratorStatus.DECLINED);
        return toResponse(collaboratorRepository.save(collaborator));
    }

    @Transactional
    public void remove(Long actorId, UUID campaignId, Long targetUserId) {
        Long resolvedActorId = duplicateUserService.resolvePrimaryUserId(actorId);
        Long resolvedTargetId = duplicateUserService.resolvePrimaryUserId(targetUserId);
        Campaign campaign = loadCampaign(campaignId);
        boolean isOwner = campaign.getCreator() != null
                && resolvedActorId.equals(campaign.getCreator().getId());
        if (!isOwner && !resolvedActorId.equals(resolvedTargetId)) {
            throw new ValidationException("Only the campaign owner can remove other collaborators");
        }
        CampaignCollaborator collaborator = collaboratorRepository
                .findByCampaign_IdAndUser_IdAndActiveTrue(campaignId, resolvedTargetId)
                .orElseThrow(() -> new ResourceNotFoundException("CampaignCollaborator", campaignId));
        collaborator.setActive(false);
        collaboratorRepository.save(collaborator);
    }

    public List<CampaignCollaboratorResponse> listCollaborators(Long viewerId, UUID campaignId, boolean privileged) {
        Campaign campaign = loadCampaign(campaignId);
        Long resolvedViewerId = viewerId != null ? duplicateUserService.resolvePrimaryUserId(viewerId) : null;
        if (!privileged && !canViewRoster(campaign, resolvedViewerId)) {
            throw new ResourceNotFoundException("Campaign", campaignId);
        }
        return collaboratorRepository.findByCampaign_IdAndActiveTrue(campaignId).stream()
                .map(CampaignCollaboratorService::toResponse)
                .toList();
    }

    public Page<CampaignCollaboratorResponse> listMyCollaborations(Long userId,
            CampaignCollaboratorStatus status, Pageable pageable) {
        Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(userId);
        CampaignCollaboratorStatus effective = status != null ? status : CampaignCollaboratorStatus.ACCEPTED;
        return collaboratorRepository
                .findByUser_IdAndStatusAndActiveTrue(resolvedUserId, effective, pageable)
                .map(CampaignCollaboratorService::toResponse);
    }

    private boolean canViewRoster(Campaign campaign, Long viewerId) {
        if (viewerId == null) {
            return false;
        }
        if (campaign.getCreator() != null && viewerId.equals(campaign.getCreator().getId())) {
            return true;
        }
        return collaboratorRepository.existsByCampaign_IdAndUser_IdAndStatusAndActiveTrue(
                campaign.getId(), viewerId, CampaignCollaboratorStatus.ACCEPTED);
    }

    private void assertOwner(Campaign campaign, Long playerId) {
        if (campaign.getCreator() == null || !playerId.equals(campaign.getCreator().getId())) {
            throw new ValidationException("Only the campaign owner can manage collaborators");
        }
    }

    private Campaign loadCampaign(UUID campaignId) {
        return campaignRepository.findByIdAndActiveTrue(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));
    }

    private static CampaignCollaboratorResponse toResponse(CampaignCollaborator collaborator) {
        User user = collaborator.getUser();
        Campaign campaign = collaborator.getCampaign();
        return CampaignCollaboratorResponse.builder()
                .id(collaborator.getId())
                .campaignId(campaign.getId())
                .campaignName(campaign.getName())
                .campaignSlug(campaign.getSlug())
                .userId(user.getId())
                .userName(user.getName())
                .userAvatarUrl(user.getAvatarUrl())
                .userCdnAvatarUrl(user.getCdnAvatarUrl())
                .userCountry(user.getCountry())
                .status(collaborator.getStatus())
                .invitedById(collaborator.getInvitedBy() != null ? collaborator.getInvitedBy().getId() : null)
                .createdAt(collaborator.getCreatedAt())
                .build();
    }
}
