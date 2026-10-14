package com.accsaber.backend.service.clan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanSeason;
import com.accsaber.backend.model.entity.clan.ClanStandingSource;
import com.accsaber.backend.model.entity.clan.ClanXpSource;
import com.accsaber.backend.model.dto.EventMissionTargets;
import com.accsaber.backend.model.dto.response.mission.MissionResponse;
import com.accsaber.backend.model.entity.mission.MissionPool;
import com.accsaber.backend.model.entity.mission.MissionStatus;
import com.accsaber.backend.model.entity.mission.MissionType;
import com.accsaber.backend.model.entity.mission.MissionTemplate;
import com.accsaber.backend.model.entity.mission.UserMission;
import com.accsaber.backend.repository.clan.ClanRepository;
import com.accsaber.backend.repository.mission.UserMissionRepository;
import com.accsaber.backend.service.mission.SharedMissionContextLoader;

@ExtendWith(MockitoExtension.class)
class ClanMissionServiceTest {

    @Mock
    private UserMissionRepository userMissionRepository;
    @Mock
    private ClanRepository clanRepository;
    @Mock
    private SharedMissionContextLoader sharedMissionContextLoader;
    @Mock
    private ClanLevelService levelService;
    @Mock
    private ClanStandingService standingService;
    @Spy
    private ClanProperties clanProperties = pinned();

    @InjectMocks
    private ClanMissionService service;

    private final Clan clan = Clan.builder().id(UUID.randomUUID()).build();

    private static ClanProperties pinned() {
        ClanProperties properties = new ClanProperties();
        properties.setMissionXp(400.0);
        properties.setMissionStanding(40.0);
        return properties;
    }

    private UserMission mission(double xpMultiplier) {
        return UserMission.builder().id(UUID.randomUUID()).pool(MissionPool.clan).clan(clan)
                .template(MissionTemplate.builder().xpMultiplier(xpMultiplier).build()).build();
    }

    private UserMission targeted(MissionType type, EventMissionTargets targets) {
        return UserMission.builder().pool(MissionPool.clan).clan(clan)
                .template(MissionTemplate.builder().type(type).pool(MissionPool.clan).eventTargets(targets).build())
                .build();
    }

    @Test
    void aPooledMissionNeedsHalfOfAFiveMemberClansShareToBePaid() {
        EventMissionTargets plays = new EventMissionTargets(null, null, null, null, null, null, 37, null, null, null,
                null, null, null, null);
        EventMissionTargets xp = new EventMissionTargets(null, null, null, null, null, null, null, 7400, null, null,
                null, null, null, null);

        assertThat(service.minRewardedContribution(targeted(MissionType.PLAY_N_MAPS, plays))).isEqualTo(3.7);
        assertThat(service.minRewardedContribution(targeted(MissionType.XP_IN_WINDOW, xp))).isEqualTo(740.0);
    }

    @Test
    void theClanPageShowsWhatACompletionBanks() {
        UserMission mission = UserMission.builder().id(UUID.randomUUID()).pool(MissionPool.clan).clan(clan)
                .status(MissionStatus.active).xpReward(150).template(MissionTemplate.builder()
                        .type(MissionType.SCORES_N).pool(MissionPool.clan).xpMultiplier(1.5).build())
                .build();
        when(clanRepository.findByIdAndActiveTrue(clan.getId())).thenReturn(Optional.of(clan));
        when(clanRepository.requireActive(clan.getId())).thenCallRealMethod();
        when(userMissionRepository.findClanShared(any(), anyBoolean(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(mission)));
        when(sharedMissionContextLoader.load(any(), any())).thenReturn(MissionResponse.SharedContext.EMPTY);
        when(standingService.currentSeason()).thenReturn(Optional.of(ClanSeason.builder().build()));

        MissionResponse response = service.list(clan.getId(), true, null, Pageable.unpaged()).getContent().get(0);

        assertThat(response.getClanXpReward()).isEqualTo(600);
        assertThat(response.getStandingReward()).isEqualTo(60);
        assertThat(response.getXpReward()).isEqualTo(150);
    }

    @Test
    void withNoSeasonRunningTheClanPageShowsNoStanding() {
        UserMission mission = UserMission.builder().id(UUID.randomUUID()).pool(MissionPool.clan).clan(clan)
                .status(MissionStatus.active).template(MissionTemplate.builder()
                        .type(MissionType.SCORES_N).pool(MissionPool.clan).xpMultiplier(1.0).build())
                .build();
        when(clanRepository.findByIdAndActiveTrue(clan.getId())).thenReturn(Optional.of(clan));
        when(clanRepository.requireActive(clan.getId())).thenCallRealMethod();
        when(userMissionRepository.findClanShared(any(), anyBoolean(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(mission)));
        when(sharedMissionContextLoader.load(any(), any())).thenReturn(MissionResponse.SharedContext.EMPTY);
        when(standingService.currentSeason()).thenReturn(Optional.empty());

        MissionResponse response = service.list(clan.getId(), true, null, Pageable.unpaged()).getContent().get(0);

        assertThat(response.getClanXpReward()).isEqualTo(400);
        assertThat(response.getStandingReward()).isNull();
    }

    @Test
    void aPerMemberMissionPaysEveryClearer() {
        assertThat(service.minRewardedContribution(targeted(MissionType.SCORES_N, null))).isZero();
    }

    @Test
    void completionBanksWeightedClanXpUnscaledAndStandingForTheRunningSeason() {
        UserMission mission = mission(1.5);
        ClanSeason season = ClanSeason.builder().id(UUID.randomUUID()).build();
        when(standingService.currentSeason()).thenReturn(Optional.of(season));

        service.bankCompletion(mission);

        ArgumentCaptor<ClanXpAward> award = ArgumentCaptor.forClass(ClanXpAward.class);
        verify(levelService).grantXp(any(), award.capture());
        assertThat(award.getValue()).isEqualTo(
                new ClanXpAward(600.0, ClanXpSource.mission, mission.getId().toString(), false));
        verify(standingService).apply(season.getId(), clan.getId(), 60.0, ClanStandingSource.mission,
                mission.getId().toString());
    }

    @Test
    void withNoSeasonRunningOnlyTheXpIsBanked() {
        when(standingService.currentSeason()).thenReturn(Optional.empty());

        service.bankCompletion(mission(1.0));

        verify(levelService).grantXp(any(), any());
        verify(standingService, never()).apply(any(), any(), anyDouble(), any(), any());
    }

    @Test
    void contributorsOfAnotherClansMissionAreNotFound() {
        UserMission mission = mission(1.0);
        when(userMissionRepository.findSharedById(mission.getId())).thenReturn(Optional.of(mission));

        assertThatThrownBy(() -> service.contributors(UUID.randomUUID(), mission.getId(), Pageable.unpaged()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(sharedMissionContextLoader, never()).contributors(any(), any());
    }

    @Test
    void contributorsOfTheClansOwnMissionAreListed() {
        UserMission mission = mission(1.0);
        when(userMissionRepository.findSharedById(mission.getId())).thenReturn(Optional.of(mission));

        service.contributors(clan.getId(), mission.getId(), Pageable.unpaged());

        verify(sharedMissionContextLoader).contributors(mission.getId(), Pageable.unpaged());
    }
}
