package com.accsaber.backend.service.mission;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.accsaber.backend.model.dto.response.mission.MissionCompletedResponse;
import com.accsaber.backend.model.dto.response.mission.UserMissionResponse;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.mission.MissionStatus;
import com.accsaber.backend.model.entity.mission.MissionType;
import com.accsaber.backend.model.entity.mission.UserMission;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.event.MissionCompletedEvent;
import com.accsaber.backend.model.event.ScoreSubmittedEvent;
import com.accsaber.backend.repository.mission.UserMissionRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;
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
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${accsaber.missions.enabled:false}")
    private boolean missionsEnabled;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onScoreSubmitted(ScoreSubmittedEvent event) {
        if (!missionsEnabled) {
            return;
        }
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
            if (mission.getStatus() != MissionStatus.active)
                continue;
            if (mission.getExpiresAt() != null && mission.getExpiresAt().isBefore(now))
                continue;
            if (evaluate(mission, latestScore)) {
                completeMission(mission, userId,
                        latestScore.getTimeSet() != null ? latestScore.getTimeSet() : Instant.now());
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
            case SCORES_N -> evalScoresN(mission, score);
        };
    }

    private boolean evalScoresN(UserMission mission, ScoreResponse score) {
        if (!score.isActive())
            return false;
        if (!matchesCategoryScope(mission, score))
            return false;
        mission.setProgressCount(mission.getProgressCount() + 1);
        return mission.getTargetCount() != null && mission.getProgressCount() >= mission.getTargetCount();
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
        return score.isActive();
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

    private void completeMission(UserMission mission, Long userId, Instant completedAt) {
        mission.setStatus(MissionStatus.completed);
        mission.setCompletedAt(completedAt);

        if (mission.getXpReward() > 0) {
            int xpReward = mission.getXpReward();
            levelUpAwardService.addMissionXp(userId, BigDecimal.valueOf(xpReward));
            creditXpToWindowMissions(userId, xpReward, completedAt);
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

        publishCompletionEvent(userId, mission);
    }

    @Transactional
    public void creditXp(Long userId, BigDecimal amount) {
        if (!missionsEnabled)
            return;
        if (amount == null)
            return;
        int gained = amount.setScale(0, RoundingMode.HALF_UP).intValue();
        creditXpToWindowMissions(userId, gained, Instant.now());
    }

    private void creditXpToWindowMissions(Long userId, int xpAmount, Instant completedAt) {
        if (xpAmount <= 0)
            return;
        Instant now = Instant.now();
        for (UserMission window : userMissionRepository.findAllActiveByUser(userId)) {
            if (window.getStatus() != MissionStatus.active)
                continue;
            if (window.getTemplate().getType() != MissionType.XP_IN_WINDOW)
                continue;
            if (window.getExpiresAt() != null && window.getExpiresAt().isBefore(now))
                continue;
            window.setProgressCount(window.getProgressCount() + xpAmount);
            if (window.getTargetXp() != null && window.getProgressCount() >= window.getTargetXp()) {
                completeMission(window, userId, completedAt);
            }
        }
    }

    private void publishCompletionEvent(Long userId, UserMission mission) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null)
            return;

        MissionCompletedResponse payload = MissionCompletedResponse.builder()
                .userId(userId)
                .userName(user.getName())
                .userCountry(user.getCountry())
                .userAvatarUrl(user.getAvatarUrl())
                .userCdnAvatarUrl(user.getCdnAvatarUrl())
                .completedAt(mission.getCompletedAt())
                .missionId(mission.getId())
                .templateId(mission.getTemplate() != null ? mission.getTemplate().getId() : null)
                .templateCode(mission.getTemplate() != null ? mission.getTemplate().getCode() : null)
                .templateName(mission.getTemplate() != null ? mission.getTemplate().getName() : null)
                .templateDescription(mission.getTemplate() != null
                        ? UserMissionResponse.renderDescription(mission) : null)
                .type(mission.getTemplate() != null && mission.getTemplate().getType() != null
                        ? mission.getTemplate().getType().name() : null)
                .pool(mission.getPool() != null ? mission.getPool().name() : null)
                .band(mission.getBand() != null ? mission.getBand().name() : null)
                .categoryId(mission.getCategory() != null ? mission.getCategory().getId() : null)
                .categoryCode(mission.getCategory() != null ? mission.getCategory().getCode() : null)
                .targetMapDifficultyId(mission.getTargetMapDifficulty() != null
                        ? mission.getTargetMapDifficulty().getId() : null)
                .xpAwarded(mission.getXpReward() != null ? BigDecimal.valueOf(mission.getXpReward()) : null)
                .itemAwardedId(mission.isItemAwarded() && mission.getItemReward() != null
                        ? mission.getItemReward().getId() : null)
                .build();

        eventPublisher.publishEvent(new MissionCompletedEvent(payload));
    }

}
