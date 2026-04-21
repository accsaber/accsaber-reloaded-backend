package com.accsaber.backend.service.map;

import java.util.List;
import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.client.BeatSaverClient;
import com.accsaber.backend.client.CriteriaCheckerClient;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.response.map.AutoCriteriaCheckResponse;
import com.accsaber.backend.model.entity.AutoCriteriaStatus;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.repository.map.MapDifficultyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoCriteriaService {

    private final MapDifficultyRepository mapDifficultyRepository;
    private final BeatSaverClient beatSaverClient;
    private final CriteriaCheckerClient criteriaCheckerClient;

    @Transactional
    public AutoCriteriaCheckResponse runCheck(UUID mapDifficultyId) {
        return doCheck(mapDifficultyId);
    }

    @Async("taskExecutor")
    @Transactional
    public void runCheckAsync(UUID mapDifficultyId) {
        try {
            doCheck(mapDifficultyId);
        } catch (Exception e) {
            log.warn("Auto criteria check failed for difficulty {}: {}", mapDifficultyId, e.getMessage());
        }
    }

    private AutoCriteriaCheckResponse doCheck(UUID mapDifficultyId) {
        MapDifficulty diff = mapDifficultyRepository.findById(mapDifficultyId)
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", mapDifficultyId));

        String hash = diff.getMap().getSongHash();
        String difficultyStr = diff.getDifficulty().getDbValue();
        String categoryCode = diff.getCategory().getCode();
        String sidecarCategory = categoryCode.endsWith("_acc")
                ? categoryCode.substring(0, categoryCode.length() - 4)
                : categoryCode;

        if (hash == null || hash.isBlank()) {
            return persistAndReturn(diff, AutoCriteriaStatus.UNAVAILABLE, List.of());
        }

        byte[] zip;
        try {
            zip = beatSaverClient.downloadMapZip(hash).orElse(null);
        } catch (Exception e) {
            log.warn("Zip download failed for hash {}: {}", hash, e.getMessage());
            return persistAndReturn(diff, AutoCriteriaStatus.UNAVAILABLE, List.of());
        }
        if (zip == null) {
            return persistAndReturn(diff, AutoCriteriaStatus.UNAVAILABLE, List.of());
        }

        CriteriaCheckerClient.CheckResult result;
        try {
            result = criteriaCheckerClient.check(zip, difficultyStr, sidecarCategory);
        } catch (Exception e) {
            log.warn("Criteria checker call failed for difficulty {}: {}", mapDifficultyId, e.getMessage());
            return persistAndReturn(diff, AutoCriteriaStatus.UNAVAILABLE, List.of());
        } finally {
            zip = null;
        }

        if (result == null || result.getStatus() == null) {
            return persistAndReturn(diff, AutoCriteriaStatus.UNAVAILABLE, List.of());
        }

        AutoCriteriaStatus status = switch (result.getStatus()) {
            case "passed" -> AutoCriteriaStatus.PASSED;
            case "failed" -> AutoCriteriaStatus.FAILED;
            default -> AutoCriteriaStatus.UNAVAILABLE;
        };
        List<String> failures = result.getFailures() != null ? result.getFailures() : List.of();
        return persistAndReturn(diff, status, failures);
    }

    private AutoCriteriaCheckResponse persistAndReturn(MapDifficulty diff, AutoCriteriaStatus status,
            List<String> failures) {
        diff.setAutoCriteriaStatus(status);
        mapDifficultyRepository.save(diff);
        return AutoCriteriaCheckResponse.builder()
                .status(status)
                .failures(failures)
                .build();
    }
}
