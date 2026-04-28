package com.accsaber.backend.service.snipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.dto.response.score.SnipeComparisonResponse;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.score.ScoreService;

@ExtendWith(MockitoExtension.class)
class SnipeServiceTest {

    private static final Long SNIPER_ID = 76561198000000001L;
    private static final Long TARGET_ID = 76561198000000002L;

    @Mock
    private ScoreRepository scoreRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ScoreService scoreService;

    @InjectMocks
    private SnipeService snipeService;

    @Nested
    class FindClosestScores {

        @Test
        void returnsComparisonsWithCorrectDelta() {
            Pageable pageable = PageRequest.of(0, 10);
            Score sniperScore = scoreWithValue(900_000);
            Score targetScore = scoreWithValue(950_000);
            Page<Object[]> page = new PageImpl<>(List.<Object[]>of(new Object[] { targetScore, sniperScore }));

            mockUsersExist();
            when(scoreRepository.findClosestSnipePairs(SNIPER_ID, TARGET_ID, pageable)).thenReturn(page);
            ScoreResponse sniperResponse = ScoreResponse.builder().build();
            ScoreResponse targetResponse = ScoreResponse.builder().build();
            when(scoreService.mapToResponse(sniperScore)).thenReturn(sniperResponse);
            when(scoreService.mapToResponse(targetScore)).thenReturn(targetResponse);

            Page<SnipeComparisonResponse> result = snipeService.findClosestScores(SNIPER_ID, TARGET_ID, pageable);

            assertThat(result.getContent()).hasSize(1);
            SnipeComparisonResponse comparison = result.getContent().get(0);
            assertThat(comparison.getSniperScore()).isSameAs(sniperResponse);
            assertThat(comparison.getTargetScore()).isSameAs(targetResponse);
            assertThat(comparison.getScoreDelta()).isEqualTo(50_000);
        }

        @Test
        void emptyResultProducesEmptyPage() {
            Pageable pageable = PageRequest.of(0, 10);
            mockUsersExist();
            when(scoreRepository.findClosestSnipePairs(SNIPER_ID, TARGET_ID, pageable))
                    .thenReturn(new PageImpl<>(List.<Object[]>of()));

            Page<SnipeComparisonResponse> result = snipeService.findClosestScores(SNIPER_ID, TARGET_ID, pageable);

            assertThat(result.getContent()).isEmpty();
            verify(scoreService, never()).mapToResponse(any());
        }

        @Test
        void rejectsSelfSnipe() {
            assertThatThrownBy(() -> snipeService.findClosestScores(SNIPER_ID, SNIPER_ID, PageRequest.of(0, 10)))
                    .isInstanceOf(ValidationException.class);
            verify(userRepository, never()).findByIdAndActiveTrue(any());
        }

        @Test
        void throwsWhenSniperMissing() {
            when(userRepository.findByIdAndActiveTrue(SNIPER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> snipeService.findClosestScores(SNIPER_ID, TARGET_ID, PageRequest.of(0, 10)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void throwsWhenTargetMissing() {
            when(userRepository.findByIdAndActiveTrue(SNIPER_ID))
                    .thenReturn(Optional.of(User.builder().id(SNIPER_ID).build()));
            when(userRepository.findByIdAndActiveTrue(TARGET_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> snipeService.findClosestScores(SNIPER_ID, TARGET_ID, PageRequest.of(0, 10)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    private void mockUsersExist() {
        when(userRepository.findByIdAndActiveTrue(eq(SNIPER_ID)))
                .thenReturn(Optional.of(User.builder().id(SNIPER_ID).name("Sniper").build()));
        when(userRepository.findByIdAndActiveTrue(eq(TARGET_ID)))
                .thenReturn(Optional.of(User.builder().id(TARGET_ID).name("Target").build()));
    }

    private Score scoreWithValue(int value) {
        return Score.builder()
                .id(UUID.randomUUID())
                .score(value)
                .build();
    }
}
