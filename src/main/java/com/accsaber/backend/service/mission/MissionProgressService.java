package com.accsaber.backend.service.mission;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.mission.MissionStatus;
import com.accsaber.backend.model.entity.mission.MissionType;
import com.accsaber.backend.model.entity.mission.UserMission;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.event.ScoreSubmittedEvent;
import com.accsaber.backend.repository.mission.UserMissionRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.service.item.ItemService;
import com.accsaber.backend.service.item.LevelUpAwardService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MissionProgressService {

    private static final Logger log = LoggerFactory.getLogger(MissionProgressService.class);
    private static final String OVERALL_CODE = "overall";

    private final UserMissionRepository userMissionRepository;
    private final ScoreRepository scoreRepository;
    private final LevelUpAwardService levelUpAwardService;
    private final ItemService itemService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onScoreSubmitted(ScoreSubmittedEvent event) {
        ScoreResponse score = event.score();
        Long userId = Long.parseLong(score.getUserId());
        evaluateAllForUser(userId, score);
    }

    private void evaluateAllForUser(Long userId, ScoreResponse latestScore) {
        List<UserMission> active = userMissionRepository.findAllActiveByUser(userId);
        if (active.isEmpty())
            return;

        Instant now = Instant.now();
        for (UserMission mission : active) {
            if (mission.getExpiresAt() != null && mission.getExpiresAt().isBefore(now))
                continue;
            if (evaluate(mission, latestScore)) {
                completeMission(mission, latestScore);
            }
        }
    }

    private boolean evaluate(UserMission mission, ScoreResponse score) {
        MissionType type = mission.getTemplate().getType();
        return switch (type) {
            case PLAY_N_MAPS -> evalPlayN(mission, score);
            case XP_IN_WINDOW -> evalXpWindow(mission, score);
            case ACC_ON_MAP -> evalAccOnMap(mission, score);
            case AP_ON_MAP -> evalApOnMap(mission, score);
            case PB_SPECIFIC_MAP -> evalPbSpecificMap(mission, score);
            case PB_ABOVE_THRESHOLD -> evalPbAboveThreshold(mission, score);
            case SNIPE_PLAYER_ON_MAP -> evalSnipe(mission, score);
            case STREAK_ON_MAP -> evalStreakOnMap(mission, score);
            case STREAK_N_IN_CATEGORY -> evalStreakNInCategory(mission, score);
            case COMEBACK_PB -> evalPbSpecificMap(mission, score);
        };
    }

    private boolean evalStreakNInCategory(UserMission mission, ScoreResponse score) {
        if (!matchesCategoryScope(mission, score))
            return false;
        if (mission.getTargetStreak() == null)
            return false;
        Integer streak = score.getStreak115();
        if (streak == null || streak < mission.getTargetStreak())
            return false;
        mission.setProgressCount(mission.getProgressCount() + 1);
        return mission.getTargetCount() != null && mission.getProgressCount() >= mission.getTargetCount();
    }

    private boolean evalStreakOnMap(UserMission mission, ScoreResponse score) {
        if (!matchesTargetMap(mission, score))
            return false;
        if (mission.getTargetStreak() == null)
            return false;
        Integer streak = score.getStreak115();
        return streak != null && streak >= mission.getTargetStreak();
    }

    private boolean matchesCategoryScope(UserMission mission, ScoreResponse score) {
        if (mission.getCategory() == null)
            return true;
        if (OVERALL_CODE.equals(mission.getCategory().getCode()))
            return true;
        return mission.getCategory().getId().equals(score.getCategoryId());
    }

    private boolean evalPlayN(UserMission mission, ScoreResponse score) {
        if (score.isPartial())
            return false;
        if (!matchesCategoryScope(mission, score))
            return false;
        mission.setProgressCount(mission.getProgressCount() + 1);
        return mission.getTargetCount() != null && mission.getProgressCount() >= mission.getTargetCount();
    }

    private boolean evalXpWindow(UserMission mission, ScoreResponse score) {
        if (!matchesCategoryScope(mission, score))
            return false;
        if (score.getXpGained() == null)
            return false;
        int gained = score.getXpGained().setScale(0, RoundingMode.HALF_UP).intValue();
        mission.setProgressCount(mission.getProgressCount() + gained);
        return mission.getTargetXp() != null && mission.getProgressCount() >= mission.getTargetXp();
    }

    private boolean evalAccOnMap(UserMission mission, ScoreResponse score) {
        if (!matchesTargetMap(mission, score))
            return false;
        if (!score.isActive())
            return false;
        return score.getAccuracy() != null && mission.getTargetAcc() != null
                && score.getAccuracy().compareTo(mission.getTargetAcc()) >= 0;
    }

    private boolean evalApOnMap(UserMission mission, ScoreResponse score) {
        if (!matchesTargetMap(mission, score))
            return false;
        if (!score.isActive())
            return false;
        return score.getAp() != null && mission.getTargetAp() != null
                && score.getAp().compareTo(mission.getTargetAp()) >= 0;
    }

    private boolean evalPbSpecificMap(UserMission mission, ScoreResponse score) {
        if (!matchesTargetMap(mission, score))
            return false;
        if (!score.isActive())
            return false;
        return score.getAp() != null && mission.getTargetAp() != null
                && score.getAp().compareTo(mission.getTargetAp()) >= 0;
    }

    private boolean evalPbAboveThreshold(UserMission mission, ScoreResponse score) {
        if (!matchesCategoryScope(mission, score))
            return false;
        if (!score.isActive() || score.getAp() == null || mission.getTargetThresholdAp() == null)
            return false;
        Score newScore = scoreRepository.findById(score.getId()).orElse(null);
        if (newScore == null || newScore.getSupersedes() == null)
            return false;
        BigDecimal priorAp = newScore.getSupersedes().getAp();
        if (priorAp == null || priorAp.compareTo(mission.getTargetThresholdAp()) < 0)
            return false;
        if (score.getAp().compareTo(priorAp) <= 0)
            return false;
        mission.setProgressCount(mission.getProgressCount() + 1);
        return mission.getTargetCount() != null && mission.getProgressCount() >= mission.getTargetCount();
    }

    private boolean evalSnipe(UserMission mission, ScoreResponse score) {
        if (!matchesTargetMap(mission, score))
            return false;
        if (!score.isActive())
            return false;
        return score.getScore() != null && mission.getTargetScore() != null
                && score.getScore() > mission.getTargetScore();
    }

    private boolean matchesTargetMap(UserMission mission, ScoreResponse score) {
        if (mission.getTargetMapDifficulty() == null)
            return false;
        return mission.getTargetMapDifficulty().getId().equals(score.getMapDifficultyId());
    }

    private void completeMission(UserMission mission, ScoreResponse latestScore) {
        Long userId = Long.parseLong(latestScore.getUserId());
        mission.setStatus(MissionStatus.completed);
        mission.setCompletedAt(latestScore.getTimeSet() != null ? latestScore.getTimeSet() : Instant.now());

        if (mission.getXpReward() > 0) {
            levelUpAwardService.addMissionXp(userId, BigDecimal.valueOf(mission.getXpReward()));
        }
        if (mission.getItemReward() != null && !mission.isItemAwarded()) {
            try {
                itemService.awardSystem(userId, mission.getItemReward().getId(),
                        ItemSource.manual, "mission:" + mission.getId(),
                        "Mission reward: " + mission.getTemplate().getName());
                mission.setItemAwarded(true);
            } catch (Exception e) {
                log.warn("Failed to award crate for mission {}: {}", mission.getId(), e.getMessage());
            }
        }
    }

}
