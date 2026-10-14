package com.accsaber.backend.service.campaign;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignCollaboratorStatus;
import com.accsaber.backend.model.entity.campaign.CampaignDifficulty;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.repository.campaign.CampaignCollaboratorRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyRepository;
import com.accsaber.backend.repository.campaign.CampaignRepository;
import com.accsaber.backend.service.media.MediaFormat;
import com.accsaber.backend.service.media.MediaProcessingService;
import com.accsaber.backend.service.player.DuplicateUserService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class CampaignAccess {

    private final CampaignRepository campaignRepository;
    private final CampaignDifficultyRepository campaignDifficultyRepository;
    private final CampaignCollaboratorRepository campaignCollaboratorRepository;
    private final DuplicateUserService duplicateUserService;
    private final MediaProcessingService mediaProcessingService;
    private final TransactionTemplate transactionTemplate;

    Campaign loadActiveCampaign(UUID campaignId) {
        return campaignRepository.findByIdAndActiveTrue(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));
    }

    CampaignDifficulty loadActiveDifficulty(UUID campaignDifficultyId) {
        return campaignDifficultyRepository.findByIdAndActiveTrue(campaignDifficultyId)
                .orElseThrow(() -> new ResourceNotFoundException("CampaignDifficulty", campaignDifficultyId));
    }

    Campaign ownedDraftCampaign(CampaignEditor editor, UUID campaignId) {
        Campaign campaign = loadActiveCampaign(campaignId);
        if (editor.privileged()) {
            return campaign;
        }
        Long playerId = resolveEditorId(editor);
        if (campaign.getCreator() == null || !playerId.equals(campaign.getCreator().getId())) {
            throw new ValidationException("Only the campaign creator can perform this action");
        }
        assertDraft(campaign);
        return campaign;
    }

    Campaign editableDraftCampaign(CampaignEditor editor, UUID campaignId) {
        Campaign campaign = loadActiveCampaign(campaignId);
        assertCanEditDraft(campaign, editor);
        return campaign;
    }

    CampaignDifficulty editableDraftDifficulty(CampaignEditor editor, UUID campaignDifficultyId) {
        CampaignDifficulty difficulty = loadActiveDifficulty(campaignDifficultyId);
        assertCanEditDraft(difficulty.getCampaign(), editor);
        return difficulty;
    }

    void assertCanEditDraft(Campaign campaign, CampaignEditor editor) {
        if (editor.privileged()) {
            return;
        }
        Long playerId = resolveEditorId(editor);
        boolean isOwner = campaign.getCreator() != null && playerId.equals(campaign.getCreator().getId());
        if (!isOwner && !isAcceptedCollaborator(campaign.getId(), playerId)) {
            throw new ValidationException("Only the campaign owner or a collaborator can perform this action");
        }
        assertDraft(campaign);
    }

    Long resolveEditorId(CampaignEditor editor) {
        if (editor.userId() == null) {
            throw new UnauthorizedException("Player authentication required");
        }
        return duplicateUserService.resolvePrimaryUserId(editor.userId());
    }

    boolean isDraftHiddenFrom(Campaign campaign, Long viewerId, boolean privileged) {
        if (privileged || campaign.getStatus() != CampaignStatus.DRAFT) {
            return false;
        }
        if (viewerId == null || campaign.getCreator() == null) {
            return true;
        }
        Long resolvedViewerId = duplicateUserService.resolvePrimaryUserId(viewerId);
        if (resolvedViewerId.equals(campaign.getCreator().getId())) {
            return false;
        }
        return !isAcceptedCollaborator(campaign.getId(), resolvedViewerId);
    }

    String storeMedia(CampaignEditor editor, UUID targetId, CampaignMediaKind kind, MultipartFile file) {
        transactionTemplate.executeWithoutResult(status -> {
            if (kind.node()) {
                editableDraftDifficulty(editor, targetId);
            } else {
                editableDraftCampaign(editor, targetId);
            }
        });
        return mediaProcessingService.storeImage(file, kind.subdir(), targetId.toString(), MediaFormat.GIF);
    }

    void deleteMedia(CampaignMediaKind kind, UUID targetId) {
        mediaProcessingService.deleteIfExists(kind.subdir(), targetId.toString());
    }

    static void ensureEditable(Campaign campaign) {
        if (campaign.getStatus() == CampaignStatus.CURATED) {
            throw new ValidationException("Curated campaigns are locked and cannot be edited");
        }
    }

    private static void assertDraft(Campaign campaign) {
        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new ValidationException("Players can only edit campaigns in draft status; unpublish first");
        }
    }

    private boolean isAcceptedCollaborator(UUID campaignId, Long playerId) {
        return campaignCollaboratorRepository.existsByCampaign_IdAndUser_IdAndStatusAndActiveTrue(
                campaignId, playerId, CampaignCollaboratorStatus.ACCEPTED);
    }
}
