package com.accsaber.backend.service.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.entity.map.ComplexityEstimateSource;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyComplexityEstimate;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.map.MapDifficultyComplexityEstimateRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;

@ExtendWith(MockitoExtension.class)
class ComplexityEstimateServiceTest {

    @Mock
    private MapDifficultyRepository mapDifficultyRepository;
    @Mock
    private MapDifficultyComplexityEstimateRepository estimateRepository;
    @Mock
    private ComplexityRater oldScript;
    @Mock
    private ComplexityRater newScript;
    @Mock
    private ComplexityScenarioService scenarioService;

    @Test
    void refreshUpsertsOneEstimatePerRaterAndEvictsScenarios() {
        MapDifficulty difficulty = MapDifficulty.builder().id(UUID.randomUUID()).status(MapDifficultyStatus.RANKED).build();
        when(mapDifficultyRepository.findByStatusAndActiveTrueWithCategory(MapDifficultyStatus.RANKED))
                .thenReturn(List.of(difficulty));
        when(mapDifficultyRepository.findByStatusAndActiveTrueWithCategory(MapDifficultyStatus.QUALIFIED))
                .thenReturn(List.of());
        when(mapDifficultyRepository.findByStatusAndActiveTrueWithCategory(MapDifficultyStatus.QUEUE))
                .thenReturn(List.of());
        when(oldScript.source()).thenReturn(ComplexityEstimateSource.OLD_SCRIPT);
        when(oldScript.version()).thenReturn("ai-acc-curve");
        when(oldScript.rate(difficulty)).thenReturn(Optional.of(new ComplexityRater.Rating(9.1, Map.of("aiAccuracy", 0.99))));
        when(newScript.source()).thenReturn(ComplexityEstimateSource.NEW_SCRIPT);
        when(newScript.version()).thenReturn("note-acc-2026-09");
        when(newScript.rate(difficulty)).thenReturn(Optional.of(new ComplexityRater.Rating(7.4, Map.of("meanNoteAccuracy", 0.9))));
        MapDifficultyComplexityEstimate existing = MapDifficultyComplexityEstimate.builder()
                .id(UUID.randomUUID()).mapDifficulty(difficulty).source(ComplexityEstimateSource.OLD_SCRIPT)
                .complexity(12.0).version("stale").build();
        when(estimateRepository.findByMapDifficultyIdAndSource(difficulty.getId(), ComplexityEstimateSource.OLD_SCRIPT))
                .thenReturn(Optional.of(existing));
        when(estimateRepository.findByMapDifficultyIdAndSource(difficulty.getId(), ComplexityEstimateSource.NEW_SCRIPT))
                .thenReturn(Optional.empty());

        ComplexityEstimateService service = new ComplexityEstimateService(mapDifficultyRepository, estimateRepository,
                List.of(oldScript, newScript), scenarioService);
        service.refreshAllAsync().join();

        ArgumentCaptor<MapDifficultyComplexityEstimate> saved = ArgumentCaptor.forClass(MapDifficultyComplexityEstimate.class);
        verify(estimateRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0)).isSameAs(existing);
        assertThat(existing.getComplexity()).isEqualTo(9.1);
        assertThat(existing.getVersion()).isEqualTo("ai-acc-curve");
        assertThat(existing.getInputs().get("aiAccuracy").asDouble()).isEqualTo(0.99);
        MapDifficultyComplexityEstimate created = saved.getAllValues().get(1);
        assertThat(created.getSource()).isEqualTo(ComplexityEstimateSource.NEW_SCRIPT);
        assertThat(created.getComplexity()).isEqualTo(7.4);
        assertThat(created.getMapDifficulty()).isSameAs(difficulty);
        verify(scenarioService).evict();
    }

    @Test
    void ratersThatReturnNothingStoreNothing() {
        MapDifficulty difficulty = MapDifficulty.builder().id(UUID.randomUUID()).build();
        when(newScript.rate(difficulty)).thenReturn(Optional.empty());

        ComplexityEstimateService service = new ComplexityEstimateService(mapDifficultyRepository, estimateRepository,
                List.of(newScript), scenarioService);

        assertThat(service.refresh(difficulty)).isZero();
        verify(estimateRepository, org.mockito.Mockito.never()).save(any());
    }
}
