package com.accsaber.backend.service.clan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanSeason;
import com.accsaber.backend.model.entity.clan.ClanStandingSource;
import com.accsaber.backend.model.entity.clan.ClanXpSource;
import com.accsaber.backend.model.entity.mission.MissionPool;
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

    @Test
    void endingAClanExpiresEveryOpenRowItOwns() {
        service.endAll(clan.getId());

        verify(userMissionRepository).expireActiveForClan(clan.getId());
    }
}
