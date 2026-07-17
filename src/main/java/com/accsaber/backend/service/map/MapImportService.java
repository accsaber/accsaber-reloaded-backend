package com.accsaber.backend.service.map;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.accsaber.backend.client.BeatLeaderClient;
import com.accsaber.backend.client.BeatSaverClient;
import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderLeaderboardResponse;
import com.accsaber.backend.model.dto.platform.beatsaver.BeatSaverMapResponse;
import com.accsaber.backend.model.dto.request.map.CreateMapDifficultyRequest;
import com.accsaber.backend.model.dto.request.map.ImportCampaignMapRequest;
import com.accsaber.backend.model.dto.request.map.ImportMapFromLeaderboardIdsRequest;
import com.accsaber.backend.model.dto.request.map.RefreshMapDifficultyRequest;
import com.accsaber.backend.model.dto.response.map.AiComplexityResponse;
import com.accsaber.backend.model.dto.response.map.MapDifficultyResponse;
import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.CurveRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.map.MapRepository;
import com.accsaber.backend.service.media.CdnSyncService;
import com.accsaber.backend.service.playlist.PlaylistService;
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
    private final PlaylistService playlistService;
    private final CdnSyncService cdnSyncService;

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
        scheduleMapCoverMirror(response.getMapId());

        return response;
    }

    private static final int MAX_CAMPAIGN_IMPORTS_PER_PLAYER = 100;

    @Transactional
    public MapDifficultyResponse importCampaignMap(Long playerId, ImportCampaignMapRequest importRequest) {
        return mapService.getDifficultyResponse(resolveCampaignMap(playerId, importRequest, null).getId());
    }

    @Transactional
    public MapDifficulty resolveCampaignMap(Long playerId, ImportCampaignMapRequest importRequest,
            UUID previousVersionId) {
        String blId = importRequest.getBlLeaderboardId().trim();
        String ssId = importRequest.getSsLeaderboardId() != null && !importRequest.getSsLeaderboardId().isBlank()
                ? importRequest.getSsLeaderboardId().trim()
                : null;

        Optional<MapDifficulty> existingByBl = mapDifficultyRepository.findByBlLeaderboardId(blId);
        if (existingByBl.isPresent()) {
            MapDifficulty existing = existingByBl.get();
            if (existing.isActive()) {
                return existing;
            }
            if (existing.getStatus() != MapDifficultyStatus.CAMPAIGN
                    || mapDifficultyRepository.findByMapIdAndDifficultyAndCharacteristicAndActiveTrue(
                            existing.getMap().getId(), existing.getDifficulty(),
                            existing.getCharacteristic()).isPresent()) {
                throw new ConflictException(
                        "This leaderboard belongs to a difficulty that was removed from the system");
            }
            existing.setActive(true);
            if (existing.getImportedBy() == null) {
                existing.setImportedBy(playerId);
            }
            return mapDifficultyRepository.save(existing);
        }
        if (ssId != null && mapDifficultyRepository.findBySsLeaderboardId(ssId).isPresent()) {
            throw new ConflictException(
                    "ScoreSaber leaderboard ID '" + ssId + "' is already used by another difficulty");
        }

        if (playerId != null && mapDifficultyRepository.countByImportedByAndStatusAndActiveTrue(playerId,
                MapDifficultyStatus.CAMPAIGN) >= MAX_CAMPAIGN_IMPORTS_PER_PLAYER) {
            throw new ValidationException("You have reached the maximum of "
                    + MAX_CAMPAIGN_IMPORTS_PER_PLAYER + " imported campaign maps");
        }

        BeatLeaderLeaderboardResponse blLeaderboard = beatLeaderClient.getLeaderboard(blId)
                .orElseThrow(() -> new ValidationException(
                        "BeatLeader leaderboard not found for ID: " + blId));
        if (blLeaderboard.getSong() == null || blLeaderboard.getSong().getHash() == null
                || blLeaderboard.getDifficulty() == null) {
            throw new ValidationException("BeatLeader leaderboard is missing song or difficulty data");
        }
        Integer maxScore = blLeaderboard.getDifficulty().getMaxScore();
        if (maxScore == null || maxScore <= 0) {
            throw new ValidationException("BeatLeader leaderboard has no valid max score");
        }
        Difficulty difficulty = parseDifficulty(blLeaderboard.getDifficulty().getDifficultyName());
        String characteristic = blLeaderboard.getDifficulty().getModeName();
        if (characteristic == null || characteristic.isBlank()) {
            throw new ValidationException("BeatLeader leaderboard has no characteristic");
        }

        String songHash = blLeaderboard.getSong().getHash();
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

        final String finalSongName = songName;
        final String finalSongSubName = songSubName;
        final String finalSongAuthor = songAuthor;
        final String finalMapAuthor = mapAuthor;
        final String finalBeatsaverCode = beatsaverCode;
        final String finalCoverUrl = coverUrl;
        Map map = mapRepository.findBySongHashAndActiveTrue(songHash)
                .orElseGet(() -> mapRepository.save(Map.builder()
                        .songName(finalSongName)
                        .songSubName(finalSongSubName)
                        .songAuthor(finalSongAuthor)
                        .songHash(songHash)
                        .mapAuthor(finalMapAuthor)
                        .beatsaverCode(finalBeatsaverCode)
                        .coverUrl(finalCoverUrl)
                        .active(true)
                        .build()));

        Optional<MapDifficulty> samePosition = mapDifficultyRepository
                .findByMapIdAndDifficultyAndCharacteristicAndActiveTrue(map.getId(), difficulty, characteristic);
        if (samePosition.isPresent()) {
            return samePosition.get();
        }

        MapDifficulty previousVersion = previousVersionId != null
                ? mapDifficultyRepository.findById(previousVersionId).orElse(null)
                : null;

        MapDifficulty created = mapDifficultyRepository.save(MapDifficulty.builder()
                .map(map)
                .difficulty(difficulty)
                .characteristic(characteristic)
                .ssLeaderboardId(ssId)
                .blLeaderboardId(blId)
                .maxScore(maxScore)
                .status(MapDifficultyStatus.CAMPAIGN)
                .importedBy(playerId)
                .previousVersion(previousVersion)
                .active(true)
                .build());

        log.info("Player {} imported campaign map difficulty: {} ({}) - BL:{} SS:{}", playerId, songName,
                difficulty, blId, ssId);

        scheduleMapCoverMirror(map.getId());

        return created;
    }

    private Difficulty parseDifficulty(String difficultyName) {
        if (difficultyName == null) {
            throw new ValidationException("BeatLeader leaderboard has no difficulty name");
        }
        try {
            return Difficulty.fromDbValue(difficultyName);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Unsupported difficulty: " + difficultyName);
        }
    }

    @Transactional
    public MapDifficultyResponse refreshMapDifficulty(UUID difficultyId, RefreshMapDifficultyRequest request,
            UUID staffId) {
        MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrue(difficultyId)
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", difficultyId));

        if (difficulty.getStatus() != MapDifficultyStatus.QUEUE
                && difficulty.getStatus() != MapDifficultyStatus.QUALIFIED) {
            throw new ValidationException(
                    "Refresh is only allowed on QUEUE or QUALIFIED difficulties");
        }

        String newBlId = request.getBlLeaderboardId();
        String newSsId = request.getSsLeaderboardId();

        mapDifficultyRepository.findByBlLeaderboardId(newBlId)
                .filter(existing -> !existing.getId().equals(difficultyId))
                .ifPresent(existing -> {
                    throw new ConflictException(String.format(
                            "BeatLeader leaderboard ID '%s' is already used by another difficulty (ID: %s)",
                            newBlId, existing.getId()));
                });
        mapDifficultyRepository.findBySsLeaderboardId(newSsId)
                .filter(existing -> !existing.getId().equals(difficultyId))
                .ifPresent(existing -> {
                    throw new ConflictException(String.format(
                            "ScoreSaber leaderboard ID '%s' is already used by another difficulty (ID: %s)",
                            newSsId, existing.getId()));
                });

        BeatLeaderLeaderboardResponse blLeaderboard = beatLeaderClient.getLeaderboard(newBlId)
                .orElseThrow(() -> new ValidationException(
                        "BeatLeader leaderboard not found for ID: " + newBlId));

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

        Map currentMap = difficulty.getMap();
        if (currentMap.getSongHash().equalsIgnoreCase(songHash)) {
            currentMap.setSongName(songName);
            currentMap.setSongSubName(songSubName);
            currentMap.setSongAuthor(songAuthor);
            currentMap.setMapAuthor(mapAuthor);
            currentMap.setBeatsaverCode(beatsaverCode);
            currentMap.setCoverUrl(coverUrl);
            mapRepository.save(currentMap);
        } else {
            final String newSongName = songName;
            final String newSongSubName = songSubName;
            final String newSongAuthor = songAuthor;
            final String newMapAuthor = mapAuthor;
            final String newBeatsaverCode = beatsaverCode;
            final String newCoverUrl = coverUrl;
            Map newMap = mapRepository.findBySongHashAndActiveTrue(songHash)
                    .orElseGet(() -> mapRepository.save(Map.builder()
                            .songName(newSongName)
                            .songSubName(newSongSubName)
                            .songAuthor(newSongAuthor)
                            .songHash(songHash)
                            .mapAuthor(newMapAuthor)
                            .beatsaverCode(newBeatsaverCode)
                            .coverUrl(newCoverUrl)
                            .active(true)
                            .build()));
            difficulty.setMap(newMap);
        }

        difficulty.setBlLeaderboardId(newBlId);
        difficulty.setSsLeaderboardId(newSsId);
        difficulty.setMaxScore(maxScore);
        difficulty.setLastUpdatedBy(staffId);
        mapDifficultyRepository.save(difficulty);

        log.info("Refreshed map difficulty {}: BL={} SS={} hash={}", difficultyId, newBlId, newSsId, songHash);

        playlistService.evictAllUnrankedPlaylists();
        scheduleAutoCriteriaCheck(difficultyId);
        scheduleMapCoverMirror(difficulty.getMap().getId());

        return mapService.getDifficultyResponse(difficultyId);
    }

    private void scheduleMapCoverMirror(UUID mapId) {
        if (mapId == null) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cdnSyncService.mirrorMapCoverAsync(mapId);
                }
            });
        } else {
            cdnSyncService.mirrorMapCoverAsync(mapId);
        }
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
