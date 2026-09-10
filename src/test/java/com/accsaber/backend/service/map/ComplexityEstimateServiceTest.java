package com.accsaber.backend.service.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.client.ComplexityModelClient;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyComplexityEstimate;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.map.MapDifficultyComplexityEstimateRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.service.map.NoteAccuracyComplexityRater.Rating;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ComplexityEstimateServiceTest {

    @Mock
    private MapDifficultyRepository mapDifficultyRepository;
    @Mock
    private MapDifficultyComplexityEstimateRepository estimateRepository;
    @Mock
    private NoteAccuracyComplexityRater rater;
    @Mock
    private ComplexityModelClient modelClient;
    @Mock
    private ComplexityScenarioService scenarioService;
    @InjectMocks
    private ComplexityEstimateService service;

    @Test
    void refreshUpsertsTheEstimateAndEvictsScenarios() {
        MapDifficulty difficulty = MapDifficulty.builder().id(UUID.randomUUID()).status(MapDifficultyStatus.RANKED).build();
        when(mapDifficultyRepository.findByStatusAndActiveTrueWithCategory(MapDifficultyStatus.RANKED))
                .thenReturn(List.of(difficulty));
        when(mapDifficultyRepository.findByStatusAndActiveTrueWithCategory(MapDifficultyStatus.QUALIFIED))
                .thenReturn(List.of());
        when(mapDifficultyRepository.findByStatusAndActiveTrueWithCategory(MapDifficultyStatus.QUEUE))
                .thenReturn(List.of());
        when(rater.version()).thenReturn("note-acc-2026-09-11");
        when(rater.rate(difficulty)).thenReturn(Optional.of(new Rating(7.4, Map.of("meanNoteAccuracy", 0.9))));
        when(estimateRepository.findByMapDifficultyId(difficulty.getId())).thenReturn(Optional.empty());
        when(modelClient.health()).thenReturn(Optional.empty());

        service.refreshAllAsync().join();

        ArgumentCaptor<MapDifficultyComplexityEstimate> saved = ArgumentCaptor.forClass(MapDifficultyComplexityEstimate.class);
        verify(estimateRepository).save(saved.capture());
        assertThat(saved.getValue().getComplexity()).isEqualTo(7.4);
        assertThat(saved.getValue().getVersion()).isEqualTo("note-acc-2026-09-11");
        assertThat(saved.getValue().getInputs().get("meanNoteAccuracy").asDouble()).isEqualTo(0.9);
        assertThat(saved.getValue().getMapDifficulty()).isSameAs(difficulty);
        verify(scenarioService).evict();
    }

    @Test
    void aMapTheScriptCannotPriceStoresNothing() {
        MapDifficulty difficulty = MapDifficulty.builder().id(UUID.randomUUID()).build();
        when(estimateRepository.findByMapDifficultyId(difficulty.getId())).thenReturn(Optional.empty());
        when(rater.rate(difficulty)).thenReturn(Optional.empty());

        assertThat(service.refresh(difficulty, null).stored()).isFalse();
        verify(estimateRepository, never()).save(any());
    }

    @Test
    void repricesFromStoredInputsInsteadOfCallingTheModelWhenTheHashMatches() {
        MapDifficulty difficulty = MapDifficulty.builder().id(UUID.randomUUID()).build();
        MapDifficultyComplexityEstimate existing = MapDifficultyComplexityEstimate.builder()
                .id(UUID.randomUUID()).mapDifficulty(difficulty)
                .complexity(7.0).version("old").inputs(new ObjectMapper().createObjectNode())
                .build();
        when(estimateRepository.findByMapDifficultyId(difficulty.getId())).thenReturn(Optional.of(existing));
        when(rater.version()).thenReturn("note-acc-2026-09-11");
        when(rater.reprice(difficulty, existing.getInputs(), "hash"))
                .thenReturn(Optional.of(new Rating(7.5, Map.of("meanNoteAccuracy", 0.9))));

        ComplexityEstimateService.Outcome outcome = service.refresh(difficulty, "hash");

        assertThat(outcome.repriced()).isTrue();
        assertThat(existing.getComplexity()).isEqualTo(7.5);
        assertThat(existing.getVersion()).isEqualTo("note-acc-2026-09-11");
        verify(rater, never()).rate(any());
    }
}
