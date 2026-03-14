package com.accsaber.backend.service.map;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.accsaber.backend.client.BeatLeaderClient;
import com.accsaber.backend.client.BeatSaverClient;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderLeaderboardResponse;
import com.accsaber.backend.model.dto.platform.beatsaver.BeatSaverMapResponse;
import com.accsaber.backend.model.dto.request.map.CreateMapDifficultyRequest;
import com.accsaber.backend.model.dto.request.map.ImportMapFromLeaderboardIdsRequest;
import com.accsaber.backend.model.dto.response.map.MapDifficultyResponse;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MapImportService {

    private final BeatLeaderClient beatLeaderClient;
    private final BeatSaverClient beatSaverClient;
    private final MapService mapService;

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
        return mapService.importMapDifficulty(request, staffId, status);
    }
}
