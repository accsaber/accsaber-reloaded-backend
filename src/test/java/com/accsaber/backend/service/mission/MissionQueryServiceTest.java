package com.accsaber.backend.service.mission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.entity.mission.MissionPool;
import com.accsaber.backend.model.entity.mission.MissionStatus;
import com.accsaber.backend.model.entity.mission.UserMission;
import com.accsaber.backend.repository.mission.UserMissionRepository;

@ExtendWith(MockitoExtension.class)
class MissionQueryServiceTest {

    private static final Long USER = 5L;

    @Mock
    private UserMissionRepository userMissionRepository;

    @InjectMocks
    private MissionQueryService service;

    private final UserMission weekly = UserMission.builder().pool(MissionPool.weekly).build();
    private final UserMission clanRow = UserMission.builder().pool(MissionPool.clan).build();

    @Test
    void theUnfilteredListsLeaveClanRowsOut() {
        when(userMissionRepository.findCurrentByUser(eq(USER), any())).thenReturn(List.of(weekly, clanRow));
        when(userMissionRepository.findByUser_IdAndStatus(USER, MissionStatus.completed))
                .thenReturn(List.of(clanRow, weekly));

        assertThat(service.listActive(USER)).containsExactly(weekly);
        assertThat(service.listCompleted(USER)).containsExactly(weekly);
    }

    @Test
    void askingForTheClanPoolReturnsTheClanRows() {
        when(userMissionRepository.findCurrentByUserAndPool(eq(USER), eq(MissionPool.clan), any()))
                .thenReturn(List.of(clanRow));

        assertThat(service.listActiveByPool(USER, MissionPool.clan)).containsExactly(clanRow);
    }
}
