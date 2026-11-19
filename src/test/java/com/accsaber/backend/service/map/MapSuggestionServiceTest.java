package com.accsaber.backend.service.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.map.SuggestMapRequest;
import com.accsaber.backend.model.dto.response.map.MapDifficultyResponse;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.map.MapDifficultyRepository;

@ExtendWith(MockitoExtension.class)
class MapSuggestionServiceTest {

    private static final Long PLAYER = 76561198000000001L;

    @Mock
    private MapImportService mapImportService;

    @Mock
    private MapService mapService;

    @Mock
    private MapDifficultyRepository mapDifficultyRepository;

    @Mock
    private MapSubscriptionService subscriptionService;

    @Mock
    private MapSuggestionRateLimitService rateLimitService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private MapSuggestionService service;

    private void allowSuggesting() {
        when(rateLimitService.tryAcquire(PLAYER)).thenReturn(true);
        when(transactionTemplate.execute(any())).thenAnswer(
                call -> call.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
    }

    @Test
    void aSuggestionIsImportedAsSuggestedAndCreditedToThePlayer() {
        allowSuggesting();
        UUID id = UUID.randomUUID();
        MapDifficulty difficulty = MapDifficulty.builder().id(id).status(MapDifficultyStatus.SUGGESTED).build();
        when(mapImportService.importByLeaderboardIds(any(), isNull(), eq(MapDifficultyStatus.SUGGESTED)))
                .thenReturn(MapDifficultyResponse.builder().id(id).build());
        when(mapDifficultyRepository.findById(id)).thenReturn(Optional.of(difficulty));

        service.suggest(PLAYER, new SuggestMapRequest("bl_1", null, UUID.randomUUID()));

        assertThat(difficulty.getImportedBy()).isEqualTo(PLAYER);
        verify(subscriptionService).subscribe(id, PLAYER);
    }

    @Test
    void aPlayerAtTheCapCannotSuggestAnother() {
        allowSuggesting();
        when(mapDifficultyRepository.countByImportedByAndStatusAndActiveTrue(PLAYER, MapDifficultyStatus.SUGGESTED))
                .thenReturn((long) MapSuggestionService.MAX_OPEN_SUGGESTIONS);

        assertThatThrownBy(() -> service.suggest(PLAYER, new SuggestMapRequest("bl_1", null, UUID.randomUUID())))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(mapImportService);
    }

    @Test
    void onlyTheSuggesterCanWithdrawAndOnlyWhileItIsStillASuggestion() {
        MapDifficulty mine = MapDifficulty.builder().id(UUID.randomUUID()).status(MapDifficultyStatus.SUGGESTED)
                .importedBy(PLAYER).build();
        MapDifficulty queued = MapDifficulty.builder().id(UUID.randomUUID()).status(MapDifficultyStatus.QUEUE)
                .importedBy(PLAYER).build();
        when(mapDifficultyRepository.findActiveForUpdate(mine.getId())).thenReturn(Optional.of(mine));
        when(mapDifficultyRepository.findActiveForUpdate(queued.getId())).thenReturn(Optional.of(queued));

        assertThatThrownBy(() -> service.withdraw(mine.getId(), PLAYER + 1)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.withdraw(queued.getId(), PLAYER)).isInstanceOf(ValidationException.class);
        verifyNoInteractions(mapService);

        service.withdraw(mine.getId(), PLAYER);

        verify(mapService).withdrawSuggestion(mine);
    }

    @Test
    void onlyASuggestionCanBeAddedToTheQueue() {
        UUID id = UUID.randomUUID();
        when(mapDifficultyRepository.findActiveForUpdate(id)).thenReturn(Optional.of(
                MapDifficulty.builder().id(id).status(MapDifficultyStatus.RANKED).build()));

        assertThatThrownBy(() -> service.accept(id, UUID.randomUUID())).isInstanceOf(ValidationException.class);
        verifyNoInteractions(mapService);
    }
}
