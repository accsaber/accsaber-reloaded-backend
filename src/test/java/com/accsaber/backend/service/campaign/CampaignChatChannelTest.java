package com.accsaber.backend.service.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.chat.ChatMessage;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.campaign.CampaignRepository;
import com.accsaber.backend.repository.chat.ChatMessageRepository;
import com.accsaber.backend.websocket.server.CampaignPresenceWebSocketHandler;

@ExtendWith(MockitoExtension.class)
class CampaignChatChannelTest {

    @Mock
    private ChatMessageRepository chatRepository;
    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private CampaignCollaboratorService collaboratorService;
    @Mock
    private CampaignPresenceWebSocketHandler presenceHandler;

    @InjectMocks
    private CampaignChatChannel channel;

    private UUID campaignId;

    @BeforeEach
    void setUp() {
        campaignId = UUID.randomUUID();
    }

    @Test
    void participantsGetIn() {
        when(collaboratorService.isParticipant(campaignId, 50L)).thenReturn(true);

        assertThatCode(() -> channel.assertParticipant(campaignId, 50L)).doesNotThrowAnyException();
    }

    @Test
    void anyoneElseIsTurnedAway() {
        when(collaboratorService.isParticipant(campaignId, 50L)).thenReturn(false);

        assertThatThrownBy(() -> channel.assertParticipant(campaignId, 50L))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void newMessageIsAttachedToTheCampaign() {
        Campaign campaign = Campaign.builder().id(campaignId).build();
        User author = User.builder().id(50L).build();
        when(campaignRepository.getReferenceById(campaignId)).thenReturn(campaign);

        ChatMessage message = channel.newMessage(campaignId, author, "hello");

        assertThat(message.getCampaign()).isSameAs(campaign);
        assertThat(message.getUser()).isSameAs(author);
        assertThat(message.getContent()).isEqualTo("hello");
    }

    @Test
    void pruneAndBroadcastStayScopedToTheCampaign() {
        channel.prune(campaignId, 1000);
        channel.broadcast(campaignId, "{}");

        verify(chatRepository).pruneCampaignToNewest(campaignId, 1000);
        verify(presenceHandler).broadcastChat(campaignId, "{}");
    }
}
