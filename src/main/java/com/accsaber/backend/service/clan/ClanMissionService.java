package com.accsaber.backend.service.clan;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
import com.accsaber.backend.model.dto.EventMissionTargets;
import com.accsaber.backend.model.entity.mission.MissionTemplate;
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
        boolean seasonRunning = standingService.currentSeason().isPresent();
        Map<UUID, MissionResponse.ClanReward> rewards = page.getContent().stream()
                .collect(Collectors.toMap(UserMission::getId, mission -> new MissionResponse.ClanReward(
                        (int) Math.round(clanXpFor(mission)),
                        seasonRunning ? (int) Math.round(standingFor(mission)) : null)));
        MissionResponse.SharedContext context = sharedMissionContextLoader.load(page.getContent(), viewerId)
                .withClanRewards(rewards);
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
        levelService.grantXp(clanId, new ClanXpAward(clanXpFor(mission), ClanXpSource.mission, sourceId, false));
        standingService.currentSeason().ifPresent(season -> standingService.apply(season.getId(), clanId,
                standingFor(mission), ClanStandingSource.mission, sourceId));
    }

    private double clanXpFor(UserMission mission) {
        return clanProperties.getMissionXp() * mission.getTemplate().getXpMultiplier();
    }

    private double standingFor(UserMission mission) {
        return clanProperties.getMissionStanding() * mission.getTemplate().getXpMultiplier();
    }

    public double minRewardedContribution(UserMission mission) {
        MissionTemplate template = mission.getTemplate();
        if (!template.isClan() || template.isPerMember()) {
            return 0.0;
        }
        EventMissionTargets targets = template.getEventTargets();
        Number base = switch (template.getType().getAxis()) {
            case XP -> targets.xp();
            case AP -> targets.ap();
            case COUNT -> targets.count();
            case BINARY -> null;
        };
        return base == null ? 0.0
                : base.doubleValue() / clanProperties.getRosterReferenceMembers() * clanProperties.getMissionMinShare();
    }
}
