package com.accsaber.backend.service.stats;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public Page<LeaderboardResponse> getGlobal(UUID categoryId, String search, Pageable pageable) {
        verifyCategory(categoryId);
        boolean hasSearch = search != null && !search.isBlank();
        Pageable effective = withDefaultSort(pageable, Sort.by(Sort.Direction.ASC, "ranking"));
        Page<UserCategoryStatistics> page = hasSearch
                ? statisticsRepository.findActiveByCategoryPagedWithSearch(categoryId, search.trim(), effective)
                : statisticsRepository.findActiveByCategoryPaged(categoryId, effective);
        return page.map(this::toResponse);
    }

    public Page<LeaderboardResponse> getByCountry(UUID categoryId, String country, String search, Pageable pageable) {
        verifyCategory(categoryId);
        boolean hasSearch = search != null && !search.isBlank();
        Pageable effective = withDefaultSort(pageable, Sort.by(Sort.Direction.ASC, "countryRanking"));
        Page<UserCategoryStatistics> page = hasSearch
                ? statisticsRepository.findActiveByCategoryAndCountryPagedWithSearch(
                        categoryId, country, search.trim(), effective)
                : statisticsRepository.findActiveByCategoryAndCountryPaged(categoryId, country, effective);
        return page.map(this::toResponse);
    }

    private LeaderboardResponse toResponse(UserCategoryStatistics stats) {
        return LeaderboardResponse.builder()
                .ranking(stats.getRanking())
                .countryRanking(stats.getCountryRanking())
                .userId(String.valueOf(stats.getUser().getId()))
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
