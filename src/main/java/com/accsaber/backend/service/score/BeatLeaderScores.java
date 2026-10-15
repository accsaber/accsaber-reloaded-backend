package com.accsaber.backend.service.score;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.accsaber.backend.client.BeatLeaderClient;
import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderScoreResponse;
import com.accsaber.backend.model.dto.request.score.SubmitScoreRequest;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.score.ScoreModifierLink;
import com.accsaber.backend.repository.ModifierRepository;
import com.accsaber.backend.repository.score.ScoreModifierLinkRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.service.infra.ModifierCacheService;
import com.accsaber.backend.util.HmdMapper;
import com.accsaber.backend.util.PlatformScoreMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
class BeatLeaderScores implements PlatformScores<BeatLeaderScoreResponse> {

    private final BeatLeaderClient beatLeaderClient;
    private final ScoreRepository scoreRepository;
    private final ScoreModifierLinkRepository scoreModifierLinkRepository;
    private final ModifierRepository modifierRepository;
    private final ModifierCacheService modifierCacheService;

    List<BeatLeaderScoreResponse> recentScores(String leaderboardId, long sinceEpoch) {
        return beatLeaderClient.getRecentScores(leaderboardId, sinceEpoch);
    }

    @Override
    public String label() {
        return "BL";
    }

    @Override
    public String leaderboardId(MapDifficulty difficulty) {
        return difficulty.getBlLeaderboardId();
    }

    @Override
    public Optional<BeatLeaderScoreResponse> fetchPlayerScore(Long userId, MapDifficulty difficulty) {
        return beatLeaderClient.getPlayerScoreOnLeaderboard(
                String.valueOf(userId),
                difficulty.getMap() != null ? difficulty.getMap().getSongHash() : null,
                difficulty.getDifficulty() != null ? difficulty.getDifficulty().getDbValue() : null,
                difficulty.getCharacteristic());
    }

    @Override
    public IntFunction<ScorePage<BeatLeaderScoreResponse>> rankedPages(MapDifficulty difficulty,
            String leaderboardId) {
        return page -> {
            List<BeatLeaderScoreResponse> scores;
            try {
                scores = beatLeaderClient.getLeaderboardScores(leaderboardId, page, PAGE_SIZE);
            } catch (Exception e) {
                log.error("BL backfill failed on page {} for difficulty {}: {}",
                        page, difficulty.getId(), e.getMessage());
                throw new RuntimeException("BL backfill aborted on page " + page, e);
            }
            return new ScorePage<>(scores, scores.size() < PAGE_SIZE);
        };
    }

    @Override
    public ScorePage<BeatLeaderScoreResponse> recentPage(MapDifficulty difficulty, int page) {
        List<BeatLeaderScoreResponse> scores = beatLeaderClient.getLeaderboardScoresSortedByDate(
                difficulty.getBlLeaderboardId(), page, PAGE_SIZE);
        return new ScorePage<>(scores, scores.size() < PAGE_SIZE);
    }

    @Override
    public Cut cutoff(BeatLeaderScoreResponse score, long sinceEpoch) {
        return score.getTimepost() != null && score.getTimepost() <= sinceEpoch ? Cut.STOP : Cut.KEEP;
    }

    @Override
    public Integer baseScore(BeatLeaderScoreResponse score) {
        return score.getBaseScore();
    }

    @Override
    public boolean banned(BeatLeaderScoreResponse score) {
        return PlatformScoreMapper.hasBannedModifier(score.getModifiers());
    }

    @Override
    public boolean hasPlayerId(BeatLeaderScoreResponse score) {
        return score.getPlayer() != null && score.getPlayer().getId() != null;
    }

    @Override
    public Long playerId(BeatLeaderScoreResponse score) {
        return Long.parseLong(score.getPlayer().getId());
    }

    @Override
    public void stubPlayer(BeatLeaderScoreResponse score, Long userId) {
        BeatLeaderScoreResponse.Player player = new BeatLeaderScoreResponse.Player();
        player.setId(String.valueOf(userId));
        score.setPlayer(player);
    }

    @Override
    public Long scoreId(BeatLeaderScoreResponse score) {
        return score.getId();
    }

    @Override
    public SubmitScoreRequest toRequest(BeatLeaderScoreResponse score, MapDifficulty difficulty, Long userId,
            Map<String, UUID> modifiers) {
        return PlatformScoreMapper.fromBeatLeader(score, difficulty.getId(), userId, modifiers);
    }

    @Override
    public void mergeIntoExisting(Score existing, BeatLeaderScoreResponse score, ImportTarget target,
            Long userId) {
        if (existing.getBlScoreId() != null) {
            reconcileModifierLinks(existing, score.getModifiers());
        } else {
            enrichWithBeatLeaderData(existing, score);
        }
    }

    private void enrichWithBeatLeaderData(Score score, BeatLeaderScoreResponse blScore) {
        score.setBlScoreId(blScore.getId());
        score.setWallHits(blScore.getWallsHit());
        score.setBombHits(blScore.getBombCuts());
        score.setPauses(blScore.getPauses());
        score.setStreak115(blScore.getMaxStreak());
        score.setPlayCount(blScore.getPlayCount() != null && blScore.getPlayCount() > 0
                ? blScore.getPlayCount()
                : null);
        if (score.getMaxCombo() == null && blScore.getMaxCombo() != null && blScore.getMaxCombo() > 0) {
            score.setMaxCombo(blScore.getMaxCombo());
        }
        if (score.getBadCuts() == null && blScore.getBadCuts() != null) {
            score.setBadCuts(blScore.getBadCuts());
        }
        if (score.getMisses() == null && blScore.getMissedNotes() != null) {
            score.setMisses(blScore.getMissedNotes());
        }
        String blHmd = HmdMapper.fromBeatLeaderId(blScore.getHmd());
        if (blHmd != null) {
            score.setHmd(blHmd);
        }
        if (score.getTimeSet() == null && blScore.getTimepost() != null && blScore.getTimepost() > 0) {
            score.setTimeSet(Instant.ofEpochSecond(blScore.getTimepost()));
        }
        scoreRepository.save(score);
        reconcileModifierLinks(score, blScore.getModifiers());
        log.debug("Enriched score {} with BL data (blScoreId={})", score.getId(), blScore.getId());
    }

    private void reconcileModifierLinks(Score score, String modifiersStr) {
        if (modifiersStr == null || modifiersStr.isBlank())
            return;
        Set<UUID> existingModifierIds = scoreModifierLinkRepository.findByScore_Id(score.getId()).stream()
                .map(l -> l.getModifier().getId())
                .collect(Collectors.toSet());
        Map<String, UUID> codeToId = modifierCacheService.getModifierCodeToId();
        List<ScoreModifierLink> toAdd = new ArrayList<>();
        for (String code : modifiersStr.split(",")) {
            String trimmed = code.trim();
            if (trimmed.isEmpty())
                continue;
            UUID modifierId = codeToId.get(trimmed);
            if (modifierId == null || existingModifierIds.contains(modifierId))
                continue;
            toAdd.add(ScoreModifierLink.builder()
                    .score(score)
                    .modifier(modifierRepository.getReferenceById(modifierId))
                    .build());
        }
        if (!toAdd.isEmpty()) {
            scoreModifierLinkRepository.saveAll(toAdd);
            log.debug("Added {} missing modifier links to score {}", toAdd.size(), score.getId());
        }
    }
}
