package com.accsaber.backend.service.mission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.mission.CommunityMissionContribution;
import com.accsaber.backend.model.entity.mission.Event;
import com.accsaber.backend.model.entity.mission.MissionPool;
import com.accsaber.backend.model.entity.mission.MissionStatus;
import com.accsaber.backend.model.entity.mission.MissionTemplate;
import com.accsaber.backend.model.entity.mission.MissionType;
import com.accsaber.backend.model.entity.mission.UserMission;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.mission.CommunityMissionContributionRepository;
import com.accsaber.backend.repository.mission.MissionTemplateRepository;
import com.accsaber.backend.repository.mission.UserMissionRepository;
import com.accsaber.backend.service.item.ItemService;
import com.accsaber.backend.service.item.LevelUpAwardService;

@ExtendWith(MockitoExtension.class)
class CommunityMissionServiceTest {

    @Mock
    private MissionTemplateRepository templateRepository;
    @Mock
    private UserMissionRepository userMissionRepository;
    @Mock
    private CommunityMissionContributionRepository contributionRepository;
    @Mock
    private CommunityContextLoader communityContextLoader;
    @Mock
    private MissionRowFactory missionRowFactory;
    @Mock
    private LevelUpAwardService levelUpAwardService;
    @Mock
    private ItemService itemService;
    @Mock
    private MissionProgressService missionProgressService;
    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private CommunityMissionService service;

