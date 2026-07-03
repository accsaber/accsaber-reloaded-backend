package com.accsaber.backend.service.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignChatMessage;
import com.accsaber.backend.model.event.CampaignChatMessageEvent;
import com.accsaber.backend.model.entity.campaign.CampaignCollaboratorStatus;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.campaign.CampaignChatMessageRepository;
import com.accsaber.backend.repository.campaign.CampaignCollaboratorRepository;
import com.accsaber.backend.repository.campaign.CampaignRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.DuplicateUserService;

@ExtendWith(MockitoExtension.class)
class CampaignChatServiceTest {

    @Mock
    private CampaignChatMessageRepository chatRepository;
    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private CampaignCollaboratorRepository collaboratorRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DuplicateUserService duplicateUserService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CampaignChatService service;

    private UUID campaignId;
    private Campaign campaign;
    private User user;

    @BeforeEach
    void setUp() {
        campaignId = UUID.randomUUID();
        campaign = Campaign.builder().id(campaignId).build();
        user = User.builder().id(50L).name("Tester").build();
        when(duplicateUserService.resolvePrimaryUserId(50L)).thenReturn(50L);
    }

    @Test
    void collaboratorCanSendMessageAndContentIsTrimmed() {
        when(campaignRepository.findCreatorIdByIdAndActiveTrue(campaignId)).thenReturn(Optional.of(99L));
        when(collaboratorRepository.existsByCampaign_IdAndUser_IdAndStatusAndActiveTrue(
                campaignId, 50L, CampaignCollaboratorStatus.ACCEPTED)).thenReturn(true);
        when(campaignRepository.findByIdAndActiveTrue(campaignId)).thenReturn(Optional.of(campaign));
        when(userRepository.findByIdAndActiveTrue(50L)).thenReturn(Optional.of(user));
        when(chatRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.sendMessage(50L, campaignId, "  hello team  ");

        ArgumentCaptor<CampaignChatMessage> captor = ArgumentCaptor.forClass(CampaignChatMessage.class);
        verify(chatRepository).save(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("hello team");
        assertThat(captor.getValue().getUser().getId()).isEqualTo(50L);
        assertThat(captor.getValue().getCampaign().getId()).isEqualTo(campaignId);
        assertThat(response.getContent()).isEqualTo("hello team");
        assertThat(response.getAuthorName()).isEqualTo("Tester");
        verify(eventPublisher).publishEvent(any(CampaignChatMessageEvent.class));
    }

    @Test
    void ownerCanSendMessageWithoutCollaboratorCheck() {
        when(campaignRepository.findCreatorIdByIdAndActiveTrue(campaignId)).thenReturn(Optional.of(50L));
        when(campaignRepository.findByIdAndActiveTrue(campaignId)).thenReturn(Optional.of(campaign));
        when(userRepository.findByIdAndActiveTrue(50L)).thenReturn(Optional.of(user));
        when(chatRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.sendMessage(50L, campaignId, "owner here");

        verify(chatRepository).save(any());
    }

    @Test
    void nonMemberCannotSendMessage() {
        when(campaignRepository.findCreatorIdByIdAndActiveTrue(campaignId)).thenReturn(Optional.of(99L));
        when(collaboratorRepository.existsByCampaign_IdAndUser_IdAndStatusAndActiveTrue(
                campaignId, 50L, CampaignCollaboratorStatus.ACCEPTED)).thenReturn(false);

        assertThatThrownBy(() -> service.sendMessage(50L, campaignId, "let me in"))
                .isInstanceOf(ValidationException.class);

        verify(chatRepository, never()).save(any());
    }

    @Test
    void ownerGetsMappedHistoryNewestFirst() {
        when(campaignRepository.findCreatorIdByIdAndActiveTrue(campaignId)).thenReturn(Optional.of(50L));
        CampaignChatMessage message = CampaignChatMessage.builder()
                .id(UUID.randomUUID()).campaign(campaign).user(user).content("hi").build();
        when(chatRepository.findByCampaign_IdOrderByCreatedAtDesc(eq(campaignId), any()))
                .thenReturn(new PageImpl<>(List.of(message)));

        var page = service.getMessages(50L, campaignId, PageRequest.of(0, 50));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getContent()).isEqualTo("hi");
        assertThat(page.getContent().get(0).getAuthorName()).isEqualTo("Tester");
    }
}
