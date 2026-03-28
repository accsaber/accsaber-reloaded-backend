package com.accsaber.backend.service.player;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.response.player.UserResponse;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserNameHistory;
import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;
import com.accsaber.backend.repository.user.UserNameHistoryRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.model.dto.response.milestone.LevelResponse;
import com.accsaber.backend.service.milestone.LevelService;
import com.accsaber.backend.service.stats.OverallStatisticsService;
import com.accsaber.backend.service.stats.RankingService;
import com.accsaber.backend.service.stats.StatisticsService;

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

    public UserResponse findByUserId(Long userId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        User user = userRepository.findByIdAndActiveTrue(resolved)
                .orElseThrow(() -> new ResourceNotFoundException("User", resolved));
        return toResponse(user);
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
    public User updateProfile(Long userId, String name, String avatarUrl, String country, Boolean ssInactive) {
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
        if (ssInactive != null)
            user.setSsInactive(ssInactive);
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
        user.setBanned(banned);
        userRepository.save(user);

        if (!banned) {
            recalculateAfterUnban(userId);
        } else {
            recalculateRankingsForUser(userId);
        }
    }

    @Async("rankingExecutor")
    public void recalculateAfterUnban(Long userId) {
        log.info("Recalculating stats and rankings after unbanning user {}", userId);
        List<UUID> categoryIds = statisticsRepository.findByUser_IdAndActiveTrue(userId).stream()
                .map(s -> s.getCategory().getId())
                .toList();

        for (UUID categoryId : categoryIds) {
            statisticsService.recalculate(userId, categoryId, false, false);
            rankingService.updateRankings(categoryId);
        }
        overallStatisticsService.updateOverallRankings();
        userRepository.assignXpRankings();
        userRepository.assignXpCountryRankings();
        log.info("Recalculation complete after unbanning user {}", userId);
    }

    private void recalculateRankingsForUser(Long userId) {
        log.info("Recalculating rankings after banning user {}", userId);
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

    public List<UserNameHistory> getNameHistory(Long userId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        return userNameHistoryRepository.findByUser_IdOrderByChangedAtDesc(resolved);
    }

    private UserResponse toResponse(User user) {
        LevelResponse levelResponse = levelService.calculateLevel(user.getTotalXp());
        return UserResponse.builder()
                .id(String.valueOf(user.getId()))
                .name(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .country(user.getCountry())
                .xpRanking(user.getXpRanking())
                .xpCountryRanking(user.getXpCountryRanking())
                .level(levelResponse.getLevel())
                .levelTitle(levelResponse.getTitle())
                .banned(user.isBanned())
                .ssInactive(user.isSsInactive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
