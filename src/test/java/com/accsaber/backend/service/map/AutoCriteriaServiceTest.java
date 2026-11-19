package com.accsaber.backend.service.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.client.CriteriaCheckerClient;
import com.accsaber.backend.exception.TooManyRequestsException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.map.CriteriaCheckRequest;
import com.accsaber.backend.model.dto.response.map.AutoCriteriaCheckResponse;
import com.accsaber.backend.model.entity.AutoCriteriaStatus;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;

@ExtendWith(MockitoExtension.class)
class AutoCriteriaServiceTest {

    private static final Long PLAYER = 76561198000000001L;
    private static final String HASH = "81b24f7bb979363a41a92114327ec6a69f4421d1";
    private static final byte[] ZIP = { 'P', 'K', 3, 4 };

    @Mock
    private MapDifficultyRepository mapDifficultyRepository;

    @Mock
    private MapZipCache zipCache;

    @Mock
    private CriteriaCheckerClient criteriaCheckerClient;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CriteriaCheckRateLimitService rateLimitService;

    @InjectMocks
    private AutoCriteriaService service;

    private final UUID categoryId = UUID.randomUUID();

    private CriteriaCheckRequest twoDifficulties() {
        return new CriteriaCheckRequest(HASH, List.of(
                new CriteriaCheckRequest.Check(Difficulty.EXPERT, categoryId),
                new CriteriaCheckRequest.Check(Difficulty.EXPERT_PLUS, categoryId)));
    }

    @Test
    void severalDifficultiesByHashCostOneCheckAndReadTheCachedZipOnce() {
        when(rateLimitService.tryAcquire(PLAYER)).thenReturn(true);
        when(categoryRepository.requireActive(categoryId)).thenReturn(Category.builder().code("true_acc").build());
        when(zipCache.get(HASH)).thenReturn(Optional.of(ZIP));
        CriteriaCheckerClient.CheckResult passed = new CriteriaCheckerClient.CheckResult();
        passed.setStatus("passed");
        CriteriaCheckerClient.CheckResult failed = new CriteriaCheckerClient.CheckResult();
        failed.setStatus("failed");
        failed.setFailures(List.of("vision block at 1:20"));
        when(criteriaCheckerClient.check(ZIP, Difficulty.EXPERT.getDbValue(), "true")).thenReturn(passed);
        when(criteriaCheckerClient.check(ZIP, Difficulty.EXPERT_PLUS.getDbValue(), "true")).thenReturn(failed);

        List<AutoCriteriaCheckResponse> results = service.check(PLAYER, twoDifficulties(), null);

        assertThat(results).extracting(AutoCriteriaCheckResponse::getDifficulty, AutoCriteriaCheckResponse::getStatus)
                .containsExactly(tuple(Difficulty.EXPERT, AutoCriteriaStatus.PASSED),
                        tuple(Difficulty.EXPERT_PLUS, AutoCriteriaStatus.FAILED));
        assertThat(results.get(1).getFailures()).containsExactly("vision block at 1:20");
        verify(rateLimitService, times(1)).tryAcquire(PLAYER);
        verify(zipCache, times(1)).get(HASH);
        verify(categoryRepository, times(1)).requireActive(categoryId);
    }

    @Test
    void aMapThatIsNotOnBeatSaverAsksForTheZip() {
        when(rateLimitService.tryAcquire(PLAYER)).thenReturn(true);
        when(categoryRepository.requireActive(categoryId)).thenReturn(Category.builder().code("true_acc").build());
        when(zipCache.get(HASH)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.check(PLAYER, twoDifficulties(), null))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(criteriaCheckerClient);
    }

    @Test
    void aPlayerOverTheLimitNeverReachesTheZipOrTheChecker() {
        when(rateLimitService.tryAcquire(PLAYER)).thenReturn(false);

        assertThatThrownBy(() -> service.check(PLAYER, twoDifficulties(), null))
                .isInstanceOf(TooManyRequestsException.class);
        verifyNoInteractions(zipCache, criteriaCheckerClient, categoryRepository);
    }
}
