package com.accsaber.backend.service.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.campaign.CampaignLeaderboardEntry;
import com.accsaber.backend.model.dto.response.campaign.CampaignNodeScoreEntry;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignDifficulty;
import com.accsaber.backend.model.entity.campaign.CampaignLeaderboardBoard;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.campaign.UserCampaignStatus;
import com.accsaber.backend.repository.campaign.CampaignDifficultyRepository;
import com.accsaber.backend.repository.campaign.CampaignLeaderboardRepository;
import com.accsaber.backend.repository.campaign.CampaignRepository;

@ExtendWith(MockitoExtension.class)
class CampaignLeaderboardServiceTest {

    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private CampaignDifficultyRepository campaignDifficultyRepository;
    @Mock
    private CampaignLeaderboardRepository leaderboardRepository;

    @InjectMocks
    private CampaignLeaderboardService service;

    private UUID campaignId;
    private Campaign published;
    private final PageRequest paging = PageRequest.of(0, 50);

    @BeforeEach
    void setUp() {
        campaignId = UUID.randomUUID();
        published = Campaign.builder().id(campaignId).status(CampaignStatus.PUBLISHED).active(true).build();
    }

    @Test
    void completionsBoardAssignsRanksAndMapsPlayer() {
        when(campaignRepository.findByIdAndActiveTrue(campaignId)).thenReturn(Optional.of(published));
        Object[] alice = { 1L, "alice", "US", "a1", "cdn1", Instant.parse("2026-01-01T10:00:00Z") };
        Object[] carol = { 3L, "carol", "US", "a3", "cdn3", Instant.parse("2026-01-02T10:00:00Z") };
        when(leaderboardRepository.completions(eq(campaignId), any()))
                .thenReturn(new PageImpl<>(List.<Object[]>of(alice, carol), paging, 2));

        Page<CampaignLeaderboardEntry> page = service.getBoard(campaignId, CampaignLeaderboardBoard.COMPLETIONS,
                null, paging);

        assertThat(page.getContent()).hasSize(2);
        CampaignLeaderboardEntry first = page.getContent().get(0);
        assertThat(first.getRank()).isEqualTo(1);
        assertThat(first.getPlayer().getUserId()).isEqualTo("1");
        assertThat(first.getPlayer().getUserName()).isEqualTo("alice");
        assertThat(first.getPlayer().getCdnAvatarUrl()).isEqualTo("cdn1");
        assertThat(first.getCompletedAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
        assertThat(page.getContent().get(1).getRank()).isEqualTo(2);
    }

    @Test
    void averageBoardMapsAccuracyApAndNodes() {
        when(campaignRepository.findByIdAndActiveTrue(campaignId)).thenReturn(Optional.of(published));
        Object[] carol = { 3L, "carol", "US", "a3", "cdn3", new BigDecimal("0.98875"), new BigDecimal("295"), 2L };
        when(leaderboardRepository.averagesByAccuracy(eq(campaignId), any()))
                .thenReturn(new PageImpl<>(List.<Object[]>of(carol), paging, 1));

        CampaignLeaderboardEntry entry = service.getBoard(campaignId, CampaignLeaderboardBoard.AVG_ACCURACY,
                null, paging).getContent().get(0);

        assertThat(entry.getRank()).isEqualTo(1);
        assertThat(entry.getAverageAccuracy()).isEqualByComparingTo("0.98875");
        assertThat(entry.getAverageAp()).isEqualByComparingTo("295");
        assertThat(entry.getNodesCounted()).isEqualTo(2);
    }

    @Test
    void progressBoardSetsStatusCompletedNodesAndTotalNodes() {
        when(campaignRepository.findByIdAndActiveTrue(campaignId)).thenReturn(Optional.of(published));
        when(campaignDifficultyRepository.countByCampaign_IdAndBarrierFalseAndActiveTrue(campaignId)).thenReturn(3L);
        Object[] bob = { 2L, "bob", "GB", "a2", "cdn2", "in_progress", null, 1L };
        when(leaderboardRepository.progress(eq(campaignId), isNull(), any()))
                .thenReturn(new PageImpl<>(List.<Object[]>of(bob), paging, 1));

        CampaignLeaderboardEntry entry = service.getBoard(campaignId, CampaignLeaderboardBoard.PROGRESS,
                null, paging).getContent().get(0);

        assertThat(entry.getProgressStatus()).isEqualTo(UserCampaignStatus.IN_PROGRESS);
        assertThat(entry.getCompletedNodes()).isEqualTo(1);
        assertThat(entry.getTotalNodes()).isEqualTo(3);
        assertThat(entry.getRank()).isNull();
    }

    @Test
    void draftCampaignHasNoLeaderboards() {
        Campaign draft = Campaign.builder().id(campaignId).status(CampaignStatus.DRAFT).active(true).build();
        when(campaignRepository.findByIdAndActiveTrue(campaignId)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.getBoard(campaignId, CampaignLeaderboardBoard.COMPLETIONS, null, paging))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void nodeBoardMapsScoreAccuracyAndAp() {
        UUID nodeId = UUID.randomUUID();
        when(campaignRepository.findByIdAndActiveTrue(campaignId)).thenReturn(Optional.of(published));
        CampaignDifficulty node = CampaignDifficulty.builder().id(nodeId).campaign(published).build();
        when(campaignDifficultyRepository.findByIdAndActiveTrue(nodeId)).thenReturn(Optional.of(node));
        Object[] carol = { 3L, "carol", "US", "a3", "cdn3", 990000, new BigDecimal("0.99"), new BigDecimal("320") };
        when(leaderboardRepository.nodeScores(eq(nodeId), any()))
                .thenReturn(new PageImpl<>(List.<Object[]>of(carol), paging, 1));

        CampaignNodeScoreEntry entry = service.getNodeBoard(campaignId, nodeId, paging).getContent().get(0);

        assertThat(entry.getRank()).isEqualTo(1);
        assertThat(entry.getPlayer().getUserName()).isEqualTo("carol");
        assertThat(entry.getScore()).isEqualTo(990000);
        assertThat(entry.getAccuracy()).isEqualByComparingTo("0.99");
        assertThat(entry.getAp()).isEqualByComparingTo("320");
    }

    @Test
    void nodeBoardRejectsNodeFromAnotherCampaign() {
        UUID nodeId = UUID.randomUUID();
        Campaign otherCampaign = Campaign.builder().id(UUID.randomUUID()).status(CampaignStatus.PUBLISHED).build();
        when(campaignRepository.findByIdAndActiveTrue(campaignId)).thenReturn(Optional.of(published));
        CampaignDifficulty foreignNode = CampaignDifficulty.builder().id(nodeId).campaign(otherCampaign).build();
        when(campaignDifficultyRepository.findByIdAndActiveTrue(nodeId)).thenReturn(Optional.of(foreignNode));

        assertThatThrownBy(() -> service.getNodeBoard(campaignId, nodeId, paging))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
