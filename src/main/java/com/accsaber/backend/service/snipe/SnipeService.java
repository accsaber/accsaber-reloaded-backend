package com.accsaber.backend.service.snipe;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.dto.response.score.SnipeComparisonResponse;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.score.ScoreService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SnipeService {

    private final ScoreRepository scoreRepository;
    private final UserRepository userRepository;
    private final ScoreService scoreService;

    public Page<SnipeComparisonResponse> findClosestScores(Long sniperId, Long targetId, Pageable pageable) {
        if (sniperId.equals(targetId)) {
            throw new ValidationException("Sniper and target must be different players");
        }
        requireUser(sniperId);
        requireUser(targetId);
        return scoreRepository.findClosestSnipePairs(sniperId, targetId, pageable)
                .map(this::toComparison);
    }

    private SnipeComparisonResponse toComparison(Object[] row) {
        Score targetScore = (Score) row[0];
        Score sniperScore = (Score) row[1];
        ScoreResponse sniper = scoreService.mapToResponse(sniperScore);
        ScoreResponse target = scoreService.mapToResponse(targetScore);
        return SnipeComparisonResponse.builder()
                .sniperScore(sniper)
                .targetScore(target)
                .scoreDelta(targetScore.getScore() - sniperScore.getScore())
                .build();
    }

    private void requireUser(Long userId) {
        userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}
