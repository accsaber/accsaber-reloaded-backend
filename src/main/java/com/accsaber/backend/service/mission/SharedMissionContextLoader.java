package com.accsaber.backend.service.mission;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.accsaber.backend.model.dto.response.mission.MissionResponse;
import com.accsaber.backend.model.entity.mission.UserMission;
import com.accsaber.backend.repository.mission.MissionContributionRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SharedMissionContextLoader {

    private final MissionContributionRepository contributionRepository;

    public MissionResponse.SharedContext load(List<UserMission> missions, Long viewerId) {
        List<UUID> ids = missions.stream()
                .filter(UserMission::isCommunity)
                .map(UserMission::getId)
                .toList();
        if (ids.isEmpty()) {
            return MissionResponse.SharedContext.EMPTY;
        }
        Map<UUID, Long> contributors = contributionRepository.countContributors(ids).stream()
                .collect(Collectors.toMap(
                        MissionContributionRepository.ContributorCountView::getMissionId,
                        MissionContributionRepository.ContributorCountView::getContributors));
        if (viewerId == null) {
            return new MissionResponse.SharedContext(contributors, Map.of());
        }
        Map<UUID, Double> yours = new HashMap<>();
        for (MissionContributionRepository.ContributionView view : contributionRepository
                .findContributionsByUser(viewerId, ids)) {
            yours.put(view.getMissionId(), view.getContribution());
        }
        return new MissionResponse.SharedContext(contributors, yours);
    }
}
