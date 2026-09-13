package com.accsaber.backend.service.campaign;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.entity.chat.ChatMessage;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.campaign.CampaignRepository;
import com.accsaber.backend.repository.chat.ChatMessageRepository;
import com.accsaber.backend.service.chat.ChatChannel;
import com.accsaber.backend.websocket.server.CampaignPresenceWebSocketHandler;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CampaignChatChannel implements ChatChannel {

    private final ChatMessageRepository chatRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignCollaboratorService collaboratorService;
    private final CampaignPresenceWebSocketHandler presenceHandler;

    @Override
    public void assertParticipant(UUID campaignId, Long userId) {
        if (!collaboratorService.isParticipant(campaignId, userId)) {
            throw new ValidationException("Only the campaign owner or a collaborator can use this chat");
        }
    }

    @Override
    public Page<ChatMessage> findMessages(UUID campaignId, Pageable pageable) {
        return chatRepository.findByCampaign_IdOrderByCreatedAtDesc(campaignId, pageable);
    }

    @Override
    public ChatMessage newMessage(UUID campaignId, User author, String content) {
        return ChatMessage.builder()
                .campaign(campaignRepository.getReferenceById(campaignId))
                .user(author)
                .content(content)
                .build();
    }

    @Override
    public void prune(UUID campaignId, int keep) {
        chatRepository.pruneCampaignToNewest(campaignId, keep);
    }

    @Override
    public void broadcast(UUID campaignId, String json) {
        presenceHandler.broadcastChat(campaignId, json);
    }
}
