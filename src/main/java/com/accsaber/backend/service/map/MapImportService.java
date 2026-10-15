package com.accsaber.backend.service.map;


import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.accsaber.backend.model.dto.response.map.ComplexityEstimateResponse;
import com.accsaber.backend.model.dto.response.map.MapDifficultyResponse;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyMetadata;
import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.map.MapRepository;
import com.accsaber.backend.service.map.NoteAccuracyComplexityRater.Rating;
import com.accsaber.backend.service.media.CdnSyncService;
import com.accsaber.backend.service.playlist.PlaylistService;
import com.accsaber.backend.service.infra.AfterCommit;
import com.accsaber.backend.util.Texts;

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
    private final NoteAccuracyComplexityRater complexityRater;
    private final ComplexityEstimateService complexityEstimateService;
    private final AutoCriteriaService autoCriteriaService;
    private final PlaylistService playlistService;
    private final CdnSyncService cdnSyncService;


    @Transactional
    public MapDifficultyResponse importByLeaderboardIds(ImportMapFromLeaderboardIdsRequest importRequest,
            UUID staffId, MapDifficultyStatus status) {
        String blId = importRequest.getBlLeaderboardId();
        String ssId = Texts.blankToNull(importRequest.getSsLeaderboardId());

        if (blId == null || blId.isBlank()) {
            throw new ValidationException("BeatLeader leaderboard ID is required");
        }

        BeatLeaderLeaderboardResponse blLeaderboard = fetchBlLeaderboard(blId);
        int maxScore = blLeaderboard.getDifficulty().getMaxScore();
        SongInfo song = resolveSong(blLeaderboard);

        CreateMapDifficultyRequest request = toCreateRequest(importRequest, song, ssId, maxScore);

        log.info("Importing map difficulty: {} ({}) - BL:{} SS:{}", song.songName(), importRequest.getDifficulty(),
                blId, ssId);
        MapDifficultyResponse response = mapService.importMapDifficulty(request, staffId, status);

        applyInitialComplexity(response.getId(), importRequest, song.songName());

        scheduleAutoCriteriaCheck(response.getId());
        scheduleMapCoverMirror(response.getMapId());

        return response;
    }

    private CreateMapDifficultyRequest toCreateRequest(ImportMapFromLeaderboardIdsRequest importRequest,
            SongInfo song, String ssId, int maxScore) {
        CreateMapDifficultyRequest request = new CreateMapDifficultyRequest();
        request.setSongName(song.songName());
        request.setSongSubName(song.songSubName());
        request.setSongAuthor(song.songAuthor());
        request.setSongHash(song.songHash());
        request.setMapAuthor(song.mapAuthor());
        request.setBeatsaverCode(song.beatsaverCode());
        request.setCoverUrl(song.coverUrl());
        request.setCategoryId(importRequest.getCategoryId());
        request.setDifficulty(importRequest.getDifficulty());
        request.setCharacteristic(importRequest.getCharacteristic());
        request.setSsLeaderboardId(ssId);
        request.setBlLeaderboardId(importRequest.getBlLeaderboardId());
        request.setMaxScore(maxScore);
        request.setMetadata(extractMetadata(song.beatSaverMap(), importRequest.getDifficulty(),
                importRequest.getCharacteristic()));
        request.setBatchId(importRequest.getBatchId());
        request.setRankedAt(importRequest.getRankedAt());
        return request;
    }

    private void applyInitialComplexity(UUID difficultyId, ImportMapFromLeaderboardIdsRequest importRequest,
            String songName) {
        Double complexity = importRequest.getComplexity();
        String reason = "Initial import";
        if (complexity == null) {
            complexity = complexityEstimateService.estimateFor(difficultyId).map(Rating::complexity).orElse(null);
            reason = "Complexity script " + complexityRater.version();
            if (complexity != null) {
                log.info("Complexity script priced {} ({}) at {}", songName, importRequest.getDifficulty(), complexity);
            } else {
                log.warn("Complexity script could not price {} ({})", songName, importRequest.getDifficulty());
            }
        }

        if (complexity != null) {
            MapDifficulty entity = mapDifficultyRepository.findById(difficultyId)
                    .orElseThrow(() -> new ValidationException("Map difficulty not found after creation"));
            complexityService.setComplexity(entity, complexity, reason, null);
        }
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
            return reviveCampaignImport(existingByBl.get(), playerId);
        }
        assertCanImportCampaignMap(playerId, ssId);

        BeatLeaderLeaderboardResponse blLeaderboard = fetchBlLeaderboard(blId);
        validateCampaignLeaderboard(blLeaderboard);
        Integer maxScore = blLeaderboard.getDifficulty().getMaxScore();
        Difficulty difficulty = parseDifficulty(blLeaderboard.getDifficulty().getDifficultyName());
        String characteristic = blLeaderboard.getDifficulty().getModeName();
        if (characteristic == null || characteristic.isBlank()) {
            throw new ValidationException("BeatLeader leaderboard has no characteristic");
        }

        SongInfo song = resolveSong(blLeaderboard);
        Map map = findOrCreateMap(song);

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
                .metadata(extractMetadata(song.beatSaverMap(), difficulty, characteristic))
                .status(MapDifficultyStatus.CAMPAIGN)
                .importedBy(playerId)
                .previousVersion(previousVersion)
                .active(true)
                .build());

        log.info("Player {} imported campaign map difficulty: {} ({}) - BL:{} SS:{}", playerId, song.songName(),
                difficulty, blId, ssId);

        scheduleMapCoverMirror(map.getId());

        return created;
    }

    private MapDifficulty reviveCampaignImport(MapDifficulty existing, Long playerId) {
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

    private void assertCanImportCampaignMap(Long playerId, String ssId) {
        if (ssId != null && mapDifficultyRepository.findBySsLeaderboardId(ssId).isPresent()) {
            throw new ConflictException(
                    "ScoreSaber leaderboard ID '" + ssId + "' is already used by another difficulty");
        }

        if (playerId != null && mapDifficultyRepository.countByImportedByAndStatusAndActiveTrue(playerId,
                MapDifficultyStatus.CAMPAIGN) >= MAX_CAMPAIGN_IMPORTS_PER_PLAYER) {
            throw new ValidationException("You have reached the maximum of "
                    + MAX_CAMPAIGN_IMPORTS_PER_PLAYER + " imported campaign maps");
        }
    }

    private static void validateCampaignLeaderboard(BeatLeaderLeaderboardResponse blLeaderboard) {
        if (blLeaderboard.getSong() == null || blLeaderboard.getSong().getHash() == null
                || blLeaderboard.getDifficulty() == null) {
            throw new ValidationException("BeatLeader leaderboard is missing song or difficulty data");
        }
        Integer maxScore = blLeaderboard.getDifficulty().getMaxScore();
        if (maxScore == null || maxScore <= 0) {
            throw new ValidationException("BeatLeader leaderboard has no valid max score");
        }
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
        String newSsId = Texts.blankToNull(request.getSsLeaderboardId());
        assertLeaderboardIdsFree(difficultyId, newBlId, newSsId);

        BeatLeaderLeaderboardResponse blLeaderboard = fetchBlLeaderboard(newBlId);
        int maxScore = blLeaderboard.getDifficulty().getMaxScore();
        SongInfo song = resolveSong(blLeaderboard);
        repointMap(difficulty, song);

        difficulty.setBlLeaderboardId(newBlId);
        if (newSsId != null) {
            difficulty.setSsLeaderboardId(newSsId);
        }
        difficulty.setMaxScore(maxScore);
        difficulty.setMetadata(extractMetadata(song.beatSaverMap(), difficulty.getDifficulty(),
                difficulty.getCharacteristic()));
        difficulty.setLastUpdatedBy(staffId);
        mapDifficultyRepository.save(difficulty);

        log.info("Refreshed map difficulty {}: BL={} SS={} hash={}", difficultyId, newBlId,
                difficulty.getSsLeaderboardId(), song.songHash());

        playlistService.evictAllUnrankedPlaylists();
        scheduleAutoCriteriaCheck(difficultyId);
        scheduleMapCoverMirror(difficulty.getMap().getId());

        return mapService.getDifficultyResponse(difficultyId);
    }

    private void assertLeaderboardIdsFree(UUID difficultyId, String blId, String ssId) {
        mapDifficultyRepository.findByBlLeaderboardId(blId)
                .filter(existing -> !existing.getId().equals(difficultyId))
                .ifPresent(existing -> {
                    throw new ConflictException(String.format(
                            "BeatLeader leaderboard ID '%s' is already used by another difficulty (ID: %s)",
                            blId, existing.getId()));
                });
        if (ssId != null) {
            mapDifficultyRepository.findBySsLeaderboardId(ssId)
                    .filter(existing -> !existing.getId().equals(difficultyId))
                    .ifPresent(existing -> {
                        throw new ConflictException(String.format(
                                "ScoreSaber leaderboard ID '%s' is already used by another difficulty (ID: %s)",
                                ssId, existing.getId()));
                    });
        }
    }

    private void repointMap(MapDifficulty difficulty, SongInfo song) {
        Map currentMap = difficulty.getMap();
        if (currentMap.getSongHash().equalsIgnoreCase(song.songHash())) {
            currentMap.setSongName(song.songName());
            currentMap.setSongSubName(song.songSubName());
            currentMap.setSongAuthor(song.songAuthor());
            currentMap.setMapAuthor(song.mapAuthor());
            currentMap.setBeatsaverCode(song.beatsaverCode());
            currentMap.setCoverUrl(song.coverUrl());
            mapRepository.save(currentMap);
        } else {
            difficulty.setMap(findOrCreateMap(song));
        }
    }

    private BeatLeaderLeaderboardResponse fetchBlLeaderboard(String blId) {
        return beatLeaderClient.getLeaderboard(blId)
                .orElseThrow(() -> new ValidationException(
                        "BeatLeader leaderboard not found for ID: " + blId));
    }

    private SongInfo resolveSong(BeatLeaderLeaderboardResponse blLeaderboard) {
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
        return new SongInfo(songHash, songName, songSubName, songAuthor, mapAuthor, beatsaverCode, coverUrl,
                beatSaverMap);
    }

    private Map findOrCreateMap(SongInfo song) {
        return mapRepository.findBySongHashAndActiveTrue(song.songHash())
                .orElseGet(() -> mapRepository.save(Map.builder()
                        .songName(song.songName())
                        .songSubName(song.songSubName())
                        .songAuthor(song.songAuthor())
                        .songHash(song.songHash())
                        .mapAuthor(song.mapAuthor())
                        .beatsaverCode(song.beatsaverCode())
                        .coverUrl(song.coverUrl())
                        .active(true)
                        .build()));
    }

    private record SongInfo(String songHash, String songName, String songSubName, String songAuthor,
            String mapAuthor, String beatsaverCode, String coverUrl, BeatSaverMapResponse beatSaverMap) {
    }

    private void scheduleMapCoverMirror(UUID mapId) {
        if (mapId == null) return;
        AfterCommit.run(() -> cdnSyncService.mirrorMapCoverAsync(mapId));
    }

    private void scheduleAutoCriteriaCheck(UUID difficultyId) {
        AfterCommit.run(() -> {
            try {
                autoCriteriaService.runCheckAsync(difficultyId);
            } catch (Exception e) {
                log.warn("Failed to schedule auto criteria check for difficulty {}: {}", difficultyId, e.getMessage());
            }
        });
    }

    public ComplexityEstimateResponse estimateForDifficulty(String songHash, Difficulty difficulty,
            String characteristic) {
        var map = mapRepository.findBySongHashAndActiveTrue(songHash.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("Map", songHash));
        MapDifficulty entity = mapDifficultyRepository
                .findByMapIdAndDifficultyAndCharacteristicAndActiveTrue(map.getId(), difficulty, characteristic)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "MapDifficulty", songHash + "/" + difficulty + "/" + characteristic));
        return ComplexityEstimateResponse.builder()
                .complexity(complexityEstimateService.estimateFor(entity.getId()).map(Rating::complexity).orElse(null))
                .version(complexityRater.version())
                .build();
    }

    private static MapDifficultyMetadata extractMetadata(BeatSaverMapResponse beatSaverMap, Difficulty difficulty,
            String characteristic) {
        if (beatSaverMap == null || beatSaverMap.getVersions() == null || difficulty == null
                || characteristic == null) {
            return null;
        }
        String difficultyValue = difficulty.getDbValue();
        BeatSaverMapResponse.Diff diff = beatSaverMap.getVersions().stream()
                .filter(version -> version.getDiffs() != null)
                .flatMap(version -> version.getDiffs().stream())
                .filter(d -> difficultyValue.equalsIgnoreCase(d.getDifficulty())
                        && characteristic.equalsIgnoreCase(d.getCharacteristic()))
                .findFirst()
                .orElse(null);
        if (diff == null) {
            return null;
        }
        BeatSaverMapResponse.Metadata songMetadata = beatSaverMap.getMetadata();
        return MapDifficultyMetadata.builder()
                .bpm(songMetadata != null ? songMetadata.getBpm() : null)
                .duration(songMetadata != null ? songMetadata.getDuration() : null)
                .notes(diff.getNotes())
                .bombs(diff.getBombs())
                .walls(diff.getObstacles())
                .build();
    }

    @Async("backfillExecutor")
    public void backfillMetadata() {
        List<MapDifficulty> pending = mapDifficultyRepository.findActiveMissingMetadata();
        var byHash = pending.stream()
                .filter(d -> d.getMap() != null && d.getMap().getSongHash() != null)
                .collect(Collectors.groupingBy(d -> d.getMap().getSongHash()));
        log.info("Map metadata backfill starting: {} difficulties across {} maps", pending.size(), byHash.size());

        int updated = 0;
        for (var entry : byHash.entrySet()) {
            BeatSaverMapResponse beatSaverMap = beatSaverClient.getMapByHash(entry.getKey()).orElse(null);
            if (beatSaverMap == null) {
                continue;
            }
            List<MapDifficulty> toSave = new ArrayList<>();
            for (MapDifficulty difficulty : entry.getValue()) {
                MapDifficultyMetadata metadata = extractMetadata(beatSaverMap, difficulty.getDifficulty(),
                        difficulty.getCharacteristic());
                if (metadata != null) {
                    difficulty.setMetadata(metadata);
                    toSave.add(difficulty);
                }
            }
            if (!toSave.isEmpty()) {
                mapDifficultyRepository.saveAll(toSave);
                updated += toSave.size();
            }
        }
        log.info("Chart stats backfill complete: {} difficulties updated", updated);
    }

}
