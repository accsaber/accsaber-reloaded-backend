package com.accsaber.backend.service.map;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.accsaber.backend.client.CriteriaCheckerClient;
import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.TooManyRequestsException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.map.CriteriaCheckRequest;
import com.accsaber.backend.model.dto.response.map.AutoCriteriaCheckResponse;
import com.accsaber.backend.model.entity.AutoCriteriaStatus;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoCriteriaService {

    private final MapDifficultyRepository mapDifficultyRepository;
    private final MapZipCache zipCache;
    private final CriteriaCheckerClient criteriaCheckerClient;
    private final CategoryRepository categoryRepository;
    private final CriteriaCheckRateLimitService rateLimitService;

    @Transactional
    public AutoCriteriaCheckResponse runCheck(UUID mapDifficultyId) {
        return doCheck(mapDifficultyId);
    }

    @Transactional
    public AutoCriteriaCheckResponse runCheckForImporter(UUID mapDifficultyId, Long playerId) {
        MapDifficulty diff = mapDifficultyRepository.findByIdAndActiveTrue(mapDifficultyId)
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", mapDifficultyId));
        if (!playerId.equals(diff.getImportedBy())) {
            throw new ForbiddenException("Only the player who suggested this difficulty can refresh its check");
        }
        if (diff.getStatus() == MapDifficultyStatus.RANKED || diff.getStatus() == MapDifficultyStatus.CAMPAIGN) {
            throw new ValidationException("The criteria check only runs on suggested, queued or qualified difficulties");
        }
        acquire(playerId);
        return doCheck(mapDifficultyId);
    }

    public List<AutoCriteriaCheckResponse> check(Long playerId, CriteriaCheckRequest request, MultipartFile file) {
        acquire(playerId);
        Map<UUID, String> categoryCodes = new HashMap<>();
        for (CriteriaCheckRequest.Check check : request.checks()) {
            categoryCodes.computeIfAbsent(check.categoryId(), id -> categoryRepository.requireActive(id).getCode());
        }
        byte[] zip = file != null ? uploaded(file) : cached(request.songHash());
        return request.checks().stream()
                .map(check -> evaluate(zip, check.difficulty().getDbValue(), categoryCodes.get(check.categoryId()))
                        .toBuilder().difficulty(check.difficulty()).categoryId(check.categoryId()).build())
                .toList();
    }

    private static byte[] uploaded(MultipartFile file) {
        byte[] zip;
        try {
            zip = file.getBytes();
        } catch (IOException e) {
            throw new ValidationException("Could not read the uploaded file");
        }
        if (zip.length < 4 || zip[0] != 'P' || zip[1] != 'K') {
            throw new ValidationException("Upload the map as a .zip file");
        }
        return zip;
    }

    private byte[] cached(String songHash) {
        if (songHash == null) {
            throw new ValidationException("Send a song hash or upload the map zip");
        }
        return zipCache.get(songHash).orElseThrow(
                () -> new ValidationException("This map is not on BeatSaver. Upload its zip instead"));
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

    private void acquire(Long playerId) {
        if (!rateLimitService.tryAcquire(playerId)) {
            throw new TooManyRequestsException("You are running criteria checks too quickly. Try again in a few minutes.");
        }
    }

    private AutoCriteriaCheckResponse doCheck(UUID mapDifficultyId) {
        MapDifficulty diff = mapDifficultyRepository.findById(mapDifficultyId)
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", mapDifficultyId));

        String hash = diff.getMap().getSongHash();
        if (hash == null || hash.isBlank()) {
            return persistAndReturn(diff, unavailable());
        }

        byte[] zip;
        try {
            zip = zipCache.get(hash).orElse(null);
        } catch (Exception e) {
            log.warn("Zip download failed for hash {}: {}", hash, e.getMessage());
            return persistAndReturn(diff, unavailable());
        }
        if (zip == null) {
            return persistAndReturn(diff, unavailable());
        }
        return persistAndReturn(diff,
                evaluate(zip, diff.getDifficulty().getDbValue(), diff.getCategory().getCode()));
    }

    private AutoCriteriaCheckResponse evaluate(byte[] zip, String difficulty, String categoryCode) {
        String sidecarCategory = categoryCode.endsWith("_acc")
                ? categoryCode.substring(0, categoryCode.length() - 4)
                : categoryCode;

        CriteriaCheckerClient.CheckResult result;
        try {
            result = criteriaCheckerClient.check(zip, difficulty, sidecarCategory);
        } catch (Exception e) {
            log.warn("Criteria checker call failed: {}", e.getMessage());
            return unavailable();
        }
        if (result == null || result.getStatus() == null) {
            return unavailable();
        }

        AutoCriteriaStatus status = switch (result.getStatus()) {
            case "passed" -> AutoCriteriaStatus.PASSED;
            case "failed" -> AutoCriteriaStatus.FAILED;
            default -> AutoCriteriaStatus.UNAVAILABLE;
        };
        return AutoCriteriaCheckResponse.builder()
                .status(status)
                .failures(result.getFailures() != null ? result.getFailures() : List.of())
                .build();
    }

    private static AutoCriteriaCheckResponse unavailable() {
        return AutoCriteriaCheckResponse.builder().status(AutoCriteriaStatus.UNAVAILABLE).failures(List.of()).build();
    }

    private AutoCriteriaCheckResponse persistAndReturn(MapDifficulty diff, AutoCriteriaCheckResponse outcome) {
        diff.setAutoCriteriaStatus(outcome.getStatus());
        mapDifficultyRepository.save(diff);
        return outcome;
    }
}
