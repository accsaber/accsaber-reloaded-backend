package com.accsaber.backend.service.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.client.BeatLeaderClient;
import com.accsaber.backend.client.ScoreSaberClient;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderScoreResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoreResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoresPage;
import com.accsaber.backend.model.dto.response.map.LeaderboardPreviewResponse;
import com.accsaber.backend.model.dto.response.map.LeaderboardPreviewResponse.Row;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyComplexityEstimate;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.map.MapDifficultyComplexityEstimateRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.score.APCalculationService;
import com.accsaber.backend.util.PlatformScoreMapper;
import com.accsaber.backend.util.Rounding;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardPreviewService {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_ROWS = 500;

    private record Fetched(Long userId, String name, String avatarUrl, String country, String platform,
            double accuracy, String modifiers) {
    }

    private final MapDifficultyRepository mapDifficultyRepository;
    private final MapDifficultyComplexityService complexityService;
    private final MapDifficultyComplexityEstimateRepository estimateRepository;
    private final UserRepository userRepository;
    private final BeatLeaderClient beatLeaderClient;
    private final ScoreSaberClient scoreSaberClient;
    private final APCalculationService apCalculationService;

    @Transactional(readOnly = true)
    public LeaderboardPreviewResponse preview(UUID mapDifficultyId, int limit) {
        MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrueWithMapAndCategory(mapDifficultyId)
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", mapDifficultyId));
        if (difficulty.getCategory() == null || difficulty.getMaxScore() == null || difficulty.getMaxScore() <= 0) {
            throw new ValidationException("The difficulty needs a category and a max score before its board can be priced");
        }
        int wanted = Math.min(Math.max(limit, 1), MAX_ROWS);
        Optional<Double> current = complexityService.findActiveComplexity(difficulty.getId());
        Optional<MapDifficultyComplexityEstimate> estimate = current.isPresent() ? Optional.empty()
                : estimateRepository.findByMapDifficultyId(difficulty.getId());
        Double complexity = current.orElseGet(() -> estimate.map(MapDifficultyComplexityEstimate::getComplexity).orElse(null));
        Map<Long, Fetched> byUser = new LinkedHashMap<>();
        fetchBeatLeader(difficulty, wanted).forEach(f -> byUser.putIfAbsent(f.userId(), f));
        fetchScoreSaber(difficulty, wanted).forEach(f -> byUser.putIfAbsent(f.userId(), f));
        List<Fetched> ordered = byUser.values().stream()
                .sorted(Comparator.comparingDouble(Fetched::accuracy).reversed())
                .limit(wanted)
                .toList();
        Map<Long, User> users = userRepository.findAllById(ordered.stream().map(Fetched::userId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<Row> rows = new ArrayList<>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            rows.add(row(i + 1, ordered.get(i), users.get(ordered.get(i).userId()), difficulty, complexity));
        }
        return LeaderboardPreviewResponse.builder()
                .mapDifficultyId(difficulty.getId())
                .songName(difficulty.getMap().getSongName())
                .difficulty(difficulty.getDifficulty() == null ? null : difficulty.getDifficulty().name())
                .characteristic(difficulty.getCharacteristic())
                .categoryCode(difficulty.getCategory().getCode())
                .status(difficulty.getStatus())
                .complexity(complexity)
                .complexitySource(current.isPresent() ? "current" : estimate.map(e -> "script " + e.getVersion()).orElse(null))
                .fetched(byUser.size())
                .rows(rows)
                .build();
    }

    private Row row(int rank, Fetched fetched, User user, MapDifficulty difficulty, Double complexity) {
        double ap = complexity == null ? 0.0
                : apCalculationService.calculateRawAP(fetched.accuracy(), complexity,
                        difficulty.getCategory().getScoreCurve()).rawAP();
        return Row.builder()
                .rank(rank)
                .userId(String.valueOf(fetched.userId()))
                .name(user != null ? user.getName() : fetched.name())
                .avatarUrl(user != null ? user.getAvatarUrl() : fetched.avatarUrl())
                .cdnAvatarUrl(user != null ? user.getCdnAvatarUrl() : null)
                .country(user != null ? user.getCountry() : fetched.country())
                .platform(fetched.platform())
                .accuracy(fetched.accuracy())
                .ap(ap)
                .modifiers(fetched.modifiers())
                .build();
    }

    private List<Fetched> fetchBeatLeader(MapDifficulty difficulty, int wanted) {
        List<Fetched> out = new ArrayList<>();
        if (difficulty.getBlLeaderboardId() == null) {
            return out;
        }
        for (int page = 1; out.size() < wanted; page++) {
            List<BeatLeaderScoreResponse> scores;
            try {
                scores = beatLeaderClient.getLeaderboardScores(difficulty.getBlLeaderboardId(), page, PAGE_SIZE);
            } catch (Exception e) {
                log.warn("BeatLeader board preview stopped on page {} for {}: {}", page, difficulty.getId(), e.getMessage());
                break;
            }
            for (BeatLeaderScoreResponse score : scores) {
                if (score.getPlayer() == null || score.getBaseScore() == null
                        || PlatformScoreMapper.hasBannedModifier(score.getModifiers())) {
                    continue;
                }
                Long userId = parseId(score.getPlayer().getId());
                if (userId == null) {
                    continue;
                }
                out.add(new Fetched(userId, score.getPlayer().getName(), score.getPlayer().getAvatar(),
                        score.getPlayer().getCountry(), "BEATLEADER", accuracy(score.getBaseScore(), difficulty),
                        score.getModifiers()));
            }
            if (scores.size() < PAGE_SIZE) {
                break;
            }
        }
        return out;
    }

    private List<Fetched> fetchScoreSaber(MapDifficulty difficulty, int wanted) {
        List<Fetched> out = new ArrayList<>();
        if (difficulty.getSsLeaderboardId() == null) {
            return out;
        }
        int totalPages = Integer.MAX_VALUE;
        for (int page = 1; out.size() < wanted && page <= totalPages; page++) {
            ScoreSaberScoresPage scoresPage;
            try {
                scoresPage = scoreSaberClient.getLeaderboardScores(difficulty.getSsLeaderboardId(), page);
            } catch (Exception e) {
                log.warn("ScoreSaber board preview stopped on page {} for {}: {}", page, difficulty.getId(), e.getMessage());
                break;
            }
            if (scoresPage == null || scoresPage.getData() == null) {
                break;
            }
            if (scoresPage.getMetadata() != null && scoresPage.getMetadata().getTotalPages() != null) {
                totalPages = scoresPage.getMetadata().getTotalPages();
            }
            for (ScoreSaberScoreResponse score : scoresPage.getData()) {
                if (score.getPlayer() == null || score.getUnmodifiedScore() == null
                        || PlatformScoreMapper.hasBannedModifier(score.getMods())) {
                    continue;
                }
                Long userId = parseId(score.getPlayer().getId());
                if (userId == null) {
                    continue;
                }
                out.add(new Fetched(userId, score.getPlayer().getName(), score.getPlayer().getAvatar(),
                        score.getPlayer().getCountry(), "SCORESABER", accuracy(score.getUnmodifiedScore(), difficulty),
                        score.getMods() == null ? null : String.join(",", score.getMods())));
            }
            if (scoresPage.getData().size() < PAGE_SIZE) {
                break;
            }
        }
        return out;
    }

    private static double accuracy(int score, MapDifficulty difficulty) {
        return Rounding.round(Math.min(1.0, (double) score / difficulty.getMaxScore()), 10);
    }

    private static Long parseId(String id) {
        try {
            return id == null ? null : Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