    private Event event;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.now();
        event = Event.builder()
                .id(UUID.randomUUID())
                .title("Accursed: Totality")
                .startsAt(now.minus(21, ChronoUnit.DAYS))
                .endsAt(now.plus(21, ChronoUnit.DAYS))
                .active(true)
                .build();
    }

    @SuppressWarnings("unchecked")
    private void runTransactionsInline() {
        lenient().doAnswer(call -> {
            ((Consumer<TransactionStatus>) call.getArgument(0)).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        lenient().when(transactionTemplate.execute(any()))
                .thenAnswer(call -> ((TransactionCallback<Object>) call.getArgument(0)).doInTransaction(null));
    }

    private MissionTemplate weeklyTemplate(Instant unlocksAt, Instant completableUntil) {
        return MissionTemplate.builder()
                .id(UUID.randomUUID())
                .code("accursed_totality_community")
                .name("Gathering")
                .description("The community plays {count} ranked maps together this week.")
                .type(MissionType.PLAY_N_MAPS)
                .pool(MissionPool.community)
                .event(event)
                .unlocksAt(unlocksAt)
                .completableUntil(completableUntil)
                .fixedXp(250)
                .active(true)
                .build();
    }

    @Nested
    class WeeklyWindow {

        @Test
        void aWeeklyMissionIsNotReopenedOnceItsWeekIsOver() {
            MissionTemplate lastWeek = weeklyTemplate(
                    now.minus(21, ChronoUnit.DAYS), now.minus(14, ChronoUnit.DAYS));
            when(templateRepository.findActiveCommunityTemplates()).thenReturn(List.of(lastWeek));
            when(userMissionRepository.findTemplateIdsWithActiveCommunityMission()).thenReturn(List.of());

            assertThat(service.openMissing()).isZero();

            verify(userMissionRepository, never()).save(any());
            verify(missionRowFactory, never()).build(any(), any(), any());
        }

        @Test
        void aWeeklyMissionInsideItsWeekIsOpened() {
            MissionTemplate thisWeek = weeklyTemplate(
                    now.minus(1, ChronoUnit.DAYS), now.plus(6, ChronoUnit.DAYS));
            when(templateRepository.findActiveCommunityTemplates()).thenReturn(List.of(thisWeek));
            when(userMissionRepository.findTemplateIdsWithActiveCommunityMission()).thenReturn(List.of());
            when(userMissionRepository.countByTemplate_IdAndUserIsNullAndStatus(
                    thisWeek.getId(), MissionStatus.completed)).thenReturn(0L);
            when(missionRowFactory.build(null, thisWeek, event)).thenReturn(new UserMission());
            runTransactionsInline();

            assertThat(service.openMissing()).isEqualTo(1);

            verify(userMissionRepository).save(any(UserMission.class));
        }

        @Test
        void aWeeklyMissionAlreadyClearedIsNotReopenedWithinItsWeek() {
            MissionTemplate thisWeek = weeklyTemplate(
                    now.minus(1, ChronoUnit.DAYS), now.plus(6, ChronoUnit.DAYS));
            when(templateRepository.findActiveCommunityTemplates()).thenReturn(List.of(thisWeek));
            when(userMissionRepository.findTemplateIdsWithActiveCommunityMission()).thenReturn(List.of());
            when(userMissionRepository.countByTemplate_IdAndUserIsNullAndStatus(
                    thisWeek.getId(), MissionStatus.completed)).thenReturn(1L);

            assertThat(service.openMissing()).isZero();

            verify(userMissionRepository, never()).save(any());
        }

        @Test
        void aMissionWhoseWeekRanOutUnfinishedPaysNobody() {
            UUID missionId = UUID.randomUUID();
            UserMission expired = communityRow(missionId, MissionStatus.expired);
            when(userMissionRepository.findCommunityById(missionId)).thenReturn(Optional.of(expired));

            service.payRewards(missionId);

            verify(contributionRepository, never()).findUnrewarded(any(), any());
            verify(contributionRepository, never()).markRewarded(any(), anyLong(), any());
            verify(levelUpAwardService, never()).addMissionXp(anyLong(), any(), any());
            verify(itemService, never()).awardSystem(anyLong(), any(), any(), any(), any());
        }
    }

    @Nested
    class Payout {

        @Test
        void everyContributorIsPaidOldestFirst() {
            UUID missionId = UUID.randomUUID();
            UserMission completed = communityRow(missionId, MissionStatus.completed);
            when(userMissionRepository.findCommunityById(missionId)).thenReturn(Optional.of(completed));
            when(contributionRepository.findUnrewarded(eq(missionId), any()))
                    .thenReturn(List.of(contribution(11L), contribution(22L)))
                    .thenReturn(List.of());
            when(contributionRepository.markRewarded(eq(missionId), anyLong(), any())).thenReturn(1);
            runTransactionsInline();

            service.payRewards(missionId);

            InOrder order = inOrder(levelUpAwardService);
            order.verify(levelUpAwardService).addMissionXp(eq(11L), any(), eq(250.0));
            order.verify(levelUpAwardService).addMissionXp(eq(22L), any(), eq(250.0));
            verify(itemService).awardSystem(eq(11L), any(), eq(ItemSource.mission), eq(missionId.toString()), any());
            verify(itemService).awardSystem(eq(22L), any(), eq(ItemSource.mission), eq(missionId.toString()), any());
            verify(missionProgressService).creditXp(11L, 250.0);
            verify(missionProgressService).creditXp(22L, 250.0);
        }

        @Test
        void aContributorAlreadyPaidIsNotPaidTwice() {
            UUID missionId = UUID.randomUUID();
            UserMission completed = communityRow(missionId, MissionStatus.completed);
            when(userMissionRepository.findCommunityById(missionId)).thenReturn(Optional.of(completed));
            when(contributionRepository.findUnrewarded(eq(missionId), any()))
                    .thenReturn(List.of(contribution(11L)))
                    .thenReturn(List.of());
            when(contributionRepository.markRewarded(eq(missionId), eq(11L), any())).thenReturn(0);
            runTransactionsInline();

            service.payRewards(missionId);

            verify(levelUpAwardService, never()).addMissionXp(anyLong(), any(), any());
            verify(itemService, never()).awardSystem(anyLong(), any(), any(), any(), any());
        }
    }

    private UserMission communityRow(UUID id, MissionStatus status) {
        return UserMission.builder()
                .id(id)
                .template(weeklyTemplate(now.minus(8, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS)))
                .pool(MissionPool.community)
                .status(status)
                .xpReward(250)
                .itemReward(Item.builder().id(UUID.randomUUID()).name("Spooky '26 Crate").build())
                .progressCount(0)
                .progressAp(0.0)
                .build();
    }

    private CommunityMissionContribution contribution(Long userId) {
        return CommunityMissionContribution.builder()
                .user(User.builder().id(userId).build())
                .contribution(5.0)
                .firstAt(now)
                .lastAt(now)
                .build();
    }
}
