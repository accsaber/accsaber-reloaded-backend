package com.accsaber.backend.service.milestone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.MilestoneQuerySpec;
import com.accsaber.backend.model.dto.MilestoneQuerySpec.SelectSpec;
import com.accsaber.backend.model.dto.request.milestone.CreateMilestoneRequest;
import com.accsaber.backend.model.dto.request.milestone.CreateMilestoneSetRequest;
import com.accsaber.backend.model.dto.request.milestone.CreatePrerequisiteLinkRequest;
import com.accsaber.backend.model.dto.response.milestone.MilestoneResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneSetResponse;
import com.accsaber.backend.model.dto.response.milestone.PrerequisiteLinkResponse;
import com.accsaber.backend.model.dto.response.milestone.UserMilestoneProgressResponse;
import com.accsaber.backend.model.entity.milestone.Milestone;
import com.accsaber.backend.model.entity.milestone.MilestoneCompletionStats;
import com.accsaber.backend.model.entity.milestone.MilestonePrerequisiteLink;
import com.accsaber.backend.model.entity.milestone.MilestoneSet;
import com.accsaber.backend.model.entity.milestone.MilestoneStatus;
import com.accsaber.backend.model.entity.milestone.MilestoneTier;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.CurveRepository;
import com.accsaber.backend.repository.map.MapDifficultyMilestoneLinkRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.milestone.MilestoneCompletionStatsRepository;
import com.accsaber.backend.repository.milestone.MilestonePrerequisiteLinkRepository;
import com.accsaber.backend.repository.milestone.MilestoneItemRepository;
import com.accsaber.backend.repository.milestone.MilestoneRepository;
import com.accsaber.backend.repository.milestone.MilestoneSetItemRepository;
import com.accsaber.backend.repository.milestone.MilestoneSetRepository;
import com.accsaber.backend.repository.milestone.UserMilestoneLinkRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.DuplicateUserService;

@ExtendWith(MockitoExtension.class)
class MilestoneServiceTest {

    @Mock
    private MilestoneRepository milestoneRepository;
    @Mock
    private MilestoneSetRepository milestoneSetRepository;
    @Mock
    private UserMilestoneLinkRepository userMilestoneLinkRepository;
    @Mock
    private MilestoneCompletionStatsRepository completionStatsRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MapDifficultyRepository mapDifficultyRepository;
    @Mock
    private MapDifficultyMilestoneLinkRepository mapDifficultyMilestoneLinkRepository;
    @Mock
    private MilestonePrerequisiteLinkRepository prerequisiteLinkRepository;
    @Mock
    private MilestoneEvaluationService milestoneEvaluationService;
    @Mock
    private MilestoneQueryBuilderService queryBuilderService;
    @Mock
    private DuplicateUserService duplicateUserService;
    @Mock
    private CurveRepository curveRepository;
    @Mock
    private MilestoneProgressCalculator progressCalculator;
    @Mock
    private MilestoneItemRepository milestoneItemRepository;
    @Mock
    private MilestoneSetItemRepository milestoneSetItemRepository;

    @InjectMocks
    private MilestoneService service;

