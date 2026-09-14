package com.accsaber.backend.service.clan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.model.dto.EventMissionTargets;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanCapacity;
import com.accsaber.backend.model.entity.clan.ClanMember;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.mission.MissionPool;
import com.accsaber.backend.model.entity.mission.MissionTemplate;
import com.accsaber.backend.model.entity.mission.MissionType;
import com.accsaber.backend.model.entity.mission.UserMission;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.ClanRepository;
import com.accsaber.backend.repository.mission.MissionTemplateRepository;
import com.accsaber.backend.repository.mission.UserMissionRepository;
import com.accsaber.backend.service.mission.MissionAssignmentContext;
import com.accsaber.backend.service.mission.MissionAssignmentService;
import com.accsaber.backend.service.mission.MissionBuilderService;
import com.accsaber.backend.service.mission.MissionRolloverService;
import com.accsaber.backend.service.mission.MissionRowFactory;

@ExtendWith(MockitoExtension.class)
class ClanMissionAssignmentServiceTest {

    private static final Instant WEEK_END = Instant.parse("2026-09-21T02:00:00Z");

    @Mock
    private ClanRepository clanRepository;
    @Mock
    private ClanMemberRepository memberRepository;
    @Mock
    private MissionTemplateRepository templateRepository;
    @Mock
    private UserMissionRepository userMissionRepository;
    @Mock
    private MissionAssignmentService missionAssignmentService;
    @Mock
    private MissionBuilderService builderService;
    @Mock
    private MissionRowFactory rowFactory;
    @Mock
    private MissionRolloverService rolloverService;
    @Mock
    private ClanLevelService levelService;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private Executor backfillExecutor;

    private final ClanProperties clanProperties = new ClanProperties();
    private ClanMissionAssignmentService service;

    private final Clan clan = Clan.builder().id(UUID.randomUUID()).build();

