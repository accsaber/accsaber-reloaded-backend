package com.accsaber.backend.service.score;

import java.util.HashSet;
import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.campaign.UserCampaignStatus;
import com.accsaber.backend.repository.campaign.CampaignDifficultyRepository;
import com.accsaber.backend.repository.campaign.UserCampaignRepository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignScoreGate {

    private final CampaignDifficultyRepository campaignDifficultyRepository;
    private final UserCampaignRepository userCampaignRepository;

    private volatile Set<String> campaignBlIds = Set.of();
    private volatile Set<String> campaignSsIds = Set.of();
    private volatile Set<Long> participantIds = Set.of();

    @PostConstruct
    public void init() {
        refresh();
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 300_000)
    public void scheduledRefresh() {
        refresh();
    }

    public void refresh() {
        Set<String> blIds = new HashSet<>();
        Set<String> ssIds = new HashSet<>();
        for (Object[] row : campaignDifficultyRepository.findCampaignIngestLeaderboardIds()) {
            if (row[0] != null) {
                blIds.add((String) row[0]);
            }
            if (row[1] != null) {
                ssIds.add((String) row[1]);
            }
        }
        campaignBlIds = Set.copyOf(blIds);
        campaignSsIds = Set.copyOf(ssIds);
        participantIds = Set.copyOf(userCampaignRepository
                .findUserIdsByStatusAndCampaignReleased(UserCampaignStatus.IN_PROGRESS, CampaignStatus.DRAFT));
        log.info("Refreshed campaign score gate: {} BL, {} SS, {} participants",
                campaignBlIds.size(), campaignSsIds.size(), participantIds.size());
    }

    public boolean matchesBlLeaderboard(String leaderboardId) {
        return leaderboardId != null && campaignBlIds.contains(leaderboardId);
    }

    public boolean matchesSsLeaderboard(String leaderboardId) {
        return leaderboardId != null && campaignSsIds.contains(leaderboardId);
    }

    public boolean isParticipant(Long userId) {
        return userId != null && participantIds.contains(userId);
    }
}
