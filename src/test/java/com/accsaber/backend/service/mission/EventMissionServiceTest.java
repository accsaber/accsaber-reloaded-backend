package com.accsaber.backend.service.mission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.entity.mission.Event;
import com.accsaber.backend.model.entity.mission.MissionPool;
import com.accsaber.backend.model.entity.mission.MissionStatus;
import com.accsaber.backend.model.entity.mission.MissionTemplate;
import com.accsaber.backend.model.entity.mission.MissionType;
import com.accsaber.backend.model.entity.mission.UserEventProfile;
import com.accsaber.backend.model.entity.mission.UserMission;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.mission.EventRepository;
import com.accsaber.backend.repository.mission.MissionTemplateRepository;
import com.accsaber.backend.repository.mission.UserEventProfileRepository;
import com.accsaber.backend.repository.mission.UserMissionRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.item.ItemService;
import com.accsaber.backend.service.item.LevelUpAwardService;

@ExtendWith(MockitoExtension.class)
class EventMissionServiceTest {

        private static final Long USER_ID = 76561198000000001L;

        @Mock
        private EventRepository eventRepository;
        @Mock
        private MissionTemplateRepository templateRepository;
        @Mock
        private UserMissionRepository userMissionRepository;
        @Mock
        private UserEventProfileRepository profileRepository;
        @Mock
        private UserRepository userRepository;
        @Mock
        private CategoryRepository categoryRepository;
        @Mock
        private MapDifficultyRepository mapDifficultyRepository;
        @Mock
        private LevelUpAwardService levelUpAwardService;
        @Mock
        private ItemService itemService;
        @Mock
        private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

        @InjectMocks
        private EventMissionService service;

        private Event event;
        private MissionTemplate repeatable;
        private MissionTemplate oneShot;
        private UserEventProfile profile;

        @BeforeEach
        void setUp() {
                Instant start = Instant.now().minus(2, ChronoUnit.DAYS);
                event = Event.builder()
                                .id(UUID.randomUUID())
                                .title("Alpha's End")
                                .startsAt(start)
                                .endsAt(start.plus(28, ChronoUnit.DAYS))
                                .bonusXp(5000)
                                .active(true)
                                .build();
                repeatable = template(MissionType.PLAY_N_MAPS, true);
                oneShot = template(MissionType.SCORES_N, false);
                profile = UserEventProfile.builder()
                                .id(UUID.randomUUID())
                                .event(event)
                                .user(User.builder().id(USER_ID).build())
                                .unlockedWeek(1)
                                .build();
                lenient().when(profileRepository.findByEvent_IdAndUser_Id(event.getId(), USER_ID))
                                .thenReturn(Optional.of(profile));
                lenient().when(userRepository.getReferenceById(USER_ID))
                                .thenReturn(User.builder().id(USER_ID).build());
        }

        private MissionTemplate template(MissionType type, boolean isRepeatable) {
                return MissionTemplate.builder()
                                .id(UUID.randomUUID())
                                .code(type.name().toLowerCase() + (isRepeatable ? "_repeat" : ""))
                                .name(type.name())
                                .type(type)
                                .pool(MissionPool.event)
                                .event(event)
                                .fixedXp(200)
                                .repeatable(isRepeatable)
                                .active(true)
                                .build();
        }

        private UserMission completedMission(MissionTemplate t) {
                return UserMission.builder()
                                .id(UUID.randomUUID())
                                .template(t)
                                .pool(MissionPool.event)
                                .status(MissionStatus.completed)
                                .progressCount(20)
                                .progressAp(BigDecimal.ZERO)
                                .xpReward(200)
                                .expiresAt(event.getEndsAt())
                                .build();
        }

        @Nested
        class RepeatableRespawn {

                @Test
                void completingRepeatableSpawnsFreshMissionWithZeroProgress() {
                        when(templateRepository.findActiveByEvent(event.getId()))
                                        .thenReturn(List.of(repeatable));
                        when(userMissionRepository.countByUser_IdAndTemplate_IdAndStatus(USER_ID,
                                        repeatable.getId(), MissionStatus.completed)).thenReturn(1L);
                        when(userMissionRepository.findCompletedTemplateIdsForEvent(USER_ID, event.getId()))
                                        .thenReturn(List.of(repeatable.getId()));

                        service.onEventMissionCompleted(completedMission(repeatable), USER_ID);

                        ArgumentCaptor<UserMission> captor = ArgumentCaptor.forClass(UserMission.class);
                        verify(userMissionRepository).save(captor.capture());
                        UserMission respawned = captor.getValue();
                        assertThat(respawned.getTemplate()).isEqualTo(repeatable);
                        assertThat(respawned.getProgressCount()).isZero();
                        assertThat(respawned.getStatus()).isEqualTo(MissionStatus.active);
                        assertThat(respawned.getXpReward()).isEqualTo(200);
                }

