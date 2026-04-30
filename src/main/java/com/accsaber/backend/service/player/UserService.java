package com.accsaber.backend.service.player;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.response.milestone.LevelResponse;
import com.accsaber.backend.model.dto.response.player.UserLevelData;
import com.accsaber.backend.model.dto.response.player.UserResponse;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserNameHistory;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;
import com.accsaber.backend.repository.user.UserDuplicateLinkRepository;
import com.accsaber.backend.repository.user.UserNameHistoryRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.map.MapDifficultyStatisticsService;
import com.accsaber.backend.service.milestone.LevelService;
import com.accsaber.backend.service.score.ScoreRankingService;
import com.accsaber.backend.service.skill.SkillService;
import com.accsaber.backend.service.stats.OverallStatisticsService;
import com.accsaber.backend.service.stats.RankingService;
import com.accsaber.backend.service.stats.StatisticsService;
import com.accsaber.backend.util.HmdMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserNameHistoryRepository userNameHistoryRepository;
    private final UserCategoryStatisticsRepository statisticsRepository;
    private final DuplicateUserService duplicateUserService;
    private final StatisticsService statisticsService;
    private final RankingService rankingService;
    private final LevelService levelService;
    private final OverallStatisticsService overallStatisticsService;
    private final ScoreRepository scoreRepository;
    private final ScoreRankingService scoreRankingService;
    private final MapDifficultyStatisticsService mapDifficultyStatisticsService;
    private final UserDuplicateLinkRepository userDuplicateLinkRepository;
    private final SkillService skillService;
    private final UserRelationService userRelationService;

    @Autowired
    @Lazy
    private UserService self;

    public UserResponse findByUserId(Long userId) {
        return findByUserId(userId, null);
    }

    public UserResponse findByUserId(Long userId, Long viewerUserId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        User user = userRepository.findByIdAndActiveTrue(resolved)
                .orElseThrow(() -> new ResourceNotFoundException("User", resolved));
        return toResponse(user, viewerUserId);
    }

    public Optional<User> findOptionalByUserId(Long userId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        return userRepository.findByIdAndActiveTrue(resolved);
    }

    @Transactional
    public User createUser(Long userId, String name, String avatarUrl, String country) {
        if (userRepository.findByIdAndActiveTrue(userId).isPresent()) {
            throw new ConflictException("User", userId);
        }
        return userRepository.save(User.builder()
                .id(userId)
                .name(name)
                .avatarUrl(avatarUrl)
                .country(country)
                .build());
    }

    public BigDecimal getTotalXp(Long userId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        User user = userRepository.findByIdAndActiveTrue(resolved)
                .orElseThrow(() -> new ResourceNotFoundException("User", resolved));
        return user.getTotalXp();
    }

    @Transactional
    public User updateProfile(Long userId, String name, String avatarUrl, String country, Boolean playerInactive) {
        User user = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (name != null && !name.equals(user.getName())) {
            userNameHistoryRepository.save(UserNameHistory.builder()
                    .user(user)
                    .name(user.getName())
                    .build());
            user.setName(name);
        }
        if (avatarUrl != null)
            user.setAvatarUrl(avatarUrl);
        if (country != null)
            user.setCountry(country);
        if (playerInactive != null)
            user.setPlayerInactive(playerInactive);
        return userRepository.save(user);
    }

    @Transactional
    public User overrideCountry(Long userId, String country) {
        User user = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        user.setCountry(country);
        user.setCountryOverride(true);
        userRepository.save(user);
        recalculateRankingsForUser(userId);
        return user;
    }

    @Transactional
    public User clearCountryOverride(Long userId) {
        User user = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        user.setCountryOverride(false);
        return userRepository.save(user);
    }

    @Transactional
    public void setBanned(Long userId, boolean banned) {
        User user = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (user.isBanned() == banned) {
            log.info("User {} is already {}, skipping", userId, banned ? "banned" : "unbanned");
            return;
        }
        user.setBanned(banned);
        userRepository.save(user);

        if (!banned) {
            self.recalculateAfterUnban(userId);
        } else {
            self.recalculateRankingsForUser(userId);
        }
    }

    @Async("rankingExecutor")
    @Transactional
    public void recalculateAfterUnban(Long userId) {
        log.info("Recalculating stats and rankings after unbanning user {}", userId);

        List<Score> scores = scoreRepository.findByUser_IdAndActiveTrue(userId);
        for (Score score : scores) {
            int rank = scoreRankingService.rankNewScore(
                    score.getMapDifficulty().getId(), score.getAp(), score.getTimeSet());
            score.setRank(rank);
            scoreRepository.save(score);
        }
        log.info("Re-inserted score ranks for {} difficulties after unbanning user {}", scores.size(), userId);

        recalculateMapStatsForScores(scores, userId);

        List<UUID> categoryIds = statisticsRepository.findByUser_IdAndActiveTrue(userId).stream()
                .map(s -> s.getCategory().getId())
                .toList();

        for (UUID categoryId : categoryIds) {
            statisticsService.recalculate(userId, categoryId, false, false);
            rankingService.updateRankings(categoryId);
            skillService.upsertSkill(userId, categoryId);
        }
        overallStatisticsService.updateOverallRankings();
        userRepository.assignXpRankings();
        userRepository.assignXpCountryRankings();
        log.info("Recalculation complete after unbanning user {}", userId);
    }

    @Async("rankingExecutor")
    @Transactional
    public void recalculateRankingsForUser(Long userId) {
        log.info("Recalculating rankings after banning user {}", userId);

        List<Score> scores = scoreRepository.findByUser_IdAndActiveTrue(userId);
        for (Score score : scores) {
            scoreRepository.shiftScoreRanksUp(score.getMapDifficulty().getId(), score.getRank());
        }
        log.info("Shifted score ranks for {} difficulties after banning user {}", scores.size(), userId);

        recalculateMapStatsForScores(scores, userId);

        List<UUID> categoryIds = statisticsRepository.findByUser_IdAndActiveTrue(userId).stream()
                .map(s -> s.getCategory().getId())
                .toList();

        for (UUID categoryId : categoryIds) {
            rankingService.updateRankings(categoryId);
        }
        overallStatisticsService.updateOverallRankings();
        userRepository.assignXpRankings();
        userRepository.assignXpCountryRankings();
        log.info("Ranking recalculation complete after banning user {}", userId);
    }

    private void recalculateMapStatsForScores(List<Score> scores, Long authorId) {
        List<MapDifficulty> difficulties = scores.stream()
                .map(Score::getMapDifficulty)
                .distinct()
                .toList();
        for (MapDifficulty difficulty : difficulties) {
            mapDifficultyStatisticsService.recalculate(difficulty, authorId);
        }
        log.info("Recalculated map stats for {} difficulties", difficulties.size());
    }

    public List<UserNameHistory> getNameHistory(Long userId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        return userNameHistoryRepository.findByUser_IdOrderByChangedAtDesc(resolved);
    }

    private UserResponse toResponse(User user, Long viewerUserId) {
        LevelResponse levelResponse = levelService.calculateLevel(user.getTotalXp());
        Optional<Score> latestScore = scoreRepository.findFirstByUser_IdAndActiveTrueOrderByTimeSetDesc(user.getId());
        String secondaryId = userDuplicateLinkRepository
                .findFirstByPrimaryUser_IdAndMergedTrue(user.getId())
                .map(link -> String.valueOf(link.getSecondaryUser().getId()))
                .orElse(null);
        String primaryId = String.valueOf(user.getId());
        boolean isSelf = viewerUserId != null && viewerUserId.equals(user.getId());
        return UserResponse.builder()
                .id(primaryId)
                .blId(secondaryId == null ? null : primaryId)
                .ssId(secondaryId)
                .name(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .country(user.getCountry())
                .xpRanking(user.getXpRanking())
                .xpCountryRanking(user.getXpCountryRanking())
                .levelData(UserLevelData.builder()
                        .level(levelResponse.getLevel())
                        .title(levelResponse.getTitle())
                        .xpForCurrentLevel(levelResponse.getXpForCurrentLevel())
                        .xpForNextLevel(levelResponse.getXpForNextLevel())
                        .progressPercent(levelResponse.getProgressPercent())
                        .build())
                .banned(user.isBanned())
                .playerInactive(user.isPlayerInactive())
                .hmd(latestScore.map(s -> HmdMapper.normalize(s.getHmd())).orElse(null))
                .lastActiveTime(latestScore.map(Score::getTimeSet).orElse(null))
                .createdAt(user.getCreatedAt())
                .relations(userRelationService.countsFor(user.getId(), isSelf))
                .build();
    }
}
