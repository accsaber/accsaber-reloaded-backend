package com.accsaber.backend.service.clan;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.response.mission.MissionContributorResponse;
import com.accsaber.backend.model.dto.response.mission.MissionResponse;
import com.accsaber.backend.model.entity.clan.ClanStandingSource;
import com.accsaber.backend.model.entity.clan.ClanXpSource;
import com.accsaber.backend.model.entity.mission.UserMission;
import com.accsaber.backend.repository.clan.ClanRepository;
import com.accsaber.backend.repository.mission.UserMissionRepository;
import com.accsaber.backend.service.mission.SharedMissionContextLoader;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClanMissionService {

    private final UserMissionRepository userMissionRepository;
    private final ClanRepository clanRepository;
    private final SharedMissionContextLoader sharedMissionContextLoader;
    private final ClanLevelService levelService;
    private final ClanStandingService standingService;
    private final ClanProperties clanProperties;

    public Page<MissionResponse> list(UUID clanId, boolean current, Long viewerId, Pageable pageable) {
        clanRepository.findByIdAndActiveTrue(clanId).orElseThrow(() -> new ResourceNotFoundException("Clan", clanId));
        Page<UserMission> page = userMissionRepository.findClanShared(clanId, current, Instant.now(), pageable);
        MissionResponse.SharedContext context = sharedMissionContextLoader.load(page.getContent(), viewerId);
        return page.map(mission -> MissionResponse.from(mission, context));
    }

    public Page<MissionContributorResponse> contributors(UUID clanId, UUID missionId, Pageable pageable) {
        userMissionRepository.findSharedById(missionId)
                .filter(mission -> mission.getClan() != null && mission.getClan().getId().equals(clanId))
                .orElseThrow(() -> new ResourceNotFoundException("ClanMission", missionId));
        return sharedMissionContextLoader.contributors(missionId, pageable);
    }

    @Transactional
    public void bankCompletion(UserMission mission) {
        UUID clanId = mission.getClan().getId();
        String sourceId = mission.getId().toString();
        double weight = mission.getTemplate().getXpMultiplier();
        levelService.grantXp(clanId, new ClanXpAward(clanProperties.getMissionXp() * weight, ClanXpSource.mission,
                sourceId, false));
        standingService.currentSeason().ifPresent(season -> standingService.apply(season.getId(), clanId,
                clanProperties.getMissionStanding() * weight, ClanStandingSource.mission, sourceId));
    }

    @Transactional
    public void endAll(UUID clanId) {
        userMissionRepository.expireActiveForClan(clanId);
    }
}
