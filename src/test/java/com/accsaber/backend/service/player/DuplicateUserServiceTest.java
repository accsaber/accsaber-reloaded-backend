package com.accsaber.backend.service.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.player.DuplicateLinkResponse;
import com.accsaber.backend.model.entity.Modifier;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.score.ScoreModifierLink;
import com.accsaber.backend.model.entity.user.MergeScoreAction;
import com.accsaber.backend.model.entity.user.MergeScoreAction.ActionType;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserDuplicateLink;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.score.ScoreModifierLinkRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.staff.StaffUserRepository;
import com.accsaber.backend.repository.user.MergeScoreActionRepository;
import com.accsaber.backend.repository.user.UserDuplicateLinkRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.stats.OverallStatisticsService;
import com.accsaber.backend.service.stats.RankingService;
import com.accsaber.backend.service.stats.StatisticsService;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
class DuplicateUserServiceTest {

        private static final Long PRIMARY_ID = 76561198000000001L;
        private static final Long SECONDARY_ID = 76561198000000002L;
        private static final UUID STAFF_ID = UUID.randomUUID();

        @Mock
        private UserDuplicateLinkRepository linkRepository;
        @Mock
        private MergeScoreActionRepository mergeScoreActionRepository;
        @Mock
        private UserRepository userRepository;
        @Mock
        private ScoreRepository scoreRepository;
        @Mock
        private ScoreModifierLinkRepository modifierLinkRepository;
        @Mock
        private StaffUserRepository staffUserRepository;
        @Mock
        private CategoryRepository categoryRepository;
        @Mock
        private StatisticsService statisticsService;
        @Mock
        private OverallStatisticsService overallStatisticsService;
        @Mock
        private RankingService rankingService;
        @Mock
        private com.accsaber.backend.service.skill.SkillService skillService;
        @Mock
        private EntityManager entityManager;

        private DuplicateUserService service;
        private User primaryUser;
        private User secondaryUser;

        @BeforeEach
        void setUp() {
                TransactionSynchronizationManager.initSynchronization();
                service = new DuplicateUserService(
                                linkRepository, mergeScoreActionRepository, userRepository, scoreRepository,
                                modifierLinkRepository, staffUserRepository, categoryRepository,
                                statisticsService, overallStatisticsService, rankingService, skillService,
                                entityManager);
                service.setSelf(service);

                primaryUser = User.builder()
                                .id(PRIMARY_ID)
                                .name("Player")
                                .country("US")
                                .totalXp(new BigDecimal("1000"))
                                .active(true)
                                .build();

                secondaryUser = User.builder()
                                .id(SECONDARY_ID)
                                .name("Player")
                                .country("US")
                                .totalXp(new BigDecimal("500"))
                                .active(true)
                                .build();

                lenient().when(linkRepository.findAll()).thenReturn(List.of());
                lenient().when(mergeScoreActionRepository.save(any())).thenAnswer(inv -> {
                        MergeScoreAction a = inv.getArgument(0);
                        if (a.getId() == null)
                                a.setId(UUID.randomUUID());
                        return a;
                });
        }

