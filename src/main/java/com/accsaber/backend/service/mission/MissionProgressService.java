package com.accsaber.backend.service.mission;

import com.accsaber.backend.util.Rounding;

import java.time.Instant;
import java.util.List;
import java.util.Set;
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

import com.accsaber.backend.model.dto.EventMissionTargets;
import com.accsaber.backend.model.dto.response.mission.MissionCompletedResponse;
import com.accsaber.backend.model.dto.response.mission.MissionResponse;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.map.Batch;
import com.accsaber.backend.model.entity.map.BatchStatus;
import com.accsaber.backend.model.entity.mission.Event;
import com.accsaber.backend.model.entity.mission.MissionPool;
import com.accsaber.backend.model.entity.mission.MissionProgressAxis;
import com.accsaber.backend.model.entity.mission.MissionStatus;
import com.accsaber.backend.model.entity.mission.MissionTrigger;
import com.accsaber.backend.model.entity.mission.MissionType;
import com.accsaber.backend.model.entity.mission.UserMission;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserRelationType;
import com.accsaber.backend.model.event.CampaignCompletedEvent;
import com.accsaber.backend.model.event.SharedMissionCompletedEvent;
import com.accsaber.backend.model.event.MissionCompletedEvent;
import com.accsaber.backend.model.event.ScoreSubmittedEvent;
import com.accsaber.backend.repository.map.BatchRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.mission.MissionContributionRepository;
import com.accsaber.backend.repository.mission.UserEventProfileRepository;
import com.accsaber.backend.repository.mission.UserMissionRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;
import com.accsaber.backend.repository.user.UserRelationRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.clan.ClanMissionService;
import com.accsaber.backend.service.infra.ModifierCacheService;
import com.accsaber.backend.service.item.ItemService;
import com.accsaber.backend.service.item.LevelUpAwardService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MissionProgressService {

    private static final Logger log = LoggerFactory.getLogger(MissionProgressService.class);
    private static final String OVERALL_CODE = "overall";

    private final UserMissionRepository userMissionRepository;
    private final MissionContributionRepository contributionRepository;
    private final UserEventProfileRepository eventProfileRepository;
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
    private final ModifierCacheService modifierCacheService;
    private final ClanMissionService clanMissionService;

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
        EvalContext ctx = new EvalContext(event.userId());
        Instant completedAt = event.completedAt() != null ? event.completedAt() : Instant.now();
        for (UserMission mission : openMissionsFor(event.userId(), MissionTrigger.CAMPAIGN)) {
            if (mission.getStatus() != MissionStatus.active)
                continue;
            if (bankPersonal(mission, evalCampaignComplete(mission, event))) {
                completeMission(mission, event.userId(), completedAt);
            }
        }
        for (UserMission mission : sharedMissionsFor(MissionTrigger.CAMPAIGN, ctx)) {
            contribute(mission, event.userId(), evalCampaignComplete(mission, event));
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

    private List<UserMission> sharedMissionsFor(MissionTrigger trigger, EvalContext ctx) {
        List<UserMission> open = userMissionRepository.findActiveSharedFor(ctx.userId);
        if (open.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now();
        return open.stream()
                .filter(m -> m.getExpiresAt() == null || !m.getExpiresAt().isBefore(now))
                .filter(m -> m.getTemplate().getType().getTrigger() == trigger)
                .filter(m -> ctx.participatesIn(m.getTemplate().getEvent()))
                .toList();
    }

    private void evaluateAllForUser(Long userId, ScoreResponse latestScore) {
        EvalContext ctx = new EvalContext(userId);
        for (UserMission mission : openMissionsFor(userId, MissionTrigger.SCORE)) {
            if (mission.getStatus() != MissionStatus.active)
                continue;
            if (!isCreditable(mission, latestScore))
                continue;
            if (bankPersonal(mission, evaluate(mission, latestScore, ctx))) {
                completeMission(mission, userId,
                        latestScore.getTimeSet() != null ? latestScore.getTimeSet() : Instant.now());
            }
        }
        for (UserMission mission : sharedMissionsFor(MissionTrigger.SCORE, ctx)) {
            if (!isCreditable(mission, latestScore))
                continue;
            contribute(mission, userId, evaluate(mission, latestScore, ctx));
        }
    }

    private boolean bankPersonal(UserMission mission, double contribution) {
        if (contribution <= 0) {
            return false;
        }
        return switch (mission.getTemplate().getType().getAxis()) {
            case BINARY -> true;
            case AP -> {
                mission.setProgressAp(mission.getProgressAp() + contribution);
                yield mission.getTargetAp() != null && mission.getProgressAp() >= mission.getTargetAp();
            }
            case XP -> {
                mission.setProgressCount(mission.getProgressCount() + (int) contribution);
                yield mission.getTargetXp() != null && mission.getProgressCount() >= mission.getTargetXp();
            }
            case COUNT -> {
                mission.setProgressCount(mission.getProgressCount() + (int) contribution);
                yield mission.getTargetCount() != null && mission.getProgressCount() >= mission.getTargetCount();
            }
        };
    }

    private void contribute(UserMission mission, Long userId, double contribution) {
        if (contribution <= 0) {
            return;
        }
        Instant now = Instant.now();
        double accepted = contributionRepository.acceptContribution(
                mission.getId(), userId, contribution, contributionCap(mission), now);
        if (accepted <= 0) {
            return;
        }
        boolean banksAp = mission.getTemplate().getType().getAxis() == MissionProgressAxis.AP;
        userMissionRepository.bankSharedProgress(mission.getId(),
                banksAp ? 0 : (int) accepted, banksAp ? accepted : 0.0);
        if (userMissionRepository.claimSharedCompletion(mission.getId(), now) == 1) {
            if (mission.getPool() == MissionPool.clan) {
                clanMissionService.bankCompletion(mission);
            }
            eventPublisher.publishEvent(new SharedMissionCompletedEvent(mission.getId()));
        }
    }

    private Double contributionCap(UserMission mission) {
        if (mission.getTemplate().getType().getAxis() == MissionProgressAxis.BINARY) {
            return 1.0;
        }
        EventMissionTargets targets = mission.getTemplate().getEventTargets();
        Integer maxPerUser = targets != null ? targets.maxPerUser() : null;
        return maxPerUser != null ? maxPerUser.doubleValue() : null;
    }

    private boolean isCreditable(UserMission mission, ScoreResponse score) {
        EventMissionTargets targets = mission.getTemplate().getEventTargets();
        if (targets == null || !Boolean.TRUE.equals(targets.requirePass())) {
            return true;
        }
        return !score.isPartial() && !modifierCacheService.containsNoFail(score.getModifierIds());
    }

    private double evalCampaignComplete(UserMission mission, CampaignCompletedEvent event) {
        if (Boolean.TRUE.equals(mission.getTargetCuratedOnly())
                && event.campaignStatus() != CampaignStatus.CURATED) {
            return 0;
        }
        return 1;
    }

    private double evalSnipeRivalAnyMap(UserMission mission, ScoreResponse score, EvalContext ctx) {
        if (!matchesCategoryScope(mission, score))
            return 0;
        if (!score.isActive() || score.getScore() == null)
            return 0;
        List<Long> rivalIds = ctx.rivals();
        if (rivalIds.isEmpty())
            return 0;
        if (!scoreRepository.existsRivalScoreBelow(score.getMapDifficultyId(), rivalIds, score.getScore()))
            return 0;
        return 1;
    }

    private double evalApGainOverall(ScoreResponse score, EvalContext ctx) {
        if (!score.isActive())
            return 0;
        Double gained = statisticsRepository
                .findActiveApGainOverPrevious(ctx.userId, OVERALL_CODE)
                .orElse(0.0);
        return Math.signum(gained) > 0 ? gained : 0;
    }

    private double evalBatchPlayN(UserMission mission, ScoreResponse score, EvalContext ctx) {
        if (!matchesCategoryScope(mission, score))
            return 0;
        UUID latestBatchId = ctx.latestReleasedBatchId();
        if (latestBatchId == null)
            return 0;
        if (!mapDifficultyRepository.existsByIdAndBatch_Id(score.getMapDifficultyId(), latestBatchId))
            return 0;
        return 1;
    }

    private double evalPbRankedBefore(UserMission mission, ScoreResponse score) {
        if (!matchesCategoryScope(mission, score))
            return 0;
        if (!score.isActive() || mission.getTargetRankedBefore() == null)
            return 0;
        if (!mapDifficultyRepository.existsByIdAndRankedAtBefore(score.getMapDifficultyId(),
                mission.getTargetRankedBefore()))
            return 0;
        return 1;
    }

    private final class EvalContext {
        private final Long userId;
        private List<Long> rivals;
        private Set<UUID> begunEventIds;
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

        private boolean participatesIn(Event event) {
            if (event == null) {
                return true;
            }
            if (begunEventIds == null) {
                begunEventIds = Set.copyOf(eventProfileRepository.findEventIdsByUser(userId));
            }
            return begunEventIds.contains(event.getId());
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

    private double evaluate(UserMission mission, ScoreResponse score, EvalContext ctx) {
        MissionType type = mission.getTemplate().getType();
        return switch (type) {
            case CAMPAIGN_COMPLETE_N -> throw new IllegalStateException(
                    "Non-score-triggered mission type reached score evaluation: " + type);
            case SNIPE_RIVAL_ANY_MAP -> evalSnipeRivalAnyMap(mission, score, ctx);
            case AP_GAIN_OVERALL -> evalApGainOverall(score, ctx);
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

    private double evalScoresN(UserMission mission, ScoreResponse score) {
        if (!score.isActive())
            return 0;
        if (!matchesCategoryScope(mission, score))
            return 0;
        return 1;
    }

    private double evalStreakNInCategory(UserMission mission, ScoreResponse score) {
        if (!matchesCategoryScope(mission, score))
            return 0;
        if (mission.getTargetStreak() == null)
            return 0;
        Integer streak = score.getStreak115();
        if (streak == null || streak < mission.getTargetStreak())
            return 0;
        return 1;
    }

    private double evalStreakSum(UserMission mission, ScoreResponse score) {
        if (!matchesCategoryScope(mission, score))
            return 0;
        Integer streak = score.getStreak115();
        if (streak == null || streak <= 0)
            return 0;
        return streak;
    }

    private double evalStreakOnMap(UserMission mission, ScoreResponse score) {
        if (!matchesTargetMap(mission, score))
            return 0;
        if (mission.getTargetStreak() == null)
            return 0;
        Integer streak = score.getStreak115();
        return streak != null && streak >= mission.getTargetStreak() ? 1 : 0;
    }

    private boolean matchesCategoryScope(UserMission mission, ScoreResponse score) {
        if (mission.getCategory() == null)
            return true;
        if (OVERALL_CODE.equals(mission.getCategory().getCode()))
            return true;
        return mission.getCategory().getId().equals(score.getCategoryId());
    }

    private double evalPlayN(UserMission mission, ScoreResponse score) {
        return matchesCategoryScope(mission, score) ? 1 : 0;
    }

    private double evalXpWindow(UserMission mission, ScoreResponse score) {
        if (!matchesCategoryScope(mission, score))
            return 0;
        if (score.getXpGained() == null)
            return 0;
        return Rounding.round(score.getXpGained(), 0);
    }

    private double evalAccOnMap(UserMission mission, ScoreResponse score) {
        if (!matchesTargetMap(mission, score))
            return 0;
        if (!score.isActive())
            return 0;
        boolean met = score.getAccuracy() != null && mission.getTargetAcc() != null
                && displayedAcc(score.getAccuracy()).compareTo(displayedAcc(mission.getTargetAcc())) >= 0;
        return met ? 1 : 0;
    }

    private static Double displayedAcc(Double acc) {
        return Rounding.round(acc, 4);
    }

    private double evalApOnMap(UserMission mission, ScoreResponse score) {
        if (!matchesTargetMap(mission, score))
            return 0;
        if (!score.isActive())
            return 0;
        boolean met = score.getAp() != null && mission.getTargetAp() != null
                && score.getAp().compareTo(mission.getTargetAp()) >= 0;
        return met ? 1 : 0;
    }

    private double evalPbSpecificMap(UserMission mission, ScoreResponse score) {
        if (!matchesTargetMap(mission, score))
            return 0;
        return score.isActive() ? 1 : 0;
    }

    private double evalPbAboveThreshold(UserMission mission, ScoreResponse score) {
        if (!matchesCategoryScope(mission, score))
            return 0;
        if (!score.isActive() || score.getAp() == null || mission.getTargetThresholdAp() == null)
            return 0;
        Score newScore = scoreRepository.findById(score.getId()).orElse(null);
        if (newScore == null || newScore.getSupersedes() == null)
            return 0;
        Double priorAp = newScore.getSupersedes().getAp();
        if (priorAp == null || priorAp.compareTo(mission.getTargetThresholdAp()) < 0)
            return 0;
        if (score.getAp().compareTo(priorAp) <= 0)
            return 0;
        return 1;
    }

    private double evalSnipe(UserMission mission, ScoreResponse score) {
        if (!matchesTargetMap(mission, score))
            return 0;
        if (!score.isActive())
            return 0;
        boolean met = score.getScore() != null && mission.getTargetScore() != null
                && score.getScore() > mission.getTargetScore();
        return met ? 1 : 0;
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
            levelUpAwardService.addMissionXp(userId, (double) (xpReward));
            creditXpToWindowMissions(userId, xpReward, completedAt);
        }
        if (mission.getItemReward() != null && !mission.isItemAwarded()) {
            try {
                itemService.awardSystem(userId, mission.getItemReward().getId(),
                        ItemSource.mission, mission.getId().toString(),
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
        contributeToParent(mission, userId, completedAt);
    }

    private void contributeToParent(UserMission mission, Long userId, Instant completedAt) {
        UserMission parent = mission.getParentMission();
        if (parent != null && parent.getStatus() == MissionStatus.active && parent.getExpiresAt().isAfter(completedAt)) {
            contribute(parent, userId, 1);
        }
    }

    @Transactional
    public void creditXp(Long userId, Double amount) {
        if (!missionsEnabled)
            return;
        if (amount == null)
            return;
        int gained = (int) (Rounding.round(amount, 0));
        creditXpToWindowMissions(userId, gained, Instant.now());
    }

    private void creditXpToWindowMissions(Long userId, int xpAmount, Instant completedAt) {
        if (xpAmount <= 0)
            return;
        for (UserMission window : openMissionsFor(userId, MissionTrigger.SCORE)) {
            if (window.getTemplate().getType() != MissionType.XP_IN_WINDOW)
                continue;
            if (bankPersonal(window, xpAmount)) {
                completeMission(window, userId, completedAt);
            }
        }
        EvalContext ctx = new EvalContext(userId);
        for (UserMission window : sharedMissionsFor(MissionTrigger.SCORE, ctx)) {
            if (window.getTemplate().getType() != MissionType.XP_IN_WINDOW)
                continue;
            contribute(window, userId, xpAmount);
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
                .xpAwarded(mission.getXpReward() != null ? (double) (mission.getXpReward()) : null)
                .itemAwardedId(mission.isItemAwarded() && mission.getItemReward() != null
                        ? mission.getItemReward().getId() : null)
                .build();

        eventPublisher.publishEvent(new MissionCompletedEvent(payload));
    }

}
