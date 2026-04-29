package com.accsaber.backend.service.songsuggest;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.model.dto.response.songsuggest.SongSuggestPlayerResponse;
import com.accsaber.backend.model.dto.response.songsuggest.SongSuggestScoreResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserCategoryStatistics;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SongSuggestService {

    private static final String OVERALL_CATEGORY_CODE = "overall";
    private static final int MIN_RANKED_PLAYS = 50;
    private static final int TOP_SCORE_COUNT = 30;
    private static final int CONSISTENCY_ANCHOR_INDEX = 5;
    private static final BigDecimal CONSISTENCY_THRESHOLD_RATIO = new BigDecimal("0.88");

    private final CategoryRepository categoryRepository;
    private final UserCategoryStatisticsRepository statisticsRepository;
    private final ScoreRepository scoreRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${accsaber.songsuggest.output-path:data/songsuggest/leaderboard.json}")
    private String outputPath;

    public Path getOutputFile() {
        return Path.of(outputPath);
    }

    public Optional<Instant> getRefreshTime() {
        Path file = getOutputFile();
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.getLastModifiedTime(file).toInstant());
        } catch (IOException e) {
            log.error("Failed to read songsuggest leaderboard mtime", e);
            return Optional.empty();
        }
    }

    @Async("taskExecutor")
    public void regenerateAsync() {
        try {
            generate();
        } catch (Exception e) {
            log.error("Song Suggest leaderboard regeneration failed", e);
        }
    }

    @Transactional(readOnly = true)
    public void generate() {
        long startMillis = System.currentTimeMillis();

        List<Long> eligibleUserIds = scoreRepository.findUserIdsWithAtLeastActiveScores(MIN_RANKED_PLAYS);
        log.info("songsuggest: {} active+unbanned players meet >={} active scores",
                eligibleUserIds.size(), MIN_RANKED_PLAYS);

        Map<Long, Integer> overallRankByUserId = loadOverallRankings();

        List<SongSuggestPlayerResponse> players = new ArrayList<>(eligibleUserIds.size());
        int dropped = 0;
        for (Long userId : eligibleUserIds) {
            List<Score> top = scoreRepository.findTopActiveByUserOrderByWeightedApDesc(
                    userId, PageRequest.of(0, TOP_SCORE_COUNT));

            if (top.size() < TOP_SCORE_COUNT) {
                dropped++;
                continue;
            }
            if (!isConsistent(top)) {
                dropped++;
                continue;
            }

            User user = top.get(0).getUser();
            int rank = overallRankByUserId.getOrDefault(userId, 0);
            players.add(buildPlayer(user, rank, top));
        }

        players.sort(Comparator.comparingInt(p -> p.getRank() == 0 ? Integer.MAX_VALUE : p.getRank()));

        log.info("songsuggest: {} players included, {} dropped by consistency/min-30 filter",
                players.size(), dropped);

        writeAtomically(players);
        log.info("songsuggest: leaderboard written to {} in {} ms", outputPath,
                System.currentTimeMillis() - startMillis);
    }

    private Map<Long, Integer> loadOverallRankings() {
        Optional<Category> overall = categoryRepository.findByCodeAndActiveTrue(OVERALL_CATEGORY_CODE);
        if (overall.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> ranks = new HashMap<>();
        for (UserCategoryStatistics s : statisticsRepository.findActiveByCategoryOrderByApDesc(overall.get().getId())) {
            if (s.getRanking() != null) {
                ranks.put(s.getUser().getId(), s.getRanking());
            }
        }
        return ranks;
    }

    private boolean isConsistent(List<Score> top30ByWeightedAp) {
        List<BigDecimal> rawAps = top30ByWeightedAp.stream()
                .map(Score::getAp)
                .sorted(Comparator.reverseOrder())
                .toList();
        BigDecimal anchor = rawAps.get(CONSISTENCY_ANCHOR_INDEX);
        BigDecimal threshold = anchor.multiply(CONSISTENCY_THRESHOLD_RATIO);
        return rawAps.get(rawAps.size() - 1).compareTo(threshold) >= 0;
    }

    private SongSuggestPlayerResponse buildPlayer(User user, int globalRank, List<Score> top) {
        List<Score> sortedByRawAp = top.stream()
                .sorted(Comparator.comparing(Score::getAp).reversed())
                .toList();

        List<SongSuggestScoreResponse> scores = new ArrayList<>(sortedByRawAp.size());
        for (int i = 0; i < sortedByRawAp.size(); i++) {
            Score s = sortedByRawAp.get(i);
            scores.add(SongSuggestScoreResponse.builder()
                    .songID(buildSongId(s.getMapDifficulty()))
                    .pp(s.getAp().floatValue())
                    .rank(i + 1)
                    .build());
        }

        return SongSuggestPlayerResponse.builder()
                .id(String.valueOf(user.getId()))
                .name(user.getName())
                .rank(globalRank)
                .top10kScore(scores)
                .build();
    }

    private String buildSongId(MapDifficulty difficulty) {
        if (difficulty.getSsLeaderboardId() != null && !difficulty.getSsLeaderboardId().isBlank()) {
            return difficulty.getSsLeaderboardId();
        }
        return difficulty.getCharacteristic().toUpperCase()
                + "-" + difficulty.getDifficulty().getNumericValue()
                + "-" + difficulty.getMap().getSongHash().toUpperCase();
    }

    private void writeAtomically(List<SongSuggestPlayerResponse> players) {
        Path target = getOutputFile();
        Path parent = target.getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp-" + UUID.randomUUID());
            objectMapper.writeValue(tmp.toFile(), players);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailure) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write songsuggest leaderboard to " + target, e);
        }
    }
}
