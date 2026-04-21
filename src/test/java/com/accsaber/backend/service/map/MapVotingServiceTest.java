package com.accsaber.backend.service.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.test.util.ReflectionTestUtils;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.map.VoteListResponse;
import com.accsaber.backend.model.dto.response.map.VoteResponse;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.map.MapVoteAction;
import com.accsaber.backend.model.entity.map.StaffMapVote;
import com.accsaber.backend.model.entity.map.VoteType;
import com.accsaber.backend.model.entity.staff.StaffRole;
import com.accsaber.backend.model.entity.staff.StaffUser;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.map.StaffMapVoteRepository;
import com.accsaber.backend.repository.staff.StaffUserRepository;

@ExtendWith(MockitoExtension.class)
class MapVotingServiceTest {

        @Mock
        private StaffMapVoteRepository voteRepository;

        @Mock
        private MapDifficultyRepository mapDifficultyRepository;

        @Mock
        private StaffUserRepository staffUserRepository;

        @InjectMocks
        private MapVotingService votingService;

        @BeforeEach
        void setUp() {
                ReflectionTestUtils.setField(votingService, "rankThreshold", 3);
                ReflectionTestUtils.setField(votingService, "reweightThreshold", 3);
                ReflectionTestUtils.setField(votingService, "unrankThreshold", 3);
        }

        @Nested
        class CastVote {

                @Test
                void throwsNotFound_whenDifficultyDoesNotExist() {
                        UUID diffId = UUID.randomUUID();
                        when(mapDifficultyRepository.findByIdAndActiveTrue(diffId)).thenReturn(Optional.empty());

                        assertThatThrownBy(() -> votingService.castVote(diffId, UUID.randomUUID(),
                                        VoteType.UPVOTE, MapVoteAction.RANK, null, null, null, null, StaffRole.RANKING))
                                        .isInstanceOf(ResourceNotFoundException.class);
                }

                @Test
                void throwsValidation_whenRankVoteOnRankedDifficulty() {
                        MapDifficulty ranked = buildDifficulty(MapDifficultyStatus.RANKED);
                        when(mapDifficultyRepository.findByIdAndActiveTrue(ranked.getId()))
                                        .thenReturn(Optional.of(ranked));

                        assertThatThrownBy(() -> votingService.castVote(ranked.getId(), UUID.randomUUID(),
                                        VoteType.UPVOTE, MapVoteAction.RANK, null, null, null, null, StaffRole.RANKING))
                                        .isInstanceOf(ValidationException.class);
                }

                @Test
                void throwsValidation_whenReweightVoteOnQueueDifficulty() {
                        MapDifficulty queue = buildDifficulty(MapDifficultyStatus.QUEUE);
                        when(mapDifficultyRepository.findByIdAndActiveTrue(queue.getId()))
                                        .thenReturn(Optional.of(queue));

                        assertThatThrownBy(() -> votingService.castVote(queue.getId(), UUID.randomUUID(),
                                        VoteType.UPVOTE, MapVoteAction.REWEIGHT, BigDecimal.valueOf(7.5), null, null,
                                        null, StaffRole.RANKING))
                                        .isInstanceOf(ValidationException.class);
                }

                @Test
                void throwsValidation_whenReweightVoteMissingSuggestedComplexity() {
                        MapDifficulty ranked = buildDifficulty(MapDifficultyStatus.RANKED);
                        when(mapDifficultyRepository.findByIdAndActiveTrue(ranked.getId()))
                                        .thenReturn(Optional.of(ranked));

                        assertThatThrownBy(() -> votingService.castVote(ranked.getId(), UUID.randomUUID(),
                                        VoteType.UPVOTE, MapVoteAction.REWEIGHT, null, null, null, null,
                                        StaffRole.RANKING))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("suggestedComplexity");
                }

                @Test
                void createsNewVote_whenNoExistingVote() {
                        MapDifficulty diff = buildDifficulty(MapDifficultyStatus.QUEUE);
                        UUID staffId = UUID.randomUUID();
                        when(mapDifficultyRepository.findByIdAndActiveTrue(diff.getId()))
                                        .thenReturn(Optional.of(diff));
                        when(voteRepository.findByMapDifficultyIdAndStaffIdAndActiveTrue(
                                        diff.getId(), staffId))
                                        .thenReturn(Optional.empty());
                        when(voteRepository.save(any())).thenAnswer(inv -> {
                                StaffMapVote v = inv.getArgument(0);
                                return StaffMapVote.builder()
                                                .id(UUID.randomUUID())
                                                .mapDifficulty(v.getMapDifficulty())
                                                .staffId(v.getStaffId())
                                                .vote(v.getVote())
                                                .type(v.getType())
                                                .reason(v.getReason())
                                                .build();
                        });
                        when(staffUserRepository.findAllByIdWithUser(List.of(staffId)))
                                        .thenReturn(List.of(
                                                        StaffUser.builder().id(staffId).username("tester").build()));

                        VoteResponse response = votingService.castVote(diff.getId(), staffId,
                                        VoteType.UPVOTE, MapVoteAction.RANK, null, "Looks good", null, null,
                                        StaffRole.RANKING);

                        assertThat(response.getVote()).isEqualTo(VoteType.UPVOTE);
                        assertThat(response.getType()).isEqualTo(MapVoteAction.RANK);
                        assertThat(response.getReason()).isEqualTo("Looks good");
                        assertThat(response.getStaffId()).isEqualTo(staffId);
                }