                @Test
                void completingNonRepeatableDoesNotSpawnAnything() {
                        when(templateRepository.findActiveByEvent(event.getId()))
                                        .thenReturn(List.of(oneShot));
                        when(userMissionRepository.findCompletedTemplateIdsForEvent(USER_ID, event.getId()))
                                        .thenReturn(List.of(oneShot.getId()));

                        service.onEventMissionCompleted(completedMission(oneShot), USER_ID);

                        verify(userMissionRepository, never()).save(any());
                }

                @Test
                void repeatableStopsRespawningAfterTheEventCloses() {
                        event.setEndsAt(Instant.now().minus(1, ChronoUnit.DAYS));
                        when(templateRepository.findActiveByEvent(event.getId()))
                                        .thenReturn(List.of(repeatable));
                        when(userMissionRepository.findCompletedTemplateIdsForEvent(USER_ID, event.getId()))
                                        .thenReturn(List.of(repeatable.getId()));

                        service.onEventMissionCompleted(completedMission(repeatable), USER_ID);

                        verify(userMissionRepository, never()).save(any());
                }
        }

        @Nested
        class OnlyFirstCompletionCountsForEventProgress {

                @Test
                void repeatCompletionsDoNotInflateMissionsCompleted() {
                        when(templateRepository.findActiveByEvent(event.getId()))
                                        .thenReturn(List.of(repeatable, oneShot));
                        lenient().when(userMissionRepository.countByUser_IdAndTemplate_IdAndStatus(anyLong(),
                                        any(), any())).thenReturn(3L);
                        when(userMissionRepository.findCompletedTemplateIdsForEvent(USER_ID, event.getId()))
                                        .thenReturn(List.of(repeatable.getId()));

                        service.onEventMissionCompleted(completedMission(repeatable), USER_ID);

                        assertThat(profile.getMissionsCompleted()).isEqualTo(1);
                }

                @Test
                void repeatCompletionsAwardTheEventBonusOnlyOnce() {
                        when(templateRepository.findActiveByEvent(event.getId()))
                                        .thenReturn(List.of(repeatable));
                        lenient().when(userMissionRepository.countByUser_IdAndTemplate_IdAndStatus(anyLong(),
                                        any(), any())).thenReturn(1L);
                        when(userMissionRepository.findCompletedTemplateIdsForEvent(USER_ID, event.getId()))
                                        .thenReturn(List.of(repeatable.getId()));

                        int first = service.onEventMissionCompleted(completedMission(repeatable), USER_ID);
                        int second = service.onEventMissionCompleted(completedMission(repeatable), USER_ID);

                        assertThat(first).isEqualTo(5000);
                        assertThat(second).isZero();
                        verify(levelUpAwardService, times(1))
                                        .addMissionXp(USER_ID, BigDecimal.valueOf(5000));
                        assertThat(profile.getBonusAwardedAt()).isNotNull();
                }

                @Test
                void oneCompletionOfARepeatableUnlocksTheNextWeek() {
                        MissionTemplate weekTwo = template(MissionType.SCORES_N, false);
                        weekTwo.setUnlocksAt(event.getStartsAt().plus(7, ChronoUnit.DAYS));
                        when(templateRepository.findActiveByEvent(event.getId()))
                                        .thenReturn(List.of(repeatable, weekTwo));
                        lenient().when(userMissionRepository.countByUser_IdAndTemplate_IdAndStatus(anyLong(),
                                        any(), any())).thenReturn(1L);
                        when(userMissionRepository.findCompletedTemplateIdsForEvent(USER_ID, event.getId()))
                                        .thenReturn(List.of(repeatable.getId()));
                        lenient().when(userMissionRepository.findTemplateStatusesByUserAndEvent(USER_ID,
                                        event.getId())).thenReturn(List.of());

                        service.onEventMissionCompleted(completedMission(repeatable), USER_ID);

                        assertThat(profile.getUnlockedWeek()).isEqualTo(2);
                }
        }
}
