package com.accsaber.backend.service.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.dto.response.map.MapComplexityHistoryResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyComplexity;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.map.ReweightRound;
import com.accsaber.backend.repository.map.MapDifficultyComplexityRepository;
import com.accsaber.backend.service.map.MapDifficultyComplexityService.RankedChange;

@ExtendWith(MockitoExtension.class)
class MapDifficultyComplexityServiceTest {

    @Mock
    private MapDifficultyComplexityRepository complexityRepository;

    @InjectMocks
    private MapDifficultyComplexityService complexityService;

    @Nested
    class FindActiveComplexity {

        @Test
        void returnsComplexity_whenActiveRecordExists() {
            UUID difficultyId = UUID.randomUUID();
            MapDifficulty diff = buildDifficulty(difficultyId);
            MapDifficultyComplexity complexity = buildComplexity(diff, 7.5, true);
            when(complexityRepository.findByMapDifficultyIdAndActiveTrue(difficultyId))
                    .thenReturn(Optional.of(complexity));

            Optional<Double> result = complexityService.findActiveComplexity(difficultyId);

            assertThat(result).contains(7.5);
        }

        @Test
        void returnsEmpty_whenNoActiveRecord() {
            UUID difficultyId = UUID.randomUUID();
            when(complexityRepository.findByMapDifficultyIdAndActiveTrue(difficultyId))
                    .thenReturn(Optional.empty());

            Optional<Double> result = complexityService.findActiveComplexity(difficultyId);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class FindActiveComplexitiesForDifficulties {

        @Test
        void returnsMapOfComplexitiesByDifficultyId() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            MapDifficulty diff1 = buildDifficulty(id1);
            MapDifficulty diff2 = buildDifficulty(id2);
            List<MapDifficultyComplexity> complexities = List.of(
                    buildComplexity(diff1, 5.0, true),
                    buildComplexity(diff2, 8.0, true));
            when(complexityRepository.findActiveByMapDifficultyIdIn(List.of(id1, id2)))
                    .thenReturn(complexities);

            Map<UUID, Double> result = complexityService.findActiveComplexitiesForDifficulties(List.of(id1, id2));

            assertThat(result)
                    .containsEntry(id1, 5.0)
                    .containsEntry(id2, 8.0);
        }

    }

    @Nested
    class GetHistoryForMap {

        @Test
        void returnsHistoryInDescendingOrder() {
            UUID mapId = UUID.randomUUID();
            MapDifficulty diff = buildDifficulty(UUID.randomUUID());
            MapDifficultyComplexity old = buildComplexity(diff, 6.0, false);
            MapDifficultyComplexity current = buildComplexity(diff, 7.5, true);
            current.setSupersedes(old);
            when(complexityRepository.findAllByMapIdOrderByCreatedAtDesc(mapId))
                    .thenReturn(List.of(current, old));

            List<MapComplexityHistoryResponse> history = complexityService.getHistoryForMap(mapId);

            assertThat(history).hasSize(2);
            assertThat(history.get(0).getComplexity()).isEqualByComparingTo(7.5);
            assertThat(history.get(0).getSupersedesId()).isEqualTo(old.getId());
            assertThat(history.get(1).getComplexity()).isEqualByComparingTo(6.0);
            assertThat(history.get(1).getSupersedesId()).isNull();
        }

        @Test
        void returnsEmptyList_whenNoHistory() {
            UUID mapId = UUID.randomUUID();
            when(complexityRepository.findAllByMapIdOrderByCreatedAtDesc(mapId))
                    .thenReturn(List.of());

            List<MapComplexityHistoryResponse> history = complexityService.getHistoryForMap(mapId);

            assertThat(history).isEmpty();
        }
    }

    @Nested
    class SetComplexity {

