package com.accsaber.backend.service.campaign;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.TooManyRequestsException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.campaign.CampaignChatMessageResponse;
import com.accsaber.backend.model.event.CampaignChatMessageEvent;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignChatMessage;
import com.accsaber.backend.model.entity.campaign.CampaignCollaboratorStatus;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.campaign.CampaignChatMessageRepository;
import com.accsaber.backend.repository.campaign.CampaignCollaboratorRepository;
import com.accsaber.backend.repository.campaign.CampaignRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.DuplicateUserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampaignChatService {

    private static final int MAX_MESSAGES_PER_CAMPAIGN = 1000;

    private final CampaignChatMessageRepository chatRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignCollaboratorRepository collaboratorRepository;
    private final UserRepository userRepository;
    private final DuplicateUserService duplicateUserService;
    private final CampaignChatRateLimitService chatRateLimitService;
    private final ApplicationEventPublisher eventPublisher;

    public Page<CampaignChatMessageResponse> getMessages(Long playerId, UUID campaignId, Pageable pageable) {
        Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(playerId);
        assertParticipant(campaignId, resolvedUserId);
        return chatRepository.findByCampaign_IdOrderByCreatedAtDesc(campaignId, pageable)
                .map(CampaignChatService::toResponse);
    }

    @Transactional
    public CampaignChatMessageResponse sendMessage(Long playerId, UUID campaignId, String content) {
        Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(playerId);
        assertParticipant(campaignId, resolvedUserId);
        if (!chatRateLimitService.tryAcquire(resolvedUserId)) {
            throw new TooManyRequestsException("You're sending messages too quickly. Please slow down.");
        }
        Campaign campaign = campaignRepository.findByIdAndActiveTrue(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));
        User author = userRepository.findByIdAndActiveTrue(resolvedUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", resolvedUserId));
        CampaignChatMessage message = CampaignChatMessage.builder()
                .campaign(campaign)
                .user(author)
                .content(content.trim())
                .build();
        CampaignChatMessageResponse response = toResponse(chatRepository.save(message));
        chatRepository.pruneToNewest(campaignId, MAX_MESSAGES_PER_CAMPAIGN);
        eventPublisher.publishEvent(new CampaignChatMessageEvent(campaignId, response));
        return response;
    }

    private void assertParticipant(UUID campaignId, Long userId) {
        Long creatorId = campaignRepository.findCreatorIdByIdAndActiveTrue(campaignId).orElse(null);
        if (userId.equals(creatorId)) {
            return;
        }
        boolean collaborator = collaboratorRepository.existsByCampaign_IdAndUser_IdAndStatusAndActiveTrue(
                campaignId, userId, CampaignCollaboratorStatus.ACCEPTED);
        if (!collaborator) {
            throw new ValidationException("Only the campaign owner or a collaborator can use this chat");
        }
    }

    private static CampaignChatMessageResponse toResponse(CampaignChatMessage message) {
        User author = message.getUser();
        return CampaignChatMessageResponse.builder()
                .id(message.getId())
                .campaignId(message.getCampaign().getId())
                .authorId(author.getId())
                .authorName(author.getName())
                .authorAvatarUrl(author.getAvatarUrl())
                .authorCdnAvatarUrl(author.getCdnAvatarUrl())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
