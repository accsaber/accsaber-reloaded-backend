package com.accsaber.backend.service.snipe;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.score.SnipeComparisonResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.map.MapService;
import com.accsaber.backend.service.score.ScoreService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SnipeService {

    private static final String OVERALL_CODE = "overall";

    private final ScoreRepository scoreRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ScoreService scoreService;
    private final MapService mapService;

    public Page<SnipeComparisonResponse> findClosestScores(Long sniperId, Long targetId, String categoryCode,
            Pageable pageable) {
        if (sniperId.equals(targetId)) {
            throw new ValidationException("Sniper and target must be different players");
        }
        requireUser(sniperId);
        requireUser(targetId);
        CategoryFilter filter = resolveCategoryFilter(categoryCode);
        return scoreRepository
                .findClosestSnipePairs(sniperId, targetId, filter.categoryId(), filter.overallOnly(), pageable)
                .map(this::toComparison);
    }

    private SnipeComparisonResponse toComparison(Object[] row) {
        Score targetScore = (Score) row[0];
        Score sniperScore = (Score) row[1];
        return SnipeComparisonResponse.builder()
                .mapDifficulty(mapService.getDifficultyResponsePublic(targetScore.getMapDifficulty().getId()))
                .sniperScore(scoreService.mapToResponse(sniperScore))
                .targetScore(scoreService.mapToResponse(targetScore))
                .scoreDelta(targetScore.getScore() - sniperScore.getScore())
                .build();
    }

    CategoryFilter resolveCategoryFilter(String categoryCode) {
        if (categoryCode == null || categoryCode.isBlank()) {
            return new CategoryFilter(null, false);
        }
        if (OVERALL_CODE.equalsIgnoreCase(categoryCode)) {
            return new CategoryFilter(null, true);
        }
        Category category = categoryRepository.findByCodeAndActiveTrue(categoryCode)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryCode));
        return new CategoryFilter(category.getId(), false);
    }

    private void requireUser(Long userId) {
        userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    record CategoryFilter(UUID categoryId, boolean overallOnly) {
    }
}
