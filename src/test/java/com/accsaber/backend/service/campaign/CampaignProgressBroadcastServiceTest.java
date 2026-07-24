package com.accsaber.backend.service.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.dto.response.campaign.CampaignDifficultyResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignResponse;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.event.CampaignCompletedEvent;
import com.accsaber.backend.model.event.CampaignNodeCompletedEvent;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.websocket.server.CampaignProgressWebSocketHandler;

@ExtendWith(MockitoExtension.class)
class CampaignProgressBroadcastServiceTest {

    @Mock
    private CampaignProgressWebSocketHandler campaignProgressHandler;
    @Mock
    private CampaignService campaignService;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CampaignProgressBroadcastService service;

    private static final Long USER_ID = 76561198000000000L;

    private User player() {
        return User.builder().id(USER_ID).name("Tikugato").country("us")
                .avatarUrl("https://cdn.example/a.png").cdnAvatarUrl("https://cdn.accsaber/a.webp").build();
    }

    private CampaignResponse campaign(UUID id) {
        return CampaignResponse.builder().id(id).name("Endless Climb").slug("endless-climb")
                .status(CampaignStatus.PUBLISHED).build();
    }

    @Test
    void nodeCompletedEmbedsCampaignNodeAndPlayerDtos() {
        UUID campaignId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        when(userRepository.findByIdAndActiveTrue(USER_ID)).thenReturn(Optional.of(player()));
        when(campaignService.getCampaignSummary(campaignId)).thenReturn(campaign(campaignId));
        when(campaignService.getCampaignNode(nodeId)).thenReturn(CampaignDifficultyResponse.builder()
                .id(nodeId).songName("Reality Check").mapAuthor("Mapper").xp(new BigDecimal("120")).build());

        service.onNodeCompleted(new CampaignNodeCompletedEvent(USER_ID, campaignId, nodeId,
                Instant.parse("2026-07-03T21:00:00Z")));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(campaignProgressHandler).broadcast(json.capture());
        assertThat(json.getValue())
                .contains("\"type\":\"node_completed\"")
                .contains("\"slug\":\"endless-climb\"")
                .contains("\"songName\":\"Reality Check\"")
                .contains("\"userName\":\"Tikugato\"")
                .contains("\"cdnAvatarUrl\":\"https://cdn.accsaber/a.webp\"")
                .contains(String.valueOf(USER_ID))
                .contains("2026-07-03T21:00:00Z");
    }

    @Test
    void campaignCompletedEmbedsCampaignAndPlayerWithoutNode() {
        UUID campaignId = UUID.randomUUID();
        when(userRepository.findByIdAndActiveTrue(USER_ID)).thenReturn(Optional.of(player()));
        when(campaignService.getCampaignSummary(campaignId)).thenReturn(campaign(campaignId));

        service.onCampaignCompleted(new CampaignCompletedEvent(USER_ID, campaignId, CampaignStatus.CURATED,
                Instant.parse("2026-07-03T21:00:00Z")));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(campaignProgressHandler).broadcast(json.capture());
        assertThat(json.getValue())
                .contains("\"type\":\"campaign_completed\"")
                .contains("\"slug\":\"endless-climb\"")
                .contains("\"userName\":\"Tikugato\"")
                .doesNotContain("\"node\":")
                .doesNotContain("songName");
    }
}
