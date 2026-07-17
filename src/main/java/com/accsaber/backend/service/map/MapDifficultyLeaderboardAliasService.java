package com.accsaber.backend.service.map;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.client.BeatLeaderClient;
import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderLeaderboardResponse;
import com.accsaber.backend.model.dto.request.map.LinkLeaderboardAliasRequest;
import com.accsaber.backend.model.dto.response.map.LeaderboardAliasResponse;
import com.accsaber.backend.model.entity.map.LeaderboardPlatform;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyLeaderboardAlias;
import com.accsaber.backend.repository.map.MapDifficultyLeaderboardAliasRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.service.score.ScoreImportService;
import com.accsaber.backend.service.score.ScoreIngestionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MapDifficultyLeaderboardAliasService {

    private final MapDifficultyLeaderboardAliasRepository aliasRepository;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final BeatLeaderClient beatLeaderClient;
    private final ScoreIngestionService scoreIngestionService;
    private final ScoreImportService scoreImportService;

    @Transactional(readOnly = true)
    public List<LeaderboardAliasResponse> list(UUID difficultyId) {
        return aliasRepository.findByMapDifficulty_Id(difficultyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<LeaderboardAliasResponse> linkAlias(UUID difficultyId, LinkLeaderboardAliasRequest request,
            UUID staffId) {
        MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrue(difficultyId)
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", difficultyId.toString()));
        if (difficulty.getMaxScore() == null) {
            throw new ValidationException(
                    "Difficulty has no known max score; cannot verify chart identity for aliasing");
        }

        String blId = request.getBlLeaderboardId().trim();
        String ssId = request.getSsLeaderboardId() != null && !request.getSsLeaderboardId().isBlank()
                ? request.getSsLeaderboardId().trim()
                : null;

        assertChartMatches(difficulty, blId);
        assertLeaderboardFree("BeatLeader", mapDifficultyRepository.findByBlLeaderboardId(blId), blId);
        if (ssId != null) {
            assertLeaderboardFree("ScoreSaber", mapDifficultyRepository.findBySsLeaderboardId(ssId), ssId);
        }

        aliasRepository.save(MapDifficultyLeaderboardAlias.builder()
                .mapDifficulty(difficulty)
                .blLeaderboardId(blId)
                .ssLeaderboardId(ssId)
                .createdBy(staffId)
                .build());
        log.info("Linked leaderboard alias to difficulty {} (BL={}, SS={}) by staff {}",
                difficultyId, blId, ssId, staffId);

        scoreIngestionService.refreshRankedLeaderboardIds();
        scoreImportService.backfillLeaderboardAsync(difficultyId, LeaderboardPlatform.BEATLEADER, blId);
        if (ssId != null) {
            scoreImportService.backfillLeaderboardAsync(difficultyId, LeaderboardPlatform.SCORESABER, ssId);
        }
        return list(difficultyId);
    }

    @Transactional
    public void unlinkAlias(UUID difficultyId, UUID aliasId) {
        MapDifficultyLeaderboardAlias alias = aliasRepository.findById(aliasId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaderboardAlias", aliasId.toString()));
        if (!alias.getMapDifficulty().getId().equals(difficultyId)) {
            throw new ValidationException("Alias does not belong to the given difficulty");
        }
        aliasRepository.delete(alias);
        log.info("Unlinked leaderboard alias {} (BL={}, SS={}) from difficulty {}",
                aliasId, alias.getBlLeaderboardId(), alias.getSsLeaderboardId(), difficultyId);
        scoreIngestionService.refreshRankedLeaderboardIds();
    }

    private void assertChartMatches(MapDifficulty difficulty, String blLeaderboardId) {
        BeatLeaderLeaderboardResponse leaderboard = beatLeaderClient.getLeaderboard(blLeaderboardId)
                .orElseThrow(() -> new ValidationException(
                        "BeatLeader leaderboard not found for ID: " + blLeaderboardId));
        Integer aliasMaxScore = leaderboard.getDifficulty() != null
                ? leaderboard.getDifficulty().getMaxScore()
                : null;
        if (aliasMaxScore == null || aliasMaxScore <= 0) {
            throw new ValidationException("BeatLeader leaderboard has no valid max score");
        }
        if (!aliasMaxScore.equals(difficulty.getMaxScore())) {
            throw new ValidationException(String.format(
                    "Chart mismatch: alias max score %d does not match difficulty max score %d. "
                            + "Only note-identical charts can share a leaderboard.",
                    aliasMaxScore, difficulty.getMaxScore()));
        }
    }

    private void assertLeaderboardFree(String platform, java.util.Optional<MapDifficulty> existing,
            String leaderboardId) {
        existing.ifPresent(d -> {
            throw new ConflictException(String.format(
                    "%s leaderboard ID '%s' is already used by difficulty %s",
                    platform, leaderboardId, d.getId()));
        });
    }

    private LeaderboardAliasResponse toResponse(MapDifficultyLeaderboardAlias alias) {
        return LeaderboardAliasResponse.builder()
                .id(alias.getId())
                .blLeaderboardId(alias.getBlLeaderboardId())
                .ssLeaderboardId(alias.getSsLeaderboardId())
                .linkedAt(alias.getCreatedAt())
                .build();
    }
}
