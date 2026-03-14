package com.accsaber.backend.service.stats;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.config.CacheConfig;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.response.player.LeaderboardResponse;
import com.accsaber.backend.model.entity.user.UserCategoryStatistics;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaderboardService {

    private final UserCategoryStatisticsRepository statisticsRepository;
    private final CategoryRepository categoryRepository;

    // Self-injection allows @Cacheable on loadGlobal to work via Spring proxy
    @Lazy
    @Autowired
    private LeaderboardService self;

    public Page<LeaderboardResponse> getGlobal(UUID categoryId, Pageable pageable) {
        verifyCategory(categoryId);
        return paginate(self.loadGlobal(categoryId), pageable);
    }

    public Page<LeaderboardResponse> getByCountry(UUID categoryId, String country, Pageable pageable) {
        verifyCategory(categoryId);
        List<LeaderboardResponse> filtered = self.loadGlobal(categoryId).stream()
                .filter(r -> country.equalsIgnoreCase(r.getCountry()))
                .toList();
        return paginate(filtered, pageable);
    }

    @Cacheable(cacheNames = CacheConfig.LEADERBOARD_CACHE, key = "#categoryId.toString()")
    public List<LeaderboardResponse> loadGlobal(UUID categoryId) {
        return statisticsRepository.findActiveByCategoryOrderByApDesc(categoryId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private LeaderboardResponse toResponse(UserCategoryStatistics stats) {
        return LeaderboardResponse.builder()
                .ranking(stats.getRanking())
                .countryRanking(stats.getCountryRanking())
                .userId(stats.getUser().getId())
                .userName(stats.getUser().getName())
                .country(stats.getUser().getCountry())
                .avatarUrl(stats.getUser().getAvatarUrl())
                .ap(stats.getAp())
                .averageAcc(stats.getAverageAcc())
                .averageAp(stats.getAverageAp())
                .rankedPlays(stats.getRankedPlays())
                .topPlayId(stats.getTopPlay() != null ? stats.getTopPlay().getId() : null)
                .build();
    }

    private <T> Page<T> paginate(List<T> list, Pageable pageable) {
        int start = (int) pageable.getOffset();
        if (start >= list.size()) {
            return new PageImpl<>(List.of(), pageable, list.size());
        }
        int end = Math.min(start + pageable.getPageSize(), list.size());
        return new PageImpl<>(list.subList(start, end), pageable, list.size());
    }

    private void verifyCategory(UUID categoryId) {
        categoryRepository.findByIdAndActiveTrue(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));
    }
}
