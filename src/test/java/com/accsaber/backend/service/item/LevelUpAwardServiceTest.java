package com.accsaber.backend.service.item;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.dto.response.milestone.LevelResponse;
import com.accsaber.backend.model.entity.mission.Event;
import com.accsaber.backend.model.entity.mission.MissionPool;
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
    void missionXpFromAClanTemplateLandsInTheClanBucket() {
        when(userRepository.findTotalXpById(50L)).thenReturn(Optional.of(0.0));
        when(levelService.calculateLevel(any())).thenReturn(LevelResponse.builder().level(0).build());
        service.addMissionXp(50L, MissionTemplate.builder().pool(MissionPool.clan).build(), 100.0);

        verify(userRepository).addClanXp(50L, 100.0);
        verify(userRepository, never()).addMissionXp(any(), any());
        verify(userRepository).addXp(50L, 100.0);
    }

    static Stream<Arguments> bucketedXpGrants() {
        return Stream.of(
                Arguments.of("campaign",
                        (BiConsumer<LevelUpAwardService, Double>) (s, xp) -> s.addCampaignXp(50L, xp),
                        (Consumer<UserRepository>) r -> verify(r, never()).addCampaignXp(any(), any())),
                Arguments.of("event",
                        (BiConsumer<LevelUpAwardService, Double>) (s, xp) -> s.addEventXp(50L, xp),
                        (Consumer<UserRepository>) r -> verify(r, never()).addEventXp(any(), any())),
                Arguments.of("clan",
                        (BiConsumer<LevelUpAwardService, Double>) (s, xp) -> s.addClanXp(50L, xp),
                        (Consumer<UserRepository>) r -> verify(r, never()).addClanXp(any(), any())));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bucketedXpGrants")
    void bucketedXpIgnoresNonPositiveDelta(String bucket, BiConsumer<LevelUpAwardService, Double> grant,
            Consumer<UserRepository> bucketUntouched) {
        grant.accept(service, 0.0);

        bucketUntouched.accept(userRepository);
        verify(userRepository, never()).addXp(any(), any());
    }
}
