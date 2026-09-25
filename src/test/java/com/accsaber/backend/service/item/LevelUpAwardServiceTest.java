package com.accsaber.backend.service.item;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.dto.response.milestone.LevelResponse;
import com.accsaber.backend.model.entity.mission.Event;
import com.accsaber.backend.model.entity.mission.MissionTemplate;
import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.repository.milestone.LevelThresholdRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.milestone.LevelService;

@ExtendWith(MockitoExtension.class)
class LevelUpAwardServiceTest {

    @Mock
    private LevelService levelService;
    @Mock
    private LevelThresholdRepository levelThresholdRepository;
    @Mock
    private ItemRepository itemRepository;
    @Mock
    private ItemService itemService;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private LevelUpAwardService service;

    @Test
    void addCampaignXpIncrementsCampaignBucketAndTotal() {
        when(userRepository.findTotalXpById(50L)).thenReturn(Optional.of(0.0));
        when(levelService.calculateLevel(any())).thenReturn(LevelResponse.builder().level(0).build());

        service.addCampaignXp(50L, 100.0);

        verify(userRepository).addCampaignXp(50L, 100.0);
        verify(userRepository).addXp(50L, 100.0);
    }

    @Test
    void addCampaignXpIgnoresNonPositiveDelta() {
        service.addCampaignXp(50L, 0.0);

        verify(userRepository, never()).addCampaignXp(any(), any());
        verify(userRepository, never()).addXp(any(), any());
    }

    @Test
    void missionXpFromAnEventTemplateLandsInTheEventBucket() {
        when(userRepository.findTotalXpById(50L)).thenReturn(Optional.of(0.0));
        when(levelService.calculateLevel(any())).thenReturn(LevelResponse.builder().level(0).build());
        MissionTemplate template = MissionTemplate.builder().event(Event.builder().build()).build();

        service.addMissionXp(50L, template, 100.0);

        verify(userRepository).addEventXp(50L, 100.0);
        verify(userRepository, never()).addMissionXp(any(), any());
        verify(userRepository).addXp(50L, 100.0);
    }

    @Test
    void missionXpWithoutAnEventLandsInTheMissionBucket() {
        when(userRepository.findTotalXpById(50L)).thenReturn(Optional.of(0.0));
        when(levelService.calculateLevel(any())).thenReturn(LevelResponse.builder().level(0).build());

        service.addMissionXp(50L, MissionTemplate.builder().build(), 100.0);

        verify(userRepository).addMissionXp(50L, 100.0);
        verify(userRepository, never()).addEventXp(any(), any());
        verify(userRepository).addXp(50L, 100.0);
    }

    @Test
    void addEventXpIgnoresNonPositiveDelta() {
        service.addEventXp(50L, 0.0);

        verify(userRepository, never()).addEventXp(any(), any());
        verify(userRepository, never()).addXp(any(), any());
    }
}