        @AfterEach
        void tearDown() {
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                        TransactionSynchronizationManager.clearSynchronization();
                }
        }

        @Nested
        class ResolvePrimaryUserId {

                @Test
                void returnsOriginalId_whenNoDuplicateLink() {
                        service.refreshCache();

                        assertThat(service.resolvePrimaryUserId(PRIMARY_ID)).isEqualTo(PRIMARY_ID);
                }

                @Test
                void returnsPrimaryId_whenDuplicateLinkExists() {
                        UserDuplicateLink link = UserDuplicateLink.builder()
                                        .secondaryUser(secondaryUser)
                                        .primaryUser(primaryUser)
                                        .build();
                        when(linkRepository.findAll()).thenReturn(List.of(link));
                        service.refreshCache();

                        assertThat(service.resolvePrimaryUserId(SECONDARY_ID)).isEqualTo(PRIMARY_ID);
                }
        }

        @Nested
        class CreateLink {

                @Test
                void createsLinkAndUpdatesCache() {
                        when(userRepository.findById(PRIMARY_ID)).thenReturn(Optional.of(primaryUser));
                        when(userRepository.findById(SECONDARY_ID)).thenReturn(Optional.of(secondaryUser));
                        when(linkRepository.existsBySecondaryUser_Id(SECONDARY_ID)).thenReturn(false);
                        when(linkRepository.existsBySecondaryUser_Id(PRIMARY_ID)).thenReturn(false);
                        when(linkRepository.save(any())).thenAnswer(inv -> {
                                UserDuplicateLink l = inv.getArgument(0);
                                l.setId(UUID.randomUUID());
                                l.setCreatedAt(Instant.now());
                                return l;
                        });

                        DuplicateLinkResponse response = service.createLink(PRIMARY_ID, SECONDARY_ID, "test reason");

                        assertThat(response.getPrimaryUserId()).isEqualTo(String.valueOf(PRIMARY_ID));
                        assertThat(response.getSecondaryUserId()).isEqualTo(String.valueOf(SECONDARY_ID));
                        assertThat(response.getReason()).isEqualTo("test reason");
                        assertThat(service.resolvePrimaryUserId(SECONDARY_ID)).isEqualTo(PRIMARY_ID);
                }

                @Test
                void throwsWhenLinkingUserToSelf() {
                        assertThatThrownBy(() -> service.createLink(PRIMARY_ID, PRIMARY_ID, "test"))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("themselves");
                }

                @Test
                void throwsWhenSecondaryAlreadyLinked() {
                        when(linkRepository.existsBySecondaryUser_Id(SECONDARY_ID)).thenReturn(true);

                        assertThatThrownBy(() -> service.createLink(PRIMARY_ID, SECONDARY_ID, "test"))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("already linked");
                }

                @Test
                void throwsWhenPrimaryIsAlreadySecondary() {
                        when(linkRepository.existsBySecondaryUser_Id(SECONDARY_ID)).thenReturn(false);
                        when(linkRepository.existsBySecondaryUser_Id(PRIMARY_ID)).thenReturn(true);

                        assertThatThrownBy(() -> service.createLink(PRIMARY_ID, SECONDARY_ID, "test"))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("already a secondary");
                }

                @Test
                void throwsWhenUserNotFound() {
                        when(linkRepository.existsBySecondaryUser_Id(SECONDARY_ID)).thenReturn(false);
                        when(linkRepository.existsBySecondaryUser_Id(PRIMARY_ID)).thenReturn(false);
                        when(userRepository.findById(PRIMARY_ID)).thenReturn(Optional.empty());

                        assertThatThrownBy(() -> service.createLink(PRIMARY_ID, SECONDARY_ID, "test"))
                                        .isInstanceOf(ResourceNotFoundException.class);
                }
        }

        @Nested
        class Merge {

                @Test
                void reassignsUniqueScores_deactivatesSecondary() {
                        UUID diffId1 = UUID.randomUUID();
                        UUID diffId2 = UUID.randomUUID();
                        MapDifficulty diff1 = MapDifficulty.builder().id(diffId1).build();
                        MapDifficulty diff2 = MapDifficulty.builder().id(diffId2).build();

                        Score secScore1 = Score.builder()
                                        .id(UUID.randomUUID()).user(secondaryUser).mapDifficulty(diff1)
                                        .score(900000).scoreNoMods(900000).rank(5).rankWhenSet(5)
                                        .ap(new BigDecimal("400")).weightedAp(new BigDecimal("400"))
                                        .xpGained(new BigDecimal("50")).active(true).build();
                        Score secScore2 = Score.builder()
                                        .id(UUID.randomUUID()).user(secondaryUser).mapDifficulty(diff2)
                                        .score(800000).scoreNoMods(800000).rank(10).rankWhenSet(10)
                                        .ap(new BigDecimal("300")).weightedAp(new BigDecimal("300"))
                                        .xpGained(new BigDecimal("30")).active(true).build();

                        when(userRepository.findById(PRIMARY_ID)).thenReturn(Optional.of(primaryUser));
                        when(userRepository.findById(SECONDARY_ID)).thenReturn(Optional.of(secondaryUser));
                        when(linkRepository.findBySecondaryUser_Id(SECONDARY_ID)).thenReturn(Optional.empty());
                        when(linkRepository.existsBySecondaryUser_Id(SECONDARY_ID)).thenReturn(false);
                        when(linkRepository.existsBySecondaryUser_Id(PRIMARY_ID)).thenReturn(false);
                        when(linkRepository.save(any())).thenAnswer(inv -> {
                                UserDuplicateLink l = inv.getArgument(0);
                                l.setId(UUID.randomUUID());
                                l.setCreatedAt(Instant.now());
                                return l;
                        });
                        when(scoreRepository.findByUser_IdAndActiveTrue(SECONDARY_ID))
                                        .thenReturn(List.of(secScore1, secScore2));
                        when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(PRIMARY_ID, diffId1))
                                        .thenReturn(Optional.of(Score.builder().id(UUID.randomUUID())
                                                        .ap(new BigDecimal("500")).build()));
                        when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(PRIMARY_ID, diffId2))
                                        .thenReturn(Optional.empty());
                        when(scoreRepository.saveAndFlush(any())).thenAnswer(inv -> {
                                Score s = inv.getArgument(0);
                                if (s.getId() == null)
                                        s.setId(UUID.randomUUID());
                                return s;
                        });
                        when(modifierLinkRepository.findByScore_Id(any())).thenReturn(List.of());
                        lenient().when(categoryRepository.findByActiveTrue()).thenReturn(List.of());

                        DuplicateLinkResponse response = service.merge(PRIMARY_ID, SECONDARY_ID, STAFF_ID, "dup");

                        assertThat(response.isMerged()).isTrue();
                        assertThat(response.getReason()).isEqualTo("dup");

                        assertThat(secScore1.isActive()).isFalse();
                        assertThat(secScore2.isActive()).isFalse();

                        assertThat(secondaryUser.isActive()).isFalse();

                        assertThat(primaryUser.getTotalXp())
                                        .isEqualByComparingTo(new BigDecimal("1000"));

                        ArgumentCaptor<Score> savedScores = ArgumentCaptor.forClass(Score.class);
                        verify(scoreRepository, org.mockito.Mockito.atLeast(3)).saveAndFlush(savedScores.capture());
                        List<Score> newScores = savedScores.getAllValues().stream()
                                        .filter(s -> s.getUser() != null && s.getUser().getId().equals(PRIMARY_ID)
                                                        && s.isActive())
                                        .toList();
                        assertThat(newScores).hasSize(1);
                        assertThat(newScores.get(0).getMapDifficulty().getId()).isEqualTo(diffId2);
                        assertThat(newScores.get(0).getSupersedesReason()).isEqualTo("User merge");

                        assertThat(service.resolvePrimaryUserId(SECONDARY_ID)).isEqualTo(PRIMARY_ID);
                }

                @Test
                void skipsAllScores_whenPrimaryHasBetterScoreOnEveryDifficulty() {
                        UUID diffId = UUID.randomUUID();
                        MapDifficulty diff = MapDifficulty.builder().id(diffId).build();

                        Score secScore = Score.builder()
                                        .id(UUID.randomUUID()).user(secondaryUser).mapDifficulty(diff)
                                        .score(900000).ap(new BigDecimal("300")).active(true).build();

                        when(userRepository.findById(PRIMARY_ID)).thenReturn(Optional.of(primaryUser));
                        when(userRepository.findById(SECONDARY_ID)).thenReturn(Optional.of(secondaryUser));
                        when(linkRepository.findBySecondaryUser_Id(SECONDARY_ID)).thenReturn(Optional.empty());
                        when(linkRepository.existsBySecondaryUser_Id(SECONDARY_ID)).thenReturn(false);
                        when(linkRepository.existsBySecondaryUser_Id(PRIMARY_ID)).thenReturn(false);
                        when(linkRepository.save(any())).thenAnswer(inv -> {
                                UserDuplicateLink l = inv.getArgument(0);
                                l.setId(UUID.randomUUID());
                                l.setCreatedAt(Instant.now());
                                return l;
                        });
                        when(scoreRepository.findByUser_IdAndActiveTrue(SECONDARY_ID))
                                        .thenReturn(List.of(secScore));
                        when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(PRIMARY_ID, diffId))
                                        .thenReturn(Optional.of(Score.builder().id(UUID.randomUUID())
                                                        .ap(new BigDecimal("500")).build()));
                        when(scoreRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
                        lenient().when(categoryRepository.findByActiveTrue()).thenReturn(List.of());

                        service.merge(PRIMARY_ID, SECONDARY_ID, STAFF_ID, "dup");

                        assertThat(secScore.isActive()).isFalse();

                        ArgumentCaptor<Score> savedScores = ArgumentCaptor.forClass(Score.class);
                        verify(scoreRepository, org.mockito.Mockito.atLeastOnce()).saveAndFlush(savedScores.capture());
                        List<Score> reassigned = savedScores.getAllValues().stream()
                                        .filter(s -> s.getUser() != null && s.getUser().getId().equals(PRIMARY_ID)
                                                        && s.isActive())
                                        .toList();
                        assertThat(reassigned).isEmpty();
                }

                @Test
                void throwsWhenAlreadyMerged() {
                        UserDuplicateLink existing = UserDuplicateLink.builder()
                                        .id(UUID.randomUUID())
                                        .primaryUser(primaryUser)
                                        .secondaryUser(secondaryUser)
                                        .merged(true)
                                        .build();

                        when(userRepository.findById(PRIMARY_ID)).thenReturn(Optional.of(primaryUser));
                        when(userRepository.findById(SECONDARY_ID)).thenReturn(Optional.of(secondaryUser));
                        when(linkRepository.findBySecondaryUser_Id(SECONDARY_ID)).thenReturn(Optional.of(existing));

                        assertThatThrownBy(() -> service.merge(PRIMARY_ID, SECONDARY_ID, STAFF_ID, "dup"))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("already been merged");
                }

                @Test
                void copiesModifierLinks_whenReassigningScore() {
                        UUID diffId = UUID.randomUUID();
                        MapDifficulty diff = MapDifficulty.builder().id(diffId).build();
                        Modifier nfMod = Modifier.builder().id(UUID.randomUUID()).code("NF").build();

                        Score secScore = Score.builder()
                                        .id(UUID.randomUUID()).user(secondaryUser).mapDifficulty(diff)
                                        .score(900000).scoreNoMods(800000).rank(5).rankWhenSet(5)
                                        .ap(new BigDecimal("400")).weightedAp(new BigDecimal("400"))
                                        .xpGained(new BigDecimal("50")).active(true).build();

                        ScoreModifierLink modLink = ScoreModifierLink.builder()
                                        .id(UUID.randomUUID()).score(secScore).modifier(nfMod).build();

                        when(userRepository.findById(PRIMARY_ID)).thenReturn(Optional.of(primaryUser));
                        when(userRepository.findById(SECONDARY_ID)).thenReturn(Optional.of(secondaryUser));
                        when(linkRepository.findBySecondaryUser_Id(SECONDARY_ID)).thenReturn(Optional.empty());
                        when(linkRepository.existsBySecondaryUser_Id(SECONDARY_ID)).thenReturn(false);
                        when(linkRepository.existsBySecondaryUser_Id(PRIMARY_ID)).thenReturn(false);
                        when(linkRepository.save(any())).thenAnswer(inv -> {
                                UserDuplicateLink l = inv.getArgument(0);
                                l.setId(UUID.randomUUID());
                                l.setCreatedAt(Instant.now());
                                return l;
                        });
                        when(scoreRepository.findByUser_IdAndActiveTrue(SECONDARY_ID))
                                        .thenReturn(List.of(secScore));
                        when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(PRIMARY_ID, diffId))
                                        .thenReturn(Optional.empty());
                        when(scoreRepository.saveAndFlush(any())).thenAnswer(inv -> {
                                Score s = inv.getArgument(0);
                                if (s.getId() == null)
                                        s.setId(UUID.randomUUID());
                                return s;
                        });
                        when(modifierLinkRepository.findByScore_Id(secScore.getId()))
                                        .thenReturn(List.of(modLink));
                        lenient().when(categoryRepository.findByActiveTrue()).thenReturn(List.of());

                        service.merge(PRIMARY_ID, SECONDARY_ID, STAFF_ID, "dup");

                        @SuppressWarnings("unchecked")
                        ArgumentCaptor<List<ScoreModifierLink>> modCaptor = ArgumentCaptor.forClass(List.class);
                        verify(modifierLinkRepository).saveAll(modCaptor.capture());
                        List<ScoreModifierLink> copiedLinks = modCaptor.getValue();
                        assertThat(copiedLinks).hasSize(1);
                        assertThat(copiedLinks.get(0).getModifier().getCode()).isEqualTo("NF");
                        assertThat(copiedLinks.get(0).getScore().getUser().getId()).isEqualTo(PRIMARY_ID);
                }

                @Test
                void usesExistingLink_whenAlreadyLinkedButNotMerged() {
                        UserDuplicateLink existing = UserDuplicateLink.builder()
                                        .id(UUID.randomUUID())
                                        .primaryUser(primaryUser)
                                        .secondaryUser(secondaryUser)
                                        .merged(false)
                                        .createdAt(Instant.now())
                                        .build();

                        when(userRepository.findById(PRIMARY_ID)).thenReturn(Optional.of(primaryUser));
                        when(userRepository.findById(SECONDARY_ID)).thenReturn(Optional.of(secondaryUser));
                        when(linkRepository.findBySecondaryUser_Id(SECONDARY_ID)).thenReturn(Optional.of(existing));
                        when(scoreRepository.findByUser_IdAndActiveTrue(SECONDARY_ID)).thenReturn(List.of());
                        when(linkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
                        lenient().when(categoryRepository.findByActiveTrue()).thenReturn(List.of());

                        DuplicateLinkResponse response = service.merge(PRIMARY_ID, SECONDARY_ID, STAFF_ID, "dup");

                        assertThat(response.isMerged()).isTrue();
                        verify(linkRepository, never()).existsBySecondaryUser_Id(any());
                }
        }

        @Nested
        class DeleteUnmergedLink {

                @Test
                void deletesLinkAndRemovesFromCache() {
                        UUID linkId = UUID.randomUUID();
                        UserDuplicateLink link = UserDuplicateLink.builder()
                                        .id(linkId)
                                        .primaryUser(primaryUser)
                                        .secondaryUser(secondaryUser)
                                        .merged(false)
                                        .build();

                        when(linkRepository.findAll()).thenReturn(List.of(link));
                        service.refreshCache();
                        assertThat(service.resolvePrimaryUserId(SECONDARY_ID)).isEqualTo(PRIMARY_ID);

                        when(linkRepository.findById(linkId)).thenReturn(Optional.of(link));

                        service.deleteUnmergedLink(linkId);

                        verify(linkRepository).delete(link);
                        assertThat(service.resolvePrimaryUserId(SECONDARY_ID)).isEqualTo(SECONDARY_ID);
                }

                @Test
                void throwsWhenLinkIsMerged() {
                        UUID linkId = UUID.randomUUID();
                        UserDuplicateLink link = UserDuplicateLink.builder()
                                        .id(linkId)
                                        .primaryUser(primaryUser)
                                        .secondaryUser(secondaryUser)
                                        .merged(true)
                                        .build();
                        when(linkRepository.findById(linkId)).thenReturn(Optional.of(link));

                        assertThatThrownBy(() -> service.deleteUnmergedLink(linkId))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("Cannot delete a merged link");
                }

                @Test
                void throwsWhenLinkNotFound() {
                        UUID linkId = UUID.randomUUID();
                        when(linkRepository.findById(linkId)).thenReturn(Optional.empty());

                        assertThatThrownBy(() -> service.deleteUnmergedLink(linkId))
                                        .isInstanceOf(ResourceNotFoundException.class);
                }
        }

        @Nested
        class Unmerge {

                @Test
                void reversesActions_reactivatesSecondary() {
                        UUID linkId = UUID.randomUUID();
                        UUID diffId = UUID.randomUUID();
                        MapDifficulty diff = MapDifficulty.builder().id(diffId).build();

                        Score deactivatedSecondary = Score.builder()
                                        .id(UUID.randomUUID()).user(secondaryUser).mapDifficulty(diff)
                                        .score(900000).scoreNoMods(900000).rank(5).rankWhenSet(5)
                                        .ap(new BigDecimal("400")).weightedAp(new BigDecimal("400"))
                                        .active(false).build();
                        Score createdMerged = Score.builder()
                                        .id(UUID.randomUUID()).user(primaryUser).mapDifficulty(diff)
                                        .score(900000).scoreNoMods(900000).rank(5).rankWhenSet(5)
                                        .ap(new BigDecimal("400")).weightedAp(new BigDecimal("400"))
                                        .supersedesReason("User merge").active(true).build();

                        UserDuplicateLink link = UserDuplicateLink.builder()
                                        .id(linkId)
                                        .primaryUser(primaryUser)
                                        .secondaryUser(secondaryUser)
                                        .merged(true)
                                        .mergedAt(Instant.now())
                                        .createdAt(Instant.now())
                                        .build();

                        MergeScoreAction action1 = MergeScoreAction.builder()
                                        .id(UUID.randomUUID()).link(link)
                                        .actionType(ActionType.DEACTIVATED_SECONDARY)
                                        .score(deactivatedSecondary).build();
                        MergeScoreAction action2 = MergeScoreAction.builder()
                                        .id(UUID.randomUUID()).link(link)
                                        .actionType(ActionType.CREATED_MERGED)
                                        .score(createdMerged).build();

                        when(linkRepository.findById(linkId)).thenReturn(Optional.of(link));
                        when(mergeScoreActionRepository.findByLink_Id(linkId))
                                        .thenReturn(List.of(action1, action2));
                        when(scoreRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
                        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
                        when(linkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
                        lenient().when(categoryRepository.findByActiveTrue()).thenReturn(List.of());

                        DuplicateLinkResponse response = service.unmerge(linkId);

                        assertThat(response.isMerged()).isFalse();
                        assertThat(deactivatedSecondary.isActive()).isTrue();
                        assertThat(createdMerged.isActive()).isFalse();
                        assertThat(secondaryUser.isActive()).isTrue();
                        verify(mergeScoreActionRepository).deleteByLink_Id(linkId);
                }

                @Test
                void throwsWhenNotMerged() {
                        UUID linkId = UUID.randomUUID();
                        UserDuplicateLink link = UserDuplicateLink.builder()
                                        .id(linkId)
                                        .primaryUser(primaryUser)
                                        .secondaryUser(secondaryUser)
                                        .merged(false)
                                        .build();
                        when(linkRepository.findById(linkId)).thenReturn(Optional.of(link));

                        assertThatThrownBy(() -> service.unmerge(linkId))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("not been merged");
                }

                @Test
                void throwsWhenLinkNotFound() {
                        UUID linkId = UUID.randomUUID();
                        when(linkRepository.findById(linkId)).thenReturn(Optional.empty());

                        assertThatThrownBy(() -> service.unmerge(linkId))
                                        .isInstanceOf(ResourceNotFoundException.class);
                }

                @Test
                void reactivatesDisplacedPrimaryScore() {
                        UUID linkId = UUID.randomUUID();
                        UUID diffId = UUID.randomUUID();
                        MapDifficulty diff = MapDifficulty.builder().id(diffId).build();

                        Score deactivatedSecondary = Score.builder()
                                        .id(UUID.randomUUID()).user(secondaryUser).mapDifficulty(diff)
                                        .score(950000).ap(new BigDecimal("500")).active(false).build();
                        Score deactivatedPrimary = Score.builder()
                                        .id(UUID.randomUUID()).user(primaryUser).mapDifficulty(diff)
                                        .score(900000).ap(new BigDecimal("400")).active(false).build();
                        Score createdMerged = Score.builder()
                                        .id(UUID.randomUUID()).user(primaryUser).mapDifficulty(diff)
                                        .score(950000).ap(new BigDecimal("500"))
                                        .supersedesReason("User merge").active(true).build();

                        UserDuplicateLink link = UserDuplicateLink.builder()
                                        .id(linkId)
                                        .primaryUser(primaryUser)
                                        .secondaryUser(secondaryUser)
                                        .merged(true)
                                        .mergedAt(Instant.now())
                                        .createdAt(Instant.now())
                                        .build();

                        List<MergeScoreAction> actions = List.of(
                                        MergeScoreAction.builder().id(UUID.randomUUID()).link(link)
                                                        .actionType(ActionType.DEACTIVATED_SECONDARY)
                                                        .score(deactivatedSecondary).build(),
                                        MergeScoreAction.builder().id(UUID.randomUUID()).link(link)
                                                        .actionType(ActionType.DEACTIVATED_PRIMARY)
                                                        .score(deactivatedPrimary).build(),
                                        MergeScoreAction.builder().id(UUID.randomUUID()).link(link)
                                                        .actionType(ActionType.CREATED_MERGED)
                                                        .score(createdMerged).build());

                        when(linkRepository.findById(linkId)).thenReturn(Optional.of(link));
                        when(mergeScoreActionRepository.findByLink_Id(linkId)).thenReturn(actions);
                        when(scoreRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
                        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
                        when(linkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
                        lenient().when(categoryRepository.findByActiveTrue()).thenReturn(List.of());

                        service.unmerge(linkId);

                        assertThat(deactivatedSecondary.isActive()).isTrue();
                        assertThat(deactivatedPrimary.isActive()).isTrue();
                        assertThat(createdMerged.isActive()).isFalse();
                }
        }

        @Nested
        class MergeAllUnmerged {

                private static final Long THIRD_ID = 76561198000000003L;

                @Test
                void mergesAllUnmergedLinks() {
                        User thirdUser = User.builder()
                                        .id(THIRD_ID).name("Third").country("US")
                                        .totalXp(new BigDecimal("200")).active(true).build();

                        UserDuplicateLink link1 = UserDuplicateLink.builder()
                                        .id(UUID.randomUUID())
                                        .primaryUser(primaryUser).secondaryUser(secondaryUser)
                                        .merged(false).reason("dup1").createdAt(Instant.now()).build();
                        UserDuplicateLink link2 = UserDuplicateLink.builder()
                                        .id(UUID.randomUUID())
                                        .primaryUser(primaryUser).secondaryUser(thirdUser)
                                        .merged(false).reason("dup2").createdAt(Instant.now()).build();

                        when(linkRepository.findByMergedFalse()).thenReturn(List.of(link1, link2));
                        when(scoreRepository.findByUser_IdAndActiveTrue(SECONDARY_ID)).thenReturn(List.of());
                        when(scoreRepository.findByUser_IdAndActiveTrue(THIRD_ID)).thenReturn(List.of());
                        when(linkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                        List<DuplicateLinkResponse> results = service.mergeAllUnmerged(STAFF_ID);

                        assertThat(results).hasSize(2);
                        assertThat(results).allMatch(DuplicateLinkResponse::isMerged);
                        assertThat(secondaryUser.isActive()).isFalse();
                        assertThat(thirdUser.isActive()).isFalse();
                }

                @Test
                void returnsEmptyList_whenNoUnmergedLinks() {
                        when(linkRepository.findByMergedFalse()).thenReturn(List.of());

                        List<DuplicateLinkResponse> results = service.mergeAllUnmerged(STAFF_ID);

                        assertThat(results).isEmpty();
                        verify(linkRepository, never()).save(any());
                }
        }
}