    @BeforeEach
    void setUp() {
        service = new ClanMissionAssignmentService(clanRepository, memberRepository, templateRepository,
                userMissionRepository, missionAssignmentService, builderService, rowFactory, rolloverService,
                levelService, clanProperties, transactionTemplate, backfillExecutor);
        lenient().when(clanRepository.findByIdAndActiveTrue(clan.getId())).thenReturn(Optional.of(clan));
        lenient().when(rolloverService.nextRollover(eq(MissionPool.clan), any())).thenReturn(WEEK_END);
        lenient().when(userMissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private MissionTemplate template(EventMissionTargets targets) {
        return MissionTemplate.builder().id(UUID.randomUUID()).type(MissionType.SCORES_N).pool(MissionPool.clan)
                .eventTargets(targets).awardsItem(Item.builder().id(UUID.randomUUID()).build()).build();
    }

    private EventMissionTargets counterTargets(int count) {
        return new EventMissionTargets(null, null, null, null, null, null, count, null, null, null, null, null, null,
                null);
    }

    private void slots(int amount, MissionTemplate... templates) {
        when(templateRepository.findByPoolAndActiveTrue(MissionPool.clan)).thenReturn(List.of(templates));
        when(levelService.capacityOf(clan, ClanCapacity.mission_slots)).thenReturn(amount);
        when(builderService.weightedPickExcluding(anyList(), any(Random.class), any())).thenAnswer(inv -> {
            List<MissionTemplate> pool = inv.getArgument(0);
            Set<UUID> tried = inv.getArgument(2);
            return pool.stream().filter(t -> !tried.contains(t.getId())).findFirst().orElse(null);
        });
    }

    private MissionAssignmentContext context(Long userId, boolean plays) {
        return new MissionAssignmentContext(userId, plays ? List.of(Category.builder().build()) : List.of(), Map.of(),
                Map.of(), 0.0);
    }

    @Test
    void aCounterOpensWithItsTargetsScaledByHeadcount() {
        clanProperties.setRosterReferenceMembers(5.0);
        MissionTemplate counter = template(counterTargets(25));
        slots(1, counter);
        when(memberRepository.findOpenUserIds(clan.getId())).thenReturn(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L,
                10L));
        when(rowFactory.build(null, counter, null)).thenReturn(UserMission.builder().template(counter)
                .pool(MissionPool.clan).targetCount(25).build());

        assertThat(service.fill(clan.getId())).isEqualTo(1);

        ArgumentCaptor<UserMission> saved = ArgumentCaptor.forClass(UserMission.class);
        verify(userMissionRepository).save(saved.capture());
        assertThat(saved.getValue().getTargetCount()).isEqualTo(50);
        assertThat(saved.getValue().getClan()).isSameAs(clan);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(WEEK_END);
    }

    @Test
    void aPerMemberMissionGivesEachPlayingMemberTheirOwnRowAndCapsTheClearsAtTheRowsBuilt() {
        clanProperties.setMissionClears(2);
        clanProperties.setRosterReferenceMembers(2.0);
        MissionTemplate perMember = template(null);
        slots(1, perMember);
        when(memberRepository.findOpenUserIds(clan.getId())).thenReturn(List.of(1L, 2L, 3L));
        when(missionAssignmentService.contextFor(1L)).thenReturn(context(1L, true));
        when(missionAssignmentService.contextFor(2L)).thenReturn(context(2L, true));
        when(missionAssignmentService.contextFor(3L)).thenReturn(context(3L, false));
        when(builderService.pickAndBuild(any(), eq(List.of(perMember)), eq(WEEK_END), eq(MissionPool.clan), any(),
                any(), any(), any())).thenAnswer(inv -> UserMission.builder().template(perMember).pool(MissionPool.clan)
                        .itemReward(perMember.getAwardsItem()).build());

        assertThat(service.fill(clan.getId())).isEqualTo(1);

        ArgumentCaptor<UserMission> parent = ArgumentCaptor.forClass(UserMission.class);
        verify(userMissionRepository).save(parent.capture());
        assertThat(parent.getValue().getTargetCount()).isEqualTo(2);
        assertThat(parent.getValue().getItemReward()).isSameAs(perMember.getAwardsItem());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserMission>> rows = ArgumentCaptor.forClass(List.class);
        verify(userMissionRepository).saveAll(rows.capture());
        assertThat(rows.getValue()).hasSize(2).allSatisfy(row -> {
            assertThat(row.getParentMission()).isSameAs(parent.getValue());
            assertThat(row.getClan()).isSameAs(clan);
            assertThat(row.getItemReward()).isNull();
        });
    }

    @Test
    void aPerMemberMissionNobodyCanTakeLeavesTheSlotForTheNextTemplate() {
        MissionTemplate perMember = template(null);
        MissionTemplate counter = template(counterTargets(10));
        slots(1, perMember, counter);
        when(memberRepository.findOpenUserIds(clan.getId())).thenReturn(List.of(1L));
        when(missionAssignmentService.contextFor(1L)).thenReturn(context(1L, false));
        when(rowFactory.build(null, counter, null)).thenReturn(UserMission.builder().template(counter)
                .pool(MissionPool.clan).targetCount(10).build());

        assertThat(service.fill(clan.getId())).isEqualTo(1);

        verify(userMissionRepository, never()).saveAll(any());
        verify(rowFactory).build(null, counter, null);
    }

    @Test
    void theClanNeverOpensMoreThanItsSlots() {
        slots(2, template(counterTargets(1)), template(counterTargets(2)), template(counterTargets(3)));
        when(rowFactory.build(any(), any(), any())).thenAnswer(inv -> UserMission.builder()
                .template(inv.getArgument(1)).pool(MissionPool.clan).targetCount(1).build());

        assertThat(service.fill(clan.getId())).isEqualTo(2);
    }

    @Test
    void aLoginFillsTheMemberRowsThePlayerIsMissing() {
        MissionTemplate perMember = template(null);
        UserMission parent = UserMission.builder().id(UUID.randomUUID()).template(perMember).pool(MissionPool.clan)
                .clan(clan).expiresAt(WEEK_END).build();
        when(memberRepository.findOpenByUserId(7L)).thenReturn(Optional.of(ClanMember.builder().clan(clan).build()));
        when(userMissionRepository.findPerMemberParentsMissing(eq(clan.getId()), eq(7L), any()))
                .thenReturn(List.of(parent));
        when(missionAssignmentService.contextFor(7L)).thenReturn(context(7L, true));
        when(builderService.pickAndBuild(any(), eq(List.of(perMember)), eq(WEEK_END), eq(MissionPool.clan), any(),
                any(), any(), any())).thenReturn(UserMission.builder().template(perMember).pool(MissionPool.clan)
                        .build());
        doAnswer(inv -> {
            inv.<Runnable>getArgument(0).run();
            return null;
        }).when(backfillExecutor).execute(any());
        doAnswer(inv -> {
            inv.<Consumer<TransactionStatus>>getArgument(0).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        service.fillMemberRowsAsync(7L);

        ArgumentCaptor<UserMission> saved = ArgumentCaptor.forClass(UserMission.class);
        verify(userMissionRepository).save(saved.capture());
        assertThat(saved.getValue().getParentMission()).isSameAs(parent);
        assertThat(saved.getValue().getClan()).isSameAs(clan);
    }
}
