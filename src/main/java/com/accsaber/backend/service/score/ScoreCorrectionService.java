package com.accsaber.backend.service.score;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.map.MapDifficultyStatisticsService;
import com.accsaber.backend.service.skill.SkillService;
import com.accsaber.backend.service.stats.RankingService;
import com.accsaber.backend.service.stats.StatisticsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreCorrectionService {

        private final ScoreRepository scoreRepository;
        private final UserRepository userRepository;
        private final MapDifficultyStatisticsService mapDifficultyStatisticsService;
        private final StatisticsService statisticsService;
        private final RankingService rankingService;
        private final ScoreRankingService scoreRankingService;
        private final SkillService skillService;

        @Transactional
        public void removeScore(Long userId, UUID mapDifficultyId, String reason) {
                List<UUID> scoreIds = scoreRepository.findIdsByUserAndDifficulty(userId, mapDifficultyId);
                if (scoreIds.isEmpty()) {
                        throw new ResourceNotFoundException(
                                        "Scores for user " + userId + " on difficulty " + mapDifficultyId);
                }

                Score activeScore = scoreRepository
                                .findByUser_IdAndMapDifficulty_IdAndActiveTrue(userId, mapDifficultyId)
                                .orElse(null);
                UUID categoryId = activeScore != null
                                ? activeScore.getMapDifficulty().getCategory().getId()
                                : null;

                scoreRepository.nullifySupersedesReferences(scoreIds);
                scoreRepository.nullifyTopPlayReferences(scoreIds);
                scoreRepository.nullifyMilestoneScoreReferences(scoreIds);
                scoreRepository.deleteMergeScoreActions(scoreIds);
                scoreRepository.hardDeleteByIds(scoreIds);
                scoreRepository.flush();

                log.info("Hard-deleted {} score(s) for user {} on difficulty {} - reason: {}",
                                scoreIds.size(), userId, mapDifficultyId, reason);

                userRepository.recalculateTotalXpForAllActiveUsers();
                scoreRankingService.reassignRanks(mapDifficultyId);

                if (categoryId != null) {
                        mapDifficultyStatisticsService.recalculate(
                                        activeScore.getMapDifficulty(), userId);
                        statisticsService.recalculate(userId, categoryId);
                        final UUID finalCategoryId = categoryId;
                        rankingService.updateRankingsAsync(categoryId,
                                        () -> skillService.upsertSkill(userId, finalCategoryId));
                }
        }
}
