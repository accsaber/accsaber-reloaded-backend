package com.accsaber.backend.service.mission;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
import com.accsaber.backend.model.dto.response.mission.MissionResponse;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.map.Batch;
import com.accsaber.backend.model.entity.map.BatchStatus;
import com.accsaber.backend.model.entity.mission.MissionPool;
import com.accsaber.backend.model.entity.mission.MissionStatus;
import com.accsaber.backend.model.entity.mission.MissionTrigger;
import com.accsaber.backend.model.entity.mission.MissionType;
import com.accsaber.backend.model.entity.mission.UserMission;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserRelationType;
import com.accsaber.backend.model.event.CampaignCompletedEvent;
import com.accsaber.backend.model.event.MissionCompletedEvent;
import com.accsaber.backend.model.event.ScoreSubmittedEvent;
import com.accsaber.backend.repository.map.BatchRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.mission.UserMissionRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;
import com.accsaber.backend.repository.user.UserRelationRepository;
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
    private final EventMissionService eventMissionService;
    private final UserCategoryStatisticsRepository statisticsRepository;
    private final BatchRepository batchRepository;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final UserRelationRepository userRelationRepository;

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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCampaignCompleted(CampaignCompletedEvent event) {
        if (!missionsEnabled) {
            return;
        }
        for (UserMission mission : openMissionsFor(event.userId(), MissionTrigger.CAMPAIGN)) {
            if (mission.getStatus() != MissionStatus.active)
                continue;
            if (evalCampaignComplete(mission, event)) {
                completeMission(mission, event.userId(),
                        event.completedAt() != null ? event.completedAt() : Instant.now());
            }
        }
    }

    private List<UserMission> openMissionsFor(Long userId, MissionTrigger trigger) {
        Instant now = Instant.now();
        return userMissionRepository.findAllActiveByUser(userId).stream()
                .filter(m -> m.getStatus() == MissionStatus.active)
                .filter(m -> m.getExpiresAt() == null || !m.getExpiresAt().isBefore(now))
                .filter(m -> m.getTemplate().getType().getTrigger() == trigger)
                .toList();
    }

    private void evaluateAllForUser(Long userId, ScoreResponse latestScore) {
        EvalContext ctx = new EvalContext(userId);
        for (UserMission mission : openMissionsFor(userId, MissionTrigger.SCORE)) {
            if (mission.getStatus() != MissionStatus.active)
                continue;
            if (evaluate(mission, latestScore, ctx)) {
                completeMission(mission, userId,
                        latestScore.getTimeSet() != null ? latestScore.getTimeSet() : Instant.now());
            }
        }
    }

    private boolean evalCampaignComplete(UserMission mission, CampaignCompletedEvent event) {
        if (Boolean.TRUE.equals(mission.getTargetCuratedOnly())
                && event.campaignStatus() != CampaignStatus.CURATED) {
            return false;
        }
        return addProgress(mission, 1);
    }

    private boolean evalSnipeRivalAnyMap(UserMission mission, ScoreResponse score, EvalContext ctx) {
        if (!matchesCategoryScope(mission, score))
            return false;
        if (!score.isActive() || score.getScore() == null)
            return false;
        List<Long> rivalIds = ctx.rivals();
        if (rivalIds.isEmpty())
            return false;
        if (!scoreRepository.existsRivalScoreBelow(score.getMapDifficultyId(), rivalIds, score.getScore()))
            return false;
        return addProgress(mission, 1);
    }

    private boolean evalApGainOverall(UserMission mission, ScoreResponse score, EvalContext ctx) {
        if (!score.isActive() || mission.getTargetAp() == null)
            return false;
        BigDecimal gained = statisticsRepository
                .findActiveApGainOverPrevious(ctx.userId, OVERALL_CODE)
                .orElse(BigDecimal.ZERO);
        if (gained.signum() <= 0)
            return false;
        mission.setProgressAp(mission.getProgressAp().add(gained));
        return mission.getProgressAp().compareTo(mission.getTargetAp()) >= 0;
    }

    private boolean evalBatchPlayN(UserMission mission, ScoreResponse score, EvalContext ctx) {
        if (!matchesCategoryScope(mission, score))
            return false;
        UUID latestBatchId = ctx.latestReleasedBatchId();
        if (latestBatchId == null)
            return false;
        if (!mapDifficultyRepository.existsByIdAndBatch_Id(score.getMapDifficultyId(), latestBatchId))
            return false;
        return addProgress(mission, 1);
    }

    private boolean evalPbRankedBefore(UserMission mission, ScoreResponse score) {
        if (!matchesCategoryScope(mission, score))
            return false;
        if (!score.isActive() || mission.getTargetRankedBefore() == null)
            return false;
        if (!mapDifficultyRepository.existsByIdAndRankedAtBefore(score.getMapDifficultyId(),
                mission.getTargetRankedBefore()))
            return false;
        return addProgress(mission, 1);
    }

    private final class EvalContext {
        private final Long userId;
        private List<Long> rivals;
        private UUID latestReleasedBatchId;
        private boolean latestReleasedBatchResolved;

        private EvalContext(Long userId) {
            this.userId = userId;
        }

        private List<Long> rivals() {
            if (rivals == null) {
                rivals = userRelationRepository.findActiveTargetUserIdsByTypes(userId,
                        List.of(UserRelationType.rival));
            }
            return rivals;
        }

        private UUID latestReleasedBatchId() {
            if (!latestReleasedBatchResolved) {
                latestReleasedBatchId = batchRepository
                        .findFirstByStatusOrderByReleasedAtDesc(BatchStatus.RELEASED)
                        .map(Batch::getId).orElse(null);
                latestReleasedBatchResolved = true;
            }
            return latestReleasedBatchId;
        }
    }

    private boolean evaluate(UserMission mission, ScoreResponse score, EvalContext ctx) {
        MissionType type = mission.getTemplate().getType();
        return switch (type) {
            case CAMPAIGN_COMPLETE_N -> throw new IllegalStateException(
                    "Non-score-triggered mission type reached score evaluation: " + type);
            case SNIPE_RIVAL_ANY_MAP -> evalSnipeRivalAnyMap(mission, score, ctx);
            case AP_GAIN_OVERALL -> evalApGainOverall(mission, score, ctx);
            case BATCH_PLAY_N -> evalBatchPlayN(mission, score, ctx);
            case PB_RANKED_BEFORE_N -> evalPbRankedBefore(mission, score);
            case PLAY_N_MAPS -> evalPlayN(mission, score);
            case XP_IN_WINDOW -> evalXpWindow(mission, score);
            case ACC_ON_MAP -> evalAccOnMap(mission, score);
            case AP_ON_MAP -> evalApOnMap(mission, score);
            case PB_SPECIFIC_MAP -> evalPbSpecificMap(mission, score);
            case PB_ABOVE_THRESHOLD -> evalPbAboveThreshold(mission, score);
            case SNIPE_PLAYER_ON_MAP -> evalSnipe(mission, score);
            case STREAK_ON_MAP -> evalStreakOnMap(mission, score);
            case STREAK_N_IN_CATEGORY -> evalStreakNInCategory(mission, score);
            case STREAK_SUM_N -> evalStreakSum(mission, score);
            case COMEBACK_PB -> evalPbSpecificMap(mission, score);
            case SCORES_N -> evalScoresN(mission, score);
        };
    }

    private boolean evalScoresN(UserMission mission, ScoreResponse score) {
        if (!score.isActive())
            return false;
        if (!matchesCategoryScope(mission, score))
            return false;
        return addProgress(mission, 1);
    }

    private boolean evalStreakNInCategory(UserMission mission, ScoreResponse score) {
        if (!matchesCategoryScope(mission, score))
            return false;
        if (mission.getTargetStreak() == null)
            return false;
        Integer streak = score.getStreak115();
        if (streak == null || streak < mission.getTargetStreak())
            return false;
        return addProgress(mission, 1);
    }

    private boolean evalStreakSum(UserMission mission, ScoreResponse score) {
        if (!matchesCategoryScope(mission, score))
            return false;
        Integer streak = score.getStreak115();
        if (streak == null || streak <= 0)
            return false;
        return addProgress(mission, streak);
    }

    private boolean addProgress(UserMission mission, int amount) {
        mission.setProgressCount(mission.getProgressCount() + amount);
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
        return addProgress(mission, 1);
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
                && displayedAcc(score.getAccuracy()).compareTo(displayedAcc(mission.getTargetAcc())) >= 0;
    }

    private static BigDecimal displayedAcc(BigDecimal acc) {
        return acc.setScale(4, RoundingMode.HALF_UP);
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
        return addProgress(mission, 1);
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

        if (mission.getPool() == MissionPool.event) {
            int bonusXp = eventMissionService.onEventMissionCompleted(mission, userId);
            if (bonusXp > 0) {
                creditXpToWindowMissions(userId, bonusXp, completedAt);
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
        for (UserMission window : openMissionsFor(userId, MissionTrigger.SCORE)) {
            if (window.getTemplate().getType() != MissionType.XP_IN_WINDOW)
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
                        ? MissionResponse.renderDescription(mission) : null)
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
