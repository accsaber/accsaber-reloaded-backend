package com.accsaber.backend.service.snipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.map.PublicMapDifficultyResponse;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.dto.response.score.SnipeComparisonResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.map.MapService;
import com.accsaber.backend.service.score.ScoreQueryService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SnipeService {

    private static final String OVERALL_CODE = "overall";

    private final ScoreRepository scoreRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ScoreQueryService scoreQueryService;
    private final MapService mapService;

    public Page<SnipeComparisonResponse> findSnipeComparisons(SnipeQuery query, Pageable pageable) {
        requireDistinctPlayers(query);
        CategoryFilter filter = resolveCategoryFilter(query.categoryCode());
        Page<Object[]> pairs = scoreRepository.findSnipePairs(query.sniperId(), query.targetId(),
                filter.categoryId(), filter.overallOnly(), query.unplayed().allowsPlayed(),
                query.unplayed().allowsUnplayed(), sorted(pageable, query));

        List<Object[]> rows = pairs.getContent();
        List<UUID> difficultyIds = rows.stream()
                .map(r -> ((Score) r[0]).getMapDifficulty().getId())
                .distinct()
                .toList();
        Map<UUID, PublicMapDifficultyResponse> difficulties = mapService.getDifficultyResponsesPublic(difficultyIds);
        List<Score> scores = new ArrayList<>(rows.size() * 2);
        for (Object[] r : rows) {
            scores.add((Score) r[0]);
            if (r[1] != null) {
                scores.add((Score) r[1]);
            }
        }
        Map<UUID, ScoreResponse> scoreResponses = scoreQueryService.mapToResponsesByScoreId(scores);

        return pairs.map(r -> toComparison(r, difficulties, scoreResponses));
    }

    public SnipeSelection findSnipeDifficulties(SnipeQuery query, int size) {
        User target = requireDistinctPlayers(query);
        CategoryFilter filter = resolveCategoryFilter(query.categoryCode());
        Pageable pageable = size <= 0
                ? Pageable.unpaged(query.toSort())
                : PageRequest.of(0, size, query.toSort());
        List<MapDifficulty> difficulties = scoreRepository
                .findSnipePairs(query.sniperId(), query.targetId(), filter.categoryId(), filter.overallOnly(),
                        query.unplayed().allowsPlayed(), query.unplayed().allowsUnplayed(), pageable)
                .stream()
                .map(row -> ((Score) row[0]).getMapDifficulty())
                .toList();
        return new SnipeSelection(target, filter.label(), difficulties);
    }

    private static Pageable sorted(Pageable pageable, SnipeQuery query) {
        return pageable.isUnpaged()
                ? Pageable.unpaged(query.toSort())
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), query.toSort());
    }

    private SnipeComparisonResponse toComparison(Object[] row,
            Map<UUID, PublicMapDifficultyResponse> difficulties,
            Map<UUID, ScoreResponse> scoreResponses) {
        Score targetScore = (Score) row[0];
        Score sniperScore = (Score) row[1];
        UUID difficultyId = targetScore.getMapDifficulty().getId();
        PublicMapDifficultyResponse difficulty = difficulties.get(difficultyId);
        if (difficulty == null) {
            difficulty = mapService.getDifficultyResponsePublic(difficultyId);
        }
        return SnipeComparisonResponse.builder()
                .mapDifficulty(difficulty)
                .sniperScore(sniperScore == null ? null : resolveResponse(sniperScore, scoreResponses))
                .targetScore(resolveResponse(targetScore, scoreResponses))
                .scoreDelta(targetScore.getScore() - (sniperScore == null ? 0 : sniperScore.getScore()))
                .build();
    }

    private ScoreResponse resolveResponse(Score score, Map<UUID, ScoreResponse> scoreResponses) {
        ScoreResponse response = scoreResponses.get(score.getId());
        return response != null ? response : scoreQueryService.mapToResponse(score);
    }

    CategoryFilter resolveCategoryFilter(String categoryCode) {
        if (categoryCode == null || categoryCode.isBlank()) {
            return new CategoryFilter(null, false, null);
        }
        if (OVERALL_CODE.equalsIgnoreCase(categoryCode)) {
            return new CategoryFilter(null, true, categoryRepository.requireActiveByCode(OVERALL_CODE).getName());
        }
        Category category = categoryRepository.requireActiveByCode(categoryCode);
        return new CategoryFilter(category.getId(), false, category.getName());
    }

    private User requireDistinctPlayers(SnipeQuery query) {
        if (query.sniperId().equals(query.targetId())) {
            throw new ValidationException("Sniper and target must be different players");
        }
        userRepository.requireActive(query.sniperId());
        return userRepository.requireActive(query.targetId());
    }

    record CategoryFilter(UUID categoryId, boolean overallOnly, String label) {
    }
}
