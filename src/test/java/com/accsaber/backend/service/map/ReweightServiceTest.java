package com.accsaber.backend.service.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.map.UpdateMapComplexityRequest;
import com.accsaber.backend.model.dto.response.map.MapDifficultyResponse;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.service.score.ScoreRecalculationService;

@ExtendWith(MockitoExtension.class)
class ReweightServiceTest {

    @Mock
    private MapDifficultyRepository mapDifficultyRepository;

    @Mock
    private MapService mapService;

    @Mock
    private ScoreRecalculationService scoreRecalculationService;

    @InjectMocks
    private ReweightService reweightService;

    @Test
    void throwsNotFound_whenDifficultyDoesNotExist() {
        UUID diffId = UUID.randomUUID();
        when(mapDifficultyRepository.findByIdAndActiveTrue(diffId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reweightService.reweight(diffId, BigDecimal.valueOf(8.0), null, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void throwsValidation_whenDifficultyNotRanked() {
        MapDifficulty diff = buildDifficulty(MapDifficultyStatus.QUEUE);
        when(mapDifficultyRepository.findByIdAndActiveTrue(diff.getId())).thenReturn(Optional.of(diff));

        assertThatThrownBy(() -> reweightService.reweight(diff.getId(), BigDecimal.valueOf(8.0), null, null, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("RANKED");
    }

    @Test
    void callsUpdateComplexityAndReturnsDifficultyResponse() {
        MapDifficulty diff = buildDifficulty(MapDifficultyStatus.RANKED);
        MapDifficultyResponse expected = MapDifficultyResponse.builder()
                .id(diff.getId())
                .status(MapDifficultyStatus.RANKED)
                .build();

        when(mapDifficultyRepository.findByIdAndActiveTrue(diff.getId())).thenReturn(Optional.of(diff));
        when(mapService.updateComplexity(eq(diff.getId()), any(UpdateMapComplexityRequest.class), any(), any()))
                .thenReturn(expected);
        when(mapService.getDifficultyResponse(diff.getId())).thenReturn(expected);

        MapDifficultyResponse result = reweightService.reweight(diff.getId(), BigDecimal.valueOf(8.5), "Reweight", null,
                null);

        assertThat(result).isEqualTo(expected);
        verify(mapService).updateComplexity(eq(diff.getId()), any(), any(), any());
        verify(scoreRecalculationService).recalculateScoresAsync(diff.getId());
    }

    private MapDifficulty buildDifficulty(MapDifficultyStatus status) {
        return MapDifficulty.builder()
                .id(UUID.randomUUID())
                .difficulty(Difficulty.EXPERT_PLUS)
                .characteristic("Standard")
                .status(status)
                .active(true)
                .build();
    }
}