        @Test
        void firstComplexity_createsActiveRecord_withNoSupersedes() {
            MapDifficulty diff = buildDifficulty(UUID.randomUUID());
            when(complexityRepository.findActiveForUpdate(diff.getId()))
                    .thenReturn(Optional.empty());
            when(complexityRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

            Double result = complexityService.setComplexity(diff, 9.0, "Initial", 1L);

            assertThat(result).isEqualByComparingTo(9.0);
            ArgumentCaptor<MapDifficultyComplexity> captor = ArgumentCaptor.forClass(MapDifficultyComplexity.class);
            verify(complexityRepository).saveAndFlush(captor.capture());
            MapDifficultyComplexity saved = captor.getValue();
            assertThat(saved.isActive()).isTrue();
            assertThat(saved.getSupersedes()).isNull();
            assertThat(saved.getComplexity()).isEqualByComparingTo(9.0);
        }

        @Test
        void updatingComplexity_deactivatesExistingRecord() {
            MapDifficulty diff = buildDifficulty(UUID.randomUUID(), MapDifficultyStatus.RANKED);
            MapDifficultyComplexity existing = buildComplexity(diff, 5.0, true);
            when(complexityRepository.findActiveForUpdate(diff.getId()))
                    .thenReturn(Optional.of(existing));
            when(complexityRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

            complexityService.setComplexity(diff, 8.0, "Reweight", 42L);

            assertThat(existing.isActive()).isFalse();
            verify(complexityRepository, times(2)).saveAndFlush(any());
        }

        @Test
        void unrankedDifficulty_overwritesActiveRecordInPlace() {
            MapDifficulty diff = buildDifficulty(UUID.randomUUID(), MapDifficultyStatus.QUALIFIED);
            MapDifficultyComplexity existing = buildComplexity(diff, 5.0, true);
            when(complexityRepository.findActiveForUpdate(diff.getId()))
                    .thenReturn(Optional.of(existing));
            when(complexityRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

            complexityService.setComplexity(diff, 8.0, "Set by hand", 42L);

            verify(complexityRepository, times(1)).saveAndFlush(existing);
            assertThat(existing.isActive()).isTrue();
            assertThat(existing.getComplexity()).isEqualByComparingTo(8.0);
            assertThat(existing.getSupersedesReason()).isEqualTo("Set by hand");
            assertThat(existing.getSupersedesAuthor()).isEqualTo(42L);
        }

        @Test
        void newVersion_linksToOldViaSupersedes_andCarriesAuditFields() {
            MapDifficulty diff = buildDifficulty(UUID.randomUUID(), MapDifficultyStatus.RANKED);
            MapDifficultyComplexity existing = buildComplexity(diff, 5.0, true);
            when(complexityRepository.findActiveForUpdate(diff.getId()))
                    .thenReturn(Optional.of(existing));
            when(complexityRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

            complexityService.setComplexity(diff, 8.0, "Reweight", 42L);

            ArgumentCaptor<MapDifficultyComplexity> captor = ArgumentCaptor.forClass(MapDifficultyComplexity.class);
            verify(complexityRepository, times(2)).saveAndFlush(captor.capture());
            MapDifficultyComplexity newVersion = captor.getAllValues().get(1);
            assertThat(newVersion.getSupersedes()).isEqualTo(existing);
            assertThat(newVersion.getSupersedesReason()).isEqualTo("Reweight");
            assertThat(newVersion.getSupersedesAuthor()).isEqualTo(42L);
            assertThat(newVersion.isActive()).isTrue();
            assertThat(newVersion.getComplexity()).isEqualByComparingTo(8.0);
        }

    }

    @Test
    void supersedeAll_deactivatesCurrentAndStampsTheCategoryRound() {
        Category category = Category.builder().id(UUID.randomUUID()).code("true_acc").build();
        MapDifficulty diff = buildDifficulty(UUID.randomUUID(), MapDifficultyStatus.RANKED);
        diff.setCategory(category);
        MapDifficultyComplexity existing = buildComplexity(diff, 5.0, true);
        ReweightRound round = ReweightRound.builder().id(UUID.randomUUID()).category(category).build();

        complexityService.supersedeAll(List.of(new RankedChange(diff, existing, 6.0, "september")),
                Map.of(category.getId(), round), 42L);

        ArgumentCaptor<List<MapDifficultyComplexity>> captor = ArgumentCaptor.captor();
        verify(complexityRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(existing.isActive()).isFalse();
        MapDifficultyComplexity newVersion = captor.getValue().get(1);
        assertThat(newVersion.getSupersedes()).isSameAs(existing);
        assertThat(newVersion.getRound()).isSameAs(round);
        assertThat(newVersion.getComplexity()).isEqualTo(6.0);
        assertThat(newVersion.getSupersedesAuthor()).isEqualTo(42L);
        assertThat(newVersion.isActive()).isTrue();
    }

    private MapDifficulty buildDifficulty(UUID id) {
        return buildDifficulty(id, MapDifficultyStatus.QUEUE);
    }

    private MapDifficulty buildDifficulty(UUID id, MapDifficultyStatus status) {
        return MapDifficulty.builder()
                .id(id)
                .difficulty(Difficulty.EXPERT_PLUS)
                .characteristic("Standard")
                .status(status)
                .active(true)
                .build();
    }

    private MapDifficultyComplexity buildComplexity(MapDifficulty diff, Double value, boolean active) {
        return MapDifficultyComplexity.builder()
                .id(UUID.randomUUID())
                .mapDifficulty(diff)
                .complexity(value)
                .active(active)
                .build();
    }
}