    private MilestoneSet set;
    private Milestone milestone;
    private MilestoneQuerySpec querySpec;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(duplicateUserService.resolvePrimaryUserId(any(Long.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        set = MilestoneSet.builder()
                .id(UUID.randomUUID())
                .title("Accuracy Milestones")
                .description("Score accuracy goals")
                .setBonusXp((double) (500))
                .build();

        querySpec = new MilestoneQuerySpec(
                new SelectSpec("MAX", "ap"),
                "scores",
                List.of(new MilestoneQuerySpec.FilterSpec("active", "=", true)));

        milestone = Milestone.builder()
                .id(UUID.randomUUID())
                .milestoneSet(set)
                .title("AP Hero")
                .description("Get a 900 AP score")
                .type("milestone")
                .tier(MilestoneTier.gold)
                .xp((double) (300))
                .querySpec(querySpec)
                .targetValue((double) (900))
                .comparison("GTE")
                .active(true)
                .build();
    }

    private final Pageable defaultPageable = PageRequest.of(0, 20, Sort.by("createdAt"));

    @Nested
    class FindAllActive {

        @Test
        void completionStatsAreMergedIntoResponse() {
            MilestoneCompletionStats stats = buildStats(milestone.getId(), 50L, 200L, 25.00);
            when(milestoneRepository.findAllActiveFiltered(isNull(), isNull(), isNull(), eq(MilestoneStatus.ACTIVE),
                    eq(defaultPageable)))
                    .thenReturn(new PageImpl<>(List.of(milestone), defaultPageable, 1));
            when(completionStatsRepository.findAll()).thenReturn(List.of(stats));

            Page<MilestoneResponse> result = service.findAllActive(null, null, null, defaultPageable);

            MilestoneResponse response = result.getContent().get(0);
            assertThat(response.getCompletionPercentage()).isEqualByComparingTo(25.00);
            assertThat(response.getCompletions()).isEqualTo(50L);
            assertThat(response.getTotalPlayers()).isEqualTo(200L);
        }
    }

    @Nested
    class FindById {

        @Test
        void notFound_throwsResourceNotFoundException() {
            UUID id = UUID.randomUUID();
            when(milestoneRepository.findByIdAndActiveTrueAndStatusActive(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(id))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class CreateMilestone {

        @Test
        void invalidQuerySpec_throwsValidationException() {
            CreateMilestoneRequest request = new CreateMilestoneRequest();
            request.setSetId(set.getId());
            request.setQuerySpec(querySpec);
            request.setTargetValue(1.0);

            when(milestoneSetRepository.findByIdAndActiveTrue(set.getId()))
                    .thenReturn(Optional.of(set));
            doThrow(new com.accsaber.backend.exception.ValidationException("bad spec"))
                    .when(queryBuilderService).validate(querySpec);

            assertThatThrownBy(() -> service.createMilestone(request))
                    .isInstanceOf(com.accsaber.backend.exception.ValidationException.class);
        }
    }

    @Nested
    class CreateSet {

        @Test
        void nullBonusXp_defaultsToZero() {
            CreateMilestoneSetRequest request = new CreateMilestoneSetRequest();
            request.setTitle("Set");
            request.setSetBonusXp(null);

            when(milestoneSetRepository.save(any())).thenAnswer(i -> {
                MilestoneSet set = i.getArgument(0);
                set.setId(UUID.randomUUID());
                return set;
            });

            MilestoneSetResponse response = service.createSet(request);

            ArgumentCaptor<MilestoneSet> captor = ArgumentCaptor.forClass(MilestoneSet.class);
            verify(milestoneSetRepository).save(captor.capture());
            assertThat(captor.getValue().getSetBonusXp()).isEqualByComparingTo(0.0);
            assertThat(response.getSetBonusXp()).isEqualByComparingTo(0.0);
        }
    }

    @Nested
    class BackfillMilestone {

        @Test
        void evaluatesSingleMilestoneForEachActiveUser() {
            User user1 = User.builder().id(111L).build();
            User user2 = User.builder().id(222L).build();

            when(milestoneRepository.findByIdAndActiveTrueEager(milestone.getId()))
                    .thenReturn(Optional.of(milestone));
            when(userRepository.findByActiveTrue()).thenReturn(List.of(user1, user2));

            service.backfillMilestone(milestone.getId());

            verify(milestoneEvaluationService).evaluateSingleMilestoneForUser(111L, milestone);
            verify(milestoneEvaluationService).evaluateSingleMilestoneForUser(222L, milestone);
        }
    }

    @Nested
    class FindUserProgress {

        @Test
        void completedMilestone_showsCompletedTrue() {
            com.accsaber.backend.model.entity.milestone.UserMilestoneLink link = com.accsaber.backend.model.entity.milestone.UserMilestoneLink
                    .builder()
                    .milestone(milestone)
                    .progress((double) (950))
                    .completed(true)
                    .completedAt(java.time.Instant.now())
                    .build();

            when(milestoneRepository.findAllActiveFiltered(isNull(), isNull(), isNull(), eq(MilestoneStatus.ACTIVE),
                    eq(defaultPageable)))
                    .thenReturn(new PageImpl<>(List.of(milestone), defaultPageable, 1));
            when(userMilestoneLinkRepository.findByUser_Id(42L)).thenReturn(List.of(link));
            when(completionStatsRepository.findAll()).thenReturn(List.of());

            Page<UserMilestoneProgressResponse> progress = service.findUserProgress(42L, defaultPageable);

            assertThat(progress.getContent()).hasSize(1);
            assertThat(progress.getContent().get(0).isCompleted()).isTrue();
            assertThat(progress.getContent().get(0).getProgress()).isEqualByComparingTo((double) (950));
        }

        @Test
        void noLink_showsCompletedFalseAndNullProgress() {
            when(milestoneRepository.findAllActiveFiltered(isNull(), isNull(), isNull(), eq(MilestoneStatus.ACTIVE),
                    eq(defaultPageable)))
                    .thenReturn(new PageImpl<>(List.of(milestone), defaultPageable, 1));
            when(userMilestoneLinkRepository.findByUser_Id(99L)).thenReturn(List.of());
            when(completionStatsRepository.findAll()).thenReturn(List.of());

            Page<UserMilestoneProgressResponse> progress = service.findUserProgress(99L, defaultPageable);

            assertThat(progress.getContent()).hasSize(1);
            assertThat(progress.getContent().get(0).isCompleted()).isFalse();
            assertThat(progress.getContent().get(0).getProgress()).isNull();
        }
    }

    @Nested
    class ActivateMilestone {

        @Test
        void draftMilestone_activatedSuccessfully() {
            Milestone draft = Milestone.builder()
                    .id(UUID.randomUUID())
                    .milestoneSet(set)
                    .title("Draft")
                    .type("milestone")
                    .tier(MilestoneTier.bronze)
                    .xp(10.0)
                    .querySpec(querySpec)
                    .targetValue(1.0)
                    .comparison("GTE")
                    .status(MilestoneStatus.DRAFT)
                    .build();

            when(milestoneRepository.findByIdAndActiveTrue(draft.getId()))
                    .thenReturn(Optional.of(draft));
            when(milestoneRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            MilestoneResponse response = service.activateMilestone(draft.getId());

            assertThat(response.getStatus()).isEqualTo("ACTIVE");
            assertThat(draft.getStatus()).isEqualTo(MilestoneStatus.ACTIVE);
        }

        @Test
        void alreadyActive_throwsConflict() {
            Milestone active = Milestone.builder()
                    .id(UUID.randomUUID())
                    .milestoneSet(set)
                    .title("Active")
                    .type("milestone")
                    .tier(MilestoneTier.bronze)
                    .xp(10.0)
                    .querySpec(querySpec)
                    .targetValue(1.0)
                    .comparison("GTE")
                    .status(MilestoneStatus.ACTIVE)
                    .build();

            when(milestoneRepository.findByIdAndActiveTrue(active.getId()))
                    .thenReturn(Optional.of(active));

            assertThatThrownBy(() -> service.activateMilestone(active.getId()))
                    .isInstanceOf(ConflictException.class);
        }
    }

    @Nested
    class ActivateMilestones {

        @Test
        void allDraft_activatedSuccessfully() {
            Milestone m1 = Milestone.builder().id(UUID.randomUUID()).milestoneSet(set)
                    .title("M1").type("milestone").tier(MilestoneTier.bronze)
                    .xp(10.0).querySpec(querySpec).targetValue(1.0)
                    .comparison("GTE").status(MilestoneStatus.DRAFT).build();
            Milestone m2 = Milestone.builder().id(UUID.randomUUID()).milestoneSet(set)
                    .title("M2").type("milestone").tier(MilestoneTier.silver)
                    .xp(10.0).querySpec(querySpec).targetValue(1.0)
                    .comparison("GTE").status(MilestoneStatus.DRAFT).build();
            List<UUID> ids = List.of(m1.getId(), m2.getId());

            when(milestoneRepository.findAllActiveByIdIn(ids)).thenReturn(List.of(m1, m2));
            when(milestoneRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

            List<MilestoneResponse> responses = service.activateMilestones(ids);

            assertThat(responses).hasSize(2);
            assertThat(m1.getStatus()).isEqualTo(MilestoneStatus.ACTIVE);
            assertThat(m2.getStatus()).isEqualTo(MilestoneStatus.ACTIVE);
        }

        @Test
        void someAlreadyActive_throwsConflict() {
            Milestone draft = Milestone.builder().id(UUID.randomUUID()).milestoneSet(set)
                    .title("Draft").type("milestone").tier(MilestoneTier.bronze)
                    .xp(10.0).querySpec(querySpec).targetValue(1.0)
                    .comparison("GTE").status(MilestoneStatus.DRAFT).build();
            Milestone active = Milestone.builder().id(UUID.randomUUID()).milestoneSet(set)
                    .title("Active").type("milestone").tier(MilestoneTier.bronze)
                    .xp(10.0).querySpec(querySpec).targetValue(1.0)
                    .comparison("GTE").status(MilestoneStatus.ACTIVE).build();
            List<UUID> ids = List.of(draft.getId(), active.getId());

            when(milestoneRepository.findAllActiveByIdIn(ids)).thenReturn(List.of(draft, active));

            assertThatThrownBy(() -> service.activateMilestones(ids))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void missingIds_throwsNotFound() {
            UUID missing = UUID.randomUUID();

            when(milestoneRepository.findAllActiveByIdIn(List.of(missing))).thenReturn(List.of());

            assertThatThrownBy(() -> service.activateMilestones(List.of(missing)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class PrerequisiteLinks {

        private Milestone prerequisite;

        @BeforeEach
        void setUpPrerequisite() {
            prerequisite = Milestone.builder()
                    .id(UUID.randomUUID())
                    .milestoneSet(set)
                    .title("Prerequisite")
                    .type("milestone")
                    .tier(MilestoneTier.silver)
                    .xp((double) (100))
                    .querySpec(querySpec)
                    .targetValue((double) (500))
                    .comparison("GTE")
                    .build();
        }

        @Test
        void createLink_success() {
            CreatePrerequisiteLinkRequest request = new CreatePrerequisiteLinkRequest();
            request.setMilestoneId(milestone.getId());
            request.setPrerequisiteMilestoneId(prerequisite.getId());
            request.setBlocker(true);

            when(milestoneRepository.findByIdAndActiveTrue(milestone.getId()))
                    .thenReturn(Optional.of(milestone));
            when(milestoneRepository.findByIdAndActiveTrue(prerequisite.getId()))
                    .thenReturn(Optional.of(prerequisite));
            when(prerequisiteLinkRepository.existsByMilestone_IdAndPrerequisiteMilestone_IdAndActiveTrue(
                    milestone.getId(), prerequisite.getId())).thenReturn(false);
            when(prerequisiteLinkRepository.save(any())).thenAnswer(i -> {
                MilestonePrerequisiteLink link = i.getArgument(0);
                link.setId(UUID.randomUUID());
                return link;
            });

            PrerequisiteLinkResponse response = service.createPrerequisiteLink(request);

            assertThat(response.getMilestoneId()).isEqualTo(milestone.getId());
            assertThat(response.getPrerequisiteMilestoneId()).isEqualTo(prerequisite.getId());
            assertThat(response.getPrerequisiteTitle()).isEqualTo("Prerequisite");
            assertThat(response.isBlocker()).isTrue();
        }

        @Test
        void createLink_duplicateThrowsConflict() {
            CreatePrerequisiteLinkRequest request = new CreatePrerequisiteLinkRequest();
            request.setMilestoneId(milestone.getId());
            request.setPrerequisiteMilestoneId(prerequisite.getId());

            when(milestoneRepository.findByIdAndActiveTrue(milestone.getId()))
                    .thenReturn(Optional.of(milestone));
            when(milestoneRepository.findByIdAndActiveTrue(prerequisite.getId()))
                    .thenReturn(Optional.of(prerequisite));
            when(prerequisiteLinkRepository.existsByMilestone_IdAndPrerequisiteMilestone_IdAndActiveTrue(
                    milestone.getId(), prerequisite.getId())).thenReturn(true);

            assertThatThrownBy(() -> service.createPrerequisiteLink(request))
                    .isInstanceOf(ConflictException.class);
        }
    }

    @Nested
    class CreateMilestoneStatus {

        @Test
        void newMilestone_defaultsToDraft() {
            CreateMilestoneRequest request = new CreateMilestoneRequest();
            request.setSetId(set.getId());
            request.setTitle("Test");
            request.setType("milestone");
            request.setTier(MilestoneTier.bronze);
            request.setXp(10.0);
            request.setQuerySpec(querySpec);
            request.setTargetValue(1.0);
            request.setComparison("GTE");

            when(milestoneSetRepository.findByIdAndActiveTrue(set.getId()))
                    .thenReturn(Optional.of(set));
            when(milestoneRepository.save(any())).thenAnswer(i -> {
                Milestone saved = i.getArgument(0);
                saved.setId(UUID.randomUUID());
                return saved;
            });

            MilestoneResponse response = service.createMilestone(request);

            assertThat(response.getStatus()).isEqualTo("DRAFT");
        }
    }

    private MilestoneCompletionStats buildStats(UUID milestoneId, long completions,
            long total, Double pct) {
        MilestoneCompletionStats stats = new MilestoneCompletionStats();
        try {
            var milestoneIdField = MilestoneCompletionStats.class.getDeclaredField("milestoneId");
            milestoneIdField.setAccessible(true);
            milestoneIdField.set(stats, milestoneId);

            var completionsField = MilestoneCompletionStats.class.getDeclaredField("completions");
            completionsField.setAccessible(true);
            completionsField.set(stats, completions);

            var totalPlayersField = MilestoneCompletionStats.class.getDeclaredField("totalPlayers");
            totalPlayersField.setAccessible(true);
            totalPlayersField.set(stats, total);

            var pctField = MilestoneCompletionStats.class.getDeclaredField("completionPercentage");
            pctField.setAccessible(true);
            pctField.set(stats, pct);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return stats;
    }
}
