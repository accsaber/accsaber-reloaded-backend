package com.accsaber.backend.service.score;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.repository.score.ScoreRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreRankingService {

    private final ScoreRepository scoreRepository;

    public void reassignRanks(UUID difficultyId) {
        scoreRepository.reassignScoreRanks(difficultyId);
    }

    public int rankNewScore(UUID difficultyId, BigDecimal ap) {
        int rank = scoreRepository.countActiveScoresWithHigherAp(difficultyId, ap) + 1;
        scoreRepository.shiftScoreRanksDown(difficultyId, rank);
        return rank;
    }

    public int rankImprovedScore(UUID difficultyId, int oldRank, BigDecimal newAp) {
        scoreRepository.shiftScoreRanksUp(difficultyId, oldRank);
        int rank = scoreRepository.countActiveScoresWithHigherAp(difficultyId, newAp) + 1;
        scoreRepository.shiftScoreRanksDown(difficultyId, rank);
        return rank;
    }

    @Transactional
    public void reassignAllRanks() {
        List<UUID> difficultyIds = scoreRepository.findDistinctActiveDifficultyIds();
        if (difficultyIds.isEmpty()) {
            log.info("Score rank repair: no difficulties with active scores found");
            return;
        }
        log.info("Score rank repair: reassigning ranks for {} difficulties", difficultyIds.size());
        for (UUID difficultyId : difficultyIds) {
            scoreRepository.reassignScoreRanks(difficultyId);
        }
        log.info("Score rank repair: complete");
    }
}
