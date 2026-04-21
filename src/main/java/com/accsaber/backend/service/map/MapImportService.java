package com.accsaber.backend.service.map;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.accsaber.backend.client.BeatLeaderClient;
import com.accsaber.backend.client.BeatSaverClient;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderLeaderboardResponse;
import com.accsaber.backend.model.dto.platform.beatsaver.BeatSaverMapResponse;
import com.accsaber.backend.model.dto.request.map.CreateMapDifficultyRequest;
import com.accsaber.backend.model.dto.request.map.ImportMapFromLeaderboardIdsRequest;
import com.accsaber.backend.model.dto.response.map.AiComplexityResponse;
import com.accsaber.backend.model.dto.response.map.MapDifficultyResponse;
import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.CurveRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.map.MapRepository;
import com.accsaber.backend.service.score.APCalculationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MapImportService {

    private final BeatLeaderClient beatLeaderClient;
    private final BeatSaverClient beatSaverClient;
    private final MapService mapService;
    private final MapDifficultyComplexityService complexityService;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final MapRepository mapRepository;
    private final CurveRepository curveRepository;
    private final APCalculationService apCalculationService;
    private final AutoCriteriaService autoCriteriaService;

    private static final String COMPLEXITY_CURVE_NAME = "AI Complexity Curve";

    @Value("${accsaber.complexity-estimate.ap-target}")
    private double apTarget;

    @Value("${accsaber.complexity-estimate.accuracy-shift}")
    private double accuracyShift;

    @Value("${accsaber.complexity-estimate.transform-offset}")
    private double transformOffset;

    @Value("${accsaber.complexity-estimate.transform-scale}")
    private double transformScale;

    @Value("${accsaber.complexity-estimate.transform-base}")
    private double transformBase;

    @Transactional
    public MapDifficultyResponse importByLeaderboardIds(ImportMapFromLeaderboardIdsRequest importRequest,
            UUID staffId, MapDifficultyStatus status) {
        String blId = importRequest.getBlLeaderboardId();
        String ssId = importRequest.getSsLeaderboardId();

        if (ssId == null || ssId.isBlank()) {
            throw new ValidationException("ScoreSaber leaderboard ID is required");
        }
        if (blId == null || blId.isBlank()) {
            throw new ValidationException("BeatLeader leaderboard ID is required");
        }

        BeatLeaderLeaderboardResponse blLeaderboard = beatLeaderClient.getLeaderboard(blId)
                .orElseThrow(() -> new ValidationException(
                        "BeatLeader leaderboard not found for ID: " + blId));

        String songHash = blLeaderboard.getSong().getHash();
        int maxScore = blLeaderboard.getDifficulty().getMaxScore();

        String songName = blLeaderboard.getSong().getName();
        String songSubName = blLeaderboard.getSong().getSubName();
        String songAuthor = blLeaderboard.getSong().getAuthor();
        String mapAuthor = blLeaderboard.getSong().getMapper();
        String beatsaverCode = null;
        String coverUrl = null;

        BeatSaverMapResponse beatSaverMap = beatSaverClient.getMapByHash(songHash).orElse(null);
        if (beatSaverMap != null) {
            beatsaverCode = beatSaverMap.getId();
            if (beatSaverMap.getMetadata() != null) {
                if (beatSaverMap.getMetadata().getSongName() != null) {
                    songName = beatSaverMap.getMetadata().getSongName();
                }
                if (beatSaverMap.getMetadata().getSongSubName() != null) {
                    songSubName = beatSaverMap.getMetadata().getSongSubName();
                }
                if (beatSaverMap.getMetadata().getSongAuthorName() != null) {
                    songAuthor = beatSaverMap.getMetadata().getSongAuthorName();
                }
                if (beatSaverMap.getMetadata().getLevelAuthorName() != null) {
                    mapAuthor = beatSaverMap.getMetadata().getLevelAuthorName();
                }
            }
            if (beatSaverMap.getVersions() != null && !beatSaverMap.getVersions().isEmpty()) {
                coverUrl = beatSaverMap.getVersions().get(0).getCoverURL();
            }
        }

        CreateMapDifficultyRequest request = new CreateMapDifficultyRequest();
        request.setSongName(songName);
        request.setSongSubName(songSubName);
        request.setSongAuthor(songAuthor);
        request.setSongHash(songHash);
        request.setMapAuthor(mapAuthor);
        request.setBeatsaverCode(beatsaverCode);
        request.setCoverUrl(coverUrl);
        request.setCategoryId(importRequest.getCategoryId());
        request.setDifficulty(importRequest.getDifficulty());
        request.setCharacteristic(importRequest.getCharacteristic());
        request.setSsLeaderboardId(ssId);
        request.setBlLeaderboardId(blId);
        request.setMaxScore(maxScore);
        request.setBatchId(importRequest.getBatchId());
        request.setRankedAt(importRequest.getRankedAt());

        log.info("Importing map difficulty: {} ({}) - BL:{} SS:{}", songName, importRequest.getDifficulty(), blId,
                ssId);
        MapDifficultyResponse response = mapService.importMapDifficulty(request, staffId, status);

        BigDecimal complexity = importRequest.getComplexity();
        if (complexity == null) {
            complexity = estimateAiComplexity(songHash, importRequest.getDifficulty(),
                    importRequest.getCharacteristic());
            if (complexity != null) {
                log.info("AI complexity estimate for {} ({}): {}", songName, importRequest.getDifficulty(), complexity);
            } else {
                log.warn("AI complexity unavailable for {} ({})", songName, importRequest.getDifficulty());
            }
        }

        if (complexity != null) {
            MapDifficulty entity = mapDifficultyRepository.findById(response.getId())
                    .orElseThrow(() -> new ValidationException("Map difficulty not found after creation"));
            String reason = importRequest.getComplexity() != null ? "Initial import" : "AI complexity estimate";
            complexityService.setComplexity(entity, complexity, reason, null);
        }

        scheduleAutoCriteriaCheck(response.getId());

        return response;
    }

    private void scheduleAutoCriteriaCheck(UUID difficultyId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        autoCriteriaService.runCheckAsync(difficultyId);
                    } catch (Exception e) {
                        log.warn("Failed to schedule auto criteria check for difficulty {}: {}", difficultyId, e.getMessage());
                    }
                }
            });
        } else {
            try {
                autoCriteriaService.runCheckAsync(difficultyId);
            } catch (Exception e) {
                log.warn("Failed to schedule auto criteria check for difficulty {}: {}", difficultyId, e.getMessage());
            }
        }
    }

    public AiComplexityResponse estimateForRankedDifficulty(String songHash, Difficulty difficulty,
            String characteristic) {
        var map = mapRepository.findBySongHashAndActiveTrue(songHash.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("Map", songHash));
        MapDifficulty entity = mapDifficultyRepository
                .findByMapIdAndDifficultyAndCharacteristicAndActiveTrue(map.getId(), difficulty, characteristic)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "MapDifficulty", songHash + "/" + difficulty + "/" + characteristic));
        if (entity.getStatus() != MapDifficultyStatus.RANKED) {
            throw new ValidationException("AI complexity is only available for RANKED difficulties");
        }
        return AiComplexityResponse.builder()
                .complexity(estimateAiComplexity(songHash, difficulty, characteristic))
                .build();
    }

    public BigDecimal estimateAiComplexity(String songHash, Difficulty difficulty, String characteristic) {
        BigDecimal aiAcc = beatLeaderClient
                .getAiAccuracy(songHash, characteristic, difficulty.getNumericValue())
                .orElse(null);
        if (aiAcc == null)
            return null;

        Curve complexityCurve = curveRepository.findByNameAndActiveTrue(COMPLEXITY_CURVE_NAME).orElse(null);
        if (complexityCurve == null)
            return null;

        BigDecimal shiftedAccuracy = aiAcc.add(BigDecimal.valueOf(accuracyShift));
        BigDecimal rawMultiplier = apCalculationService.interpolate(complexityCurve, shiftedAccuracy);

        double transformedMultiplier = transformMultiplier(rawMultiplier.doubleValue(),
                transformOffset, transformScale, transformBase);
        if (transformedMultiplier <= 0)
            return null;

        double scale = complexityCurve.getScale().doubleValue();
        double shift = complexityCurve.getShift().doubleValue();
        double complexity = apTarget / (transformedMultiplier * scale) + shift;

        return BigDecimal.valueOf(complexity).setScale(1, RoundingMode.HALF_UP);
    }

    private static double transformMultiplier(double mult, double offset, double scale, double base) {
        if (mult == 0)
            return 0;
        return (mult - offset) / (1 - offset) * scale + base;
    }
}
