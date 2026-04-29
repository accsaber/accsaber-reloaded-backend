package com.accsaber.backend.service.stats;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.response.player.LeaderboardResponse;
import com.accsaber.backend.model.dto.response.player.XpLeaderboardResponse;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserCategoryStatistics;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.repository.user.UserXpRankingHistoryRepository;
import com.accsaber.backend.service.milestone.LevelService;
import com.accsaber.backend.util.HmdMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaderboardService {

    private final UserCategoryStatisticsRepository statisticsRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final UserXpRankingHistoryRepository xpRankingHistoryRepository;
    private final LevelService levelService;

    @Cacheable(value = "leaderboards", key = "'global:' + #categoryId + ':' + #search + ':' + #hmd + ':' + #includeInactive + ':' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort.toString()")
    public Page<LeaderboardResponse> getGlobal(UUID categoryId, String search, String hmd, boolean includeInactive,
            Pageable pageable) {
        return getGlobalFiltered(categoryId, search, hmd, includeInactive, null, pageable);
    }

    public Page<LeaderboardResponse> getGlobalFiltered(UUID categoryId, String search, String hmd,
            boolean includeInactive, Collection<Long> userIdFilter, Pageable pageable) {
        verifyCategory(categoryId);
        if (userIdFilter != null && userIdFilter.isEmpty()) {
            return Page.empty(pageable);
        }
        boolean hasSearch = search != null && !search.isBlank();
        String normalizedHmd = HmdMapper.normalize(hmd);
        Pageable effective = withDefaultSort(pageable, Sort.by(Sort.Direction.ASC, "ranking"));
        Page<UserCategoryStatistics> page;
        if (userIdFilter != null) {
            page = statisticsRepository.findActiveByCategoryPagedFilteredByUserIds(
                    categoryId, userIdFilter, null, hasSearch ? search.trim() : null,
                    includeInactive, normalizedHmd, effective);
        } else {
            page = hasSearch
                    ? statisticsRepository.findActiveByCategoryPagedWithSearch(categoryId, search.trim(),
                            includeInactive, normalizedHmd, effective)
                    : statisticsRepository.findActiveByCategoryPaged(categoryId, includeInactive, normalizedHmd,
                            effective);
        }
        return enrichWithLastWeekRanking(page, categoryId);
    }

    @Cacheable(value = "leaderboards", key = "'country:' + #categoryId + ':' + #country + ':' + #search + ':' + #hmd + ':' + #includeInactive + ':' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort.toString()")
    public Page<LeaderboardResponse> getByCountry(UUID categoryId, String country, String search, String hmd,
            boolean includeInactive, Pageable pageable) {
        return getByCountryFiltered(categoryId, country, search, hmd, includeInactive, null, pageable);
    }

    public Page<LeaderboardResponse> getByCountryFiltered(UUID categoryId, String country, String search, String hmd,
            boolean includeInactive, Collection<Long> userIdFilter, Pageable pageable) {
        verifyCategory(categoryId);
        if (userIdFilter != null && userIdFilter.isEmpty()) {
            return Page.empty(pageable);
        }
        boolean hasSearch = search != null && !search.isBlank();
        String normalizedHmd = HmdMapper.normalize(hmd);
        Pageable effective = withDefaultSort(pageable, Sort.by(Sort.Direction.ASC, "countryRanking"));
        Page<UserCategoryStatistics> page;
        if (userIdFilter != null) {
            page = statisticsRepository.findActiveByCategoryPagedFilteredByUserIds(
                    categoryId, userIdFilter, country, hasSearch ? search.trim() : null,
                    includeInactive, normalizedHmd, effective);
        } else {
            page = hasSearch
                    ? statisticsRepository.findActiveByCategoryAndCountryPagedWithSearch(
                            categoryId, country, search.trim(), includeInactive, normalizedHmd, effective)
                    : statisticsRepository.findActiveByCategoryAndCountryPaged(categoryId, country, includeInactive,
                            normalizedHmd, effective);
        }
        return enrichWithLastWeekRanking(page, categoryId);
    }

    public Page<XpLeaderboardResponse> getXpLeaderboard(String country, String search, String hmd,
            boolean includeInactive, Pageable pageable) {
        return getXpLeaderboardFiltered(country, search, hmd, includeInactive, null, pageable);
    }

    public Page<XpLeaderboardResponse> getXpLeaderboardFiltered(String country, String search, String hmd,
            boolean includeInactive, Collection<Long> userIdFilter, Pageable pageable) {
        if (userIdFilter != null && userIdFilter.isEmpty()) {
            return Page.empty(pageable);
        }
        boolean hasSearch = search != null && !search.isBlank();
        boolean hasCountry = country != null && !country.isBlank();
        String normalizedHmd = HmdMapper.normalize(hmd);
        Page<User> page;
        if (userIdFilter != null) {
            page = userRepository.findXpLeaderboardFilteredByUserIds(
                    userIdFilter,
                    hasCountry ? country : null,
                    hasSearch ? search.trim() : null,
                    includeInactive, normalizedHmd, pageable);
        } else if (hasCountry && hasSearch) {
            page = userRepository.findXpLeaderboardByCountryWithSearch(country, search.trim(), includeInactive,
                    normalizedHmd, pageable);
        } else if (hasCountry) {
            page = userRepository.findXpLeaderboardByCountry(country, includeInactive, normalizedHmd, pageable);
        } else if (hasSearch) {
            page = userRepository.findXpLeaderboardWithSearch(search.trim(), includeInactive, normalizedHmd, pageable);
        } else {
            page = userRepository.findXpLeaderboard(includeInactive, normalizedHmd, pageable);
        }
        return enrichXpWithLastWeekRanking(page);
    }

    private Page<LeaderboardResponse> enrichWithLastWeekRanking(Page<UserCategoryStatistics> page, UUID categoryId) {
        List<Long> userIds = page.getContent().stream()
                .map(s -> s.getUser().getId())
                .toList();
        Map<Long, Integer> lastWeekRankings = Map.of();
        if (!userIds.isEmpty()) {
            lastWeekRankings = statisticsRepository.findRankingsOneWeekAgo(categoryId, userIds).stream()
                    .collect(Collectors.toMap(
                            row -> ((Number) row[0]).longValue(),
                            row -> row[1] != null ? ((Number) row[1]).intValue() : null));
        }
        Map<Long, Integer> finalRankings = lastWeekRankings;
        return page.map(stats -> toResponse(stats, finalRankings.get(stats.getUser().getId())));
    }

    private Page<XpLeaderboardResponse> enrichXpWithLastWeekRanking(Page<User> page) {
        List<Long> userIds = page.getContent().stream()
                .map(User::getId)
                .toList();
        Map<Long, Integer> lastWeekRankings = Map.of();
        if (!userIds.isEmpty()) {
            lastWeekRankings = xpRankingHistoryRepository.findRankingsOneWeekAgo(userIds).stream()
                    .collect(Collectors.toMap(
                            row -> ((Number) row[0]).longValue(),
                            row -> row[1] != null ? ((Number) row[1]).intValue() : null));
        }
        Map<Long, Integer> finalRankings = lastWeekRankings;
        return page.map(user -> toXpResponse(user, finalRankings.get(user.getId())));
    }

    private XpLeaderboardResponse toXpResponse(User user, Integer rankingLastWeek) {
        return XpLeaderboardResponse.builder()
                .ranking(user.getXpRanking())
                .countryRanking(user.getXpCountryRanking())
                .userId(String.valueOf(user.getId()))
                .userName(user.getName())
                .country(user.getCountry())
                .avatarUrl(user.getAvatarUrl())
                .playerInactive(user.isPlayerInactive())
                .totalXp(user.getTotalXp())
                .level(levelService.calculateLevel(user.getTotalXp()).getLevel())
                .rankingLastWeek(rankingLastWeek)
                .build();
    }

    private LeaderboardResponse toResponse(UserCategoryStatistics stats, Integer rankingLastWeek) {
        return LeaderboardResponse.builder()
                .ranking(stats.getRanking())
                .countryRanking(stats.getCountryRanking())
                .userId(String.valueOf(stats.getUser().getId()))
                .userName(stats.getUser().getName())
                .country(stats.getUser().getCountry())
                .avatarUrl(stats.getUser().getAvatarUrl())
                .playerInactive(stats.getUser().isPlayerInactive())
                .ap(stats.getAp())
                .averageAcc(stats.getAverageAcc())
                .averageAp(stats.getAverageAp())
                .rankedPlays(stats.getRankedPlays())
                .topPlayId(stats.getTopPlay() != null ? stats.getTopPlay().getId() : null)
                .rankingLastWeek(rankingLastWeek)
                .build();
    }

    private Pageable withDefaultSort(Pageable pageable, Sort defaultSort) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort);
    }

    private void verifyCategory(UUID categoryId) {
        categoryRepository.findByIdAndActiveTrue(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));
    }
}
