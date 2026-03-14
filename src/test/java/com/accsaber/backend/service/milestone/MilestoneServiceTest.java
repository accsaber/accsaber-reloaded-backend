package com.accsaber.backend.service.milestone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.MilestoneQuerySpec;
import com.accsaber.backend.model.dto.MilestoneQuerySpec.SelectSpec;
import com.accsaber.backend.model.dto.request.milestone.CreateMilestoneRequest;
import com.accsaber.backend.model.dto.request.milestone.CreateMilestoneSetRequest;
import com.accsaber.backend.model.dto.response.milestone.MilestoneResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneSetResponse;
import com.accsaber.backend.model.dto.response.milestone.UserMilestoneProgressResponse;
import com.accsaber.backend.model.entity.milestone.Milestone;
import com.accsaber.backend.model.entity.milestone.MilestoneCompletionStats;
import com.accsaber.backend.model.entity.milestone.MilestoneSet;
import com.accsaber.backend.model.entity.milestone.MilestoneTier;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.milestone.MilestoneCompletionStatsRepository;
import com.accsaber.backend.repository.milestone.MilestoneRepository;
import com.accsaber.backend.repository.milestone.MilestoneSetRepository;
import com.accsaber.backend.repository.milestone.UserMilestoneLinkRepository;
import com.accsaber.backend.repository.user.UserRepository;

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
    private MilestoneEvaluationService milestoneEvaluationService;
    @Mock
    private MilestoneQueryBuilderService queryBuilderService;

    @InjectMocks
    private MilestoneService service;

    private MilestoneSet set;
    private Milestone milestone;
    private MilestoneQuerySpec querySpec;

    @BeforeEach
    void setUp() {
        set = MilestoneSet.builder()
                .id(UUID.randomUUID())
                .title("Accuracy Milestones")
                .description("Score accuracy goals")
                .setBonusXp(BigDecimal.valueOf(500))
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
                .xp(BigDecimal.valueOf(300))
                .querySpec(querySpec)
                .targetValue(BigDecimal.valueOf(900))
                .comparison("GTE")
                .active(true)
                .build();
    }

    private final Pageable defaultPageable = PageRequest.of(0, 20, Sort.by("createdAt"));

    @Nested
    class FindAllActive {

        @Test
        void noFilter_returnsAllActive() {
            when(milestoneRepository.findAllActiveFiltered(isNull(), isNull(), isNull(), eq(defaultPageable)))
                    .thenReturn(new PageImpl<>(List.of(milestone), defaultPageable, 1));
            when(completionStatsRepository.findAll()).thenReturn(List.of());

            Page<MilestoneResponse> result = service.findAllActive(null, null, null, defaultPageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getTitle()).isEqualTo("AP Hero");
        }

        @Test
        void setIdFilter_delegatesToRepository() {
            UUID setId = set.getId();
            when(milestoneRepository.findAllActiveFiltered(eq(setId), isNull(), isNull(), eq(defaultPageable)))
                    .thenReturn(new PageImpl<>(List.of(milestone), defaultPageable, 1));
            when(completionStatsRepository.findAll()).thenReturn(List.of());

            Page<MilestoneResponse> result = service.findAllActive(setId, null, null, defaultPageable);

            assertThat(result.getContent()).hasSize(1);
            verify(milestoneRepository).findAllActiveFiltered(eq(setId), isNull(), isNull(), eq(defaultPageable));
        }

        @Test
        void typeFilter_appliedViaQuery() {
            Milestone achievement = Milestone.builder()
                    .id(UUID.randomUUID())
                    .milestoneSet(set)
                    .title("FC King")
                    .type("achievement")
                    .tier(MilestoneTier.platinum)
                    .xp(BigDecimal.valueOf(500))
                    .querySpec(querySpec)
                    .targetValue(BigDecimal.ONE)
                    .comparison("GTE")
                    .build();

            when(milestoneRepository.findAllActiveFiltered(isNull(), isNull(), eq("achievement"), eq(defaultPageable)))
                    .thenReturn(new PageImpl<>(List.of(achievement), defaultPageable, 1));
            when(completionStatsRepository.findAll()).thenReturn(List.of());

            Page<MilestoneResponse> result = service.findAllActive(null, null, "achievement", defaultPageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getTitle()).isEqualTo("FC King");
        }

        @Test
        void completionStatsAreMergedIntoResponse() {
            MilestoneCompletionStats stats = buildStats(milestone.getId(), 50L, 200L, new BigDecimal("25.00"));
            when(milestoneRepository.findAllActiveFiltered(isNull(), isNull(), isNull(), eq(defaultPageable)))
                    .thenReturn(new PageImpl<>(List.of(milestone), defaultPageable, 1));
            when(completionStatsRepository.findAll()).thenReturn(List.of(stats));

            Page<MilestoneResponse> result = service.findAllActive(null, null, null, defaultPageable);

            MilestoneResponse response = result.getContent().get(0);
            assertThat(response.getCompletionPercentage()).isEqualByComparingTo(new BigDecimal("25.00"));
            assertThat(response.getCompletions()).isEqualTo(50L);
            assertThat(response.getTotalPlayers()).isEqualTo(200L);
        }
    }

    @Nested
    class FindById {

        @Test
        void found_returnsResponse() {
            when(milestoneRepository.findByIdAndActiveTrue(milestone.getId()))
                    .thenReturn(Optional.of(milestone));
            when(completionStatsRepository.findByMilestoneId(milestone.getId()))
                    .thenReturn(Optional.empty());

            MilestoneResponse response = service.findById(milestone.getId());

            assertThat(response.getId()).isEqualTo(milestone.getId());
            assertThat(response.getTargetValue()).isEqualByComparingTo(BigDecimal.valueOf(900));
            assertThat(response.getComparison()).isEqualTo("GTE");
            assertThat(response.getQuerySpec()).isEqualTo(querySpec);
        }

        @Test
        void notFound_throwsResourceNotFoundException() {
            UUID id = UUID.randomUUID();
            when(milestoneRepository.findByIdAndActiveTrue(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(id))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class CreateMilestone {

        @Test
        void validRequest_savesAndReturnsResponse() {
            CreateMilestoneRequest request = new CreateMilestoneRequest();
            request.setSetId(set.getId());
            request.setTitle("New Milestone");
            request.setDescription("Desc");
            request.setType("milestone");
            request.setTier(MilestoneTier.silver);
            request.setXp(BigDecimal.valueOf(200));
            request.setQuerySpec(querySpec);
            request.setTargetValue(BigDecimal.valueOf(500));
            request.setComparison("GTE");

            when(milestoneSetRepository.findByIdAndActiveTrue(set.getId()))
                    .thenReturn(Optional.of(set));
            when(milestoneRepository.save(any())).thenReturn(milestone);

            MilestoneResponse response = service.createMilestone(request);

            verify(queryBuilderService).validate(querySpec);
            assertThat(response).isNotNull();
        }

        @Test
        void invalidQuerySpec_throwsValidationException() {
            CreateMilestoneRequest request = new CreateMilestoneRequest();
            request.setSetId(set.getId());
            request.setQuerySpec(querySpec);
            request.setTargetValue(BigDecimal.ONE);

            when(milestoneSetRepository.findByIdAndActiveTrue(set.getId()))
                    .thenReturn(Optional.of(set));
            doThrow(new com.accsaber.backend.exception.ValidationException("bad spec"))
                    .when(queryBuilderService).validate(querySpec);

            assertThatThrownBy(() -> service.createMilestone(request))
                    .isInstanceOf(com.accsaber.backend.exception.ValidationException.class);
        }

        @Test
        void setNotFound_throwsResourceNotFoundException() {
            UUID missingSetId = UUID.randomUUID();
            CreateMilestoneRequest request = new CreateMilestoneRequest();
            request.setSetId(missingSetId);
            request.setQuerySpec(querySpec);

            when(milestoneSetRepository.findByIdAndActiveTrue(missingSetId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createMilestone(request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class CreateSet {

        @Test
        void createsSetWithBonusXp() {
            CreateMilestoneSetRequest request = new CreateMilestoneSetRequest();
            request.setTitle("New Set");
            request.setDescription("Desc");
            request.setSetBonusXp(BigDecimal.valueOf(1000));

            when(milestoneSetRepository.save(any())).thenReturn(set);

            MilestoneSetResponse response = service.createSet(request);

            assertThat(response.getTitle()).isEqualTo("Accuracy Milestones");
        }

        @Test
        void nullBonusXp_defaultsToZero() {
            CreateMilestoneSetRequest request = new CreateMilestoneSetRequest();
            request.setTitle("Set");
            request.setSetBonusXp(null);

            MilestoneSet zeroSet = MilestoneSet.builder()
                    .id(UUID.randomUUID()).title("Set").setBonusXp(BigDecimal.ZERO).build();
            when(milestoneSetRepository.save(any())).thenReturn(zeroSet);

            MilestoneSetResponse response = service.createSet(request);

            assertThat(response.getSetBonusXp()).isEqualByComparingTo(BigDecimal.ZERO);
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

        @Test
        void milestoneNotFound_throwsResourceNotFoundException() {
            UUID id = UUID.randomUUID();
            when(milestoneRepository.findByIdAndActiveTrueEager(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.backfillMilestone(id))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void noOtherMilestonesAreEvaluated() {
            User user = User.builder().id(1L).build();
            when(milestoneRepository.findByIdAndActiveTrueEager(milestone.getId()))
                    .thenReturn(Optional.of(milestone));
            when(userRepository.findByActiveTrue()).thenReturn(List.of(user));

            service.backfillMilestone(milestone.getId());

            verify(milestoneEvaluationService).evaluateSingleMilestoneForUser(1L, milestone);
            org.mockito.Mockito.verifyNoMoreInteractions(milestoneEvaluationService);
        }
    }

    @Nested
    class FindUserProgress {

        @Test
        void completedMilestone_showsCompletedTrue() {
            com.accsaber.backend.model.entity.milestone.UserMilestoneLink link = com.accsaber.backend.model.entity.milestone.UserMilestoneLink
                    .builder()
                    .milestone(milestone)
                    .progress(BigDecimal.valueOf(950))
                    .completed(true)
                    .completedAt(java.time.Instant.now())
                    .build();

            when(milestoneRepository.findAllActiveFiltered(isNull(), isNull(), isNull(), eq(defaultPageable)))
                    .thenReturn(new PageImpl<>(List.of(milestone), defaultPageable, 1));
            when(userMilestoneLinkRepository.findByUser_Id(42L)).thenReturn(List.of(link));
            when(completionStatsRepository.findAll()).thenReturn(List.of());

            Page<UserMilestoneProgressResponse> progress = service.findUserProgress(42L, defaultPageable);

            assertThat(progress.getContent()).hasSize(1);
            assertThat(progress.getContent().get(0).isCompleted()).isTrue();
            assertThat(progress.getContent().get(0).getProgress()).isEqualByComparingTo(BigDecimal.valueOf(950));
        }

        @Test
        void noLink_showsCompletedFalseAndNullProgress() {
            when(milestoneRepository.findAllActiveFiltered(isNull(), isNull(), isNull(), eq(defaultPageable)))
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
    class DeactivateMilestone {

        @Test
        void setsActiveFalseAndSaves() {
            when(milestoneRepository.findById(milestone.getId()))
                    .thenReturn(Optional.of(milestone));
            when(milestoneRepository.save(any())).thenReturn(milestone);

            service.deactivateMilestone(milestone.getId());

            assertThat(milestone.isActive()).isFalse();
            verify(milestoneRepository).save(milestone);
        }

        @Test
        void notFound_throwsResourceNotFoundException() {
            UUID id = UUID.randomUUID();
            when(milestoneRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deactivateMilestone(id))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    private MilestoneCompletionStats buildStats(UUID milestoneId, long completions,
            long total, BigDecimal pct) {
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