                @Test
                void updatesExistingVote_whenStaffAlreadyVoted() {
                        MapDifficulty diff = buildDifficulty(MapDifficultyStatus.QUEUE);
                        UUID staffId = UUID.randomUUID();
                        StaffMapVote existing = buildVote(diff, staffId, VoteType.UPVOTE, MapVoteAction.RANK, null,
                                        "First");
                        when(mapDifficultyRepository.findByIdAndActiveTrue(diff.getId()))
                                        .thenReturn(Optional.of(diff));
                        when(voteRepository.findByMapDifficultyIdAndStaffIdAndActiveTrue(
                                        diff.getId(), staffId))
                                        .thenReturn(Optional.of(existing));
                        when(voteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
                        when(staffUserRepository.findAllByIdWithUser(List.of(staffId)))
                                        .thenReturn(List.of(
                                                        StaffUser.builder().id(staffId).username("tester").build()));

                        votingService.castVote(diff.getId(), staffId, VoteType.DOWNVOTE, MapVoteAction.RANK, null,
                                        "Changed", null, null, StaffRole.RANKING);

                        assertThat(existing.getVote()).isEqualTo(VoteType.DOWNVOTE);
                        assertThat(existing.getReason()).isEqualTo("Changed");
                }
        }

        @Nested
        class GetVotes {

                @Test
                void returnsVoteListWithThresholdFlags() {
                        UUID diffId = UUID.randomUUID();
                        when(voteRepository.findByMapDifficultyIdAndActiveTrue(diffId)).thenReturn(List.of());
                        when(voteRepository.countByMapDifficultyIdAndTypeAndVoteAndActiveTrue(
                                        diffId, MapVoteAction.RANK, VoteType.UPVOTE)).thenReturn(0L);
                        when(voteRepository.countByMapDifficultyIdAndTypeAndVoteAndActiveTrue(
                                        diffId, MapVoteAction.RANK, VoteType.DOWNVOTE)).thenReturn(0L);
                        when(voteRepository.countByMapDifficultyIdAndTypeAndVoteAndActiveTrue(
                                        diffId, MapVoteAction.REWEIGHT, VoteType.UPVOTE)).thenReturn(3L);
                        when(voteRepository.countByMapDifficultyIdAndTypeAndVoteAndActiveTrue(
                                        diffId, MapVoteAction.REWEIGHT, VoteType.DOWNVOTE)).thenReturn(0L);
                        when(voteRepository.countByMapDifficultyIdAndTypeAndVoteAndActiveTrue(
                                        diffId, MapVoteAction.UNRANK, VoteType.UPVOTE)).thenReturn(1L);
                        when(voteRepository.countByMapDifficultyIdAndTypeAndVoteAndActiveTrue(
                                        diffId, MapVoteAction.UNRANK, VoteType.DOWNVOTE)).thenReturn(0L);

                        VoteListResponse result = votingService.getVotes(diffId, MapVoteAction.RANK);

                        assertThat(result.isRankReady()).isFalse();
                        assertThat(result.isReweightReady()).isTrue();
                        assertThat(result.isUnrankReady()).isFalse();
                }
        }

        @Nested
        class DeactivateVote {

                @Test
                void throwsNotFound_whenVoteDoesNotExist() {
                        UUID difficultyId = UUID.randomUUID();
                        UUID voteId = UUID.randomUUID();
                        when(voteRepository.findById(voteId)).thenReturn(Optional.empty());

                        assertThatThrownBy(() -> votingService.deactivateVote(difficultyId, voteId))
                                        .isInstanceOf(ResourceNotFoundException.class);
                }

                @Test
                void setsActiveFalse_whenVoteExists() {
                        MapDifficulty difficulty = MapDifficulty.builder().id(UUID.randomUUID()).build();
                        StaffMapVote vote = StaffMapVote.builder()
                                        .id(UUID.randomUUID())
                                        .mapDifficulty(difficulty)
                                        .active(true)
                                        .build();
                        when(voteRepository.findById(vote.getId())).thenReturn(Optional.of(vote));
                        when(voteRepository.save(any())).thenReturn(vote);
                        when(mapDifficultyRepository.findByIdAndActiveTrue(difficulty.getId()))
                                        .thenReturn(Optional.of(difficulty));

                        votingService.deactivateVote(difficulty.getId(), vote.getId());

                        assertThat(vote.isActive()).isFalse();
                        verify(voteRepository).save(vote);
                }
        }

        private MapDifficulty buildDifficulty(MapDifficultyStatus status) {
                return MapDifficulty.builder()
                                .id(UUID.randomUUID())
                                .map(com.accsaber.backend.model.entity.map.Map.builder()
                                                .id(UUID.randomUUID())
                                                .songName("Test Song")
                                                .songAuthor("Test Author")
                                                .mapAuthor("Test Mapper")
                                                .build())
                                .difficulty(Difficulty.EXPERT_PLUS)
                                .characteristic("Standard")
                                .status(status)
                                .active(true)
                                .build();
        }

        private StaffMapVote buildVote(MapDifficulty diff, UUID staffId, VoteType vote,
                        MapVoteAction type, BigDecimal suggestedComplexity, String reason) {
                return StaffMapVote.builder()
                                .id(UUID.randomUUID())
                                .mapDifficulty(diff)
                                .staffId(staffId)
                                .vote(vote)
                                .type(type)
                                .suggestedComplexity(suggestedComplexity)
                                .reason(reason)
                                .active(true)
                                .build();
        }
}
