package com.accsaber.backend.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.client.ComplexityModelClient;
import com.accsaber.backend.client.ComplexityModelClient.Health;
import com.accsaber.backend.model.entity.map.MapDifficultyComplexityEstimate;
import com.accsaber.backend.repository.map.MapDifficultyComplexityEstimateRepository;
import com.accsaber.backend.service.admin.AdminJobService;
import com.accsaber.backend.service.admin.JobRegistry;
import com.accsaber.backend.service.admin.JobType;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ComplexityEstimateRefreshSchedulerTest {

    @Mock
    private ComplexityModelClient modelClient;
    @Mock
    private MapDifficultyComplexityEstimateRepository estimateRepository;
    @Mock
    private JobRegistry jobRegistry;
    @Mock
    private AdminJobService adminJobService;

    @InjectMocks
    private ComplexityEstimateRefreshScheduler scheduler;

    private static Health health(String hash) {
        Health health = new Health();
        health.setStatus("ok");
        health.setModel("note-acc-beatleader");
        health.setModelHash(hash);
        return health;
    }

    private static MapDifficultyComplexityEstimate estimateMadeWith(String hash) {
        return MapDifficultyComplexityEstimate.builder()
                .inputs(new ObjectMapper().valueToTree(Map.of("modelHash", hash)))
                .build();
    }

    @Test
    void refreshesWhenNoEstimateExistsYet() {
        when(jobRegistry.isRunning(JobType.REFRESH_COMPLEXITY_ESTIMATES)).thenReturn(false);
        when(modelClient.health()).thenReturn(Optional.of(health("abc")));
        when(estimateRepository.findFirstByOrderByUpdatedAtDesc())
                .thenReturn(Optional.empty());

        scheduler.check();

        verify(adminJobService).run(argThat(request -> request.getType() == JobType.REFRESH_COMPLEXITY_ESTIMATES));
    }

    @Test
    void refreshesWhenTheSidecarRunsADifferentModel() {
        when(jobRegistry.isRunning(JobType.REFRESH_COMPLEXITY_ESTIMATES)).thenReturn(false);
        when(modelClient.health()).thenReturn(Optional.of(health("new")));
        when(estimateRepository.findFirstByOrderByUpdatedAtDesc())
                .thenReturn(Optional.of(estimateMadeWith("old")));

        scheduler.check();

        verify(adminJobService).run(any());
    }

    @Test
    void staysQuietWhenEstimatesAlreadyMatchTheModel() {
        when(jobRegistry.isRunning(JobType.REFRESH_COMPLEXITY_ESTIMATES)).thenReturn(false);
        when(modelClient.health()).thenReturn(Optional.of(health("same")));
        when(estimateRepository.findFirstByOrderByUpdatedAtDesc())
                .thenReturn(Optional.of(estimateMadeWith("same")));

        scheduler.check();

        verify(adminJobService, never()).run(any());
    }

    @Test
    void staysQuietWhileARefreshIsRunningOrTheSidecarIsDown() {
        when(jobRegistry.isRunning(JobType.REFRESH_COMPLEXITY_ESTIMATES)).thenReturn(true);
        scheduler.check();

        when(jobRegistry.isRunning(JobType.REFRESH_COMPLEXITY_ESTIMATES)).thenReturn(false);
        when(modelClient.health()).thenReturn(Optional.empty());
        scheduler.check();

        verify(adminJobService, never()).run(any());
    }
}
