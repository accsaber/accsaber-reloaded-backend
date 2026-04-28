package com.accsaber.backend.service.playlist;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaylistService {

    public static final int SNIPE_PLAYLIST_MAX_SONGS = 100;
    private static final String OVERALL_CODE = "overall";

    private static final Logger log = LoggerFactory.getLogger(PlaylistService.class);

    private final CategoryRepository categoryRepository;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final ScoreRepository scoreRepository;
    private final UserRepository userRepository;
    private final PlaylistAssembler playlistAssembler;

    @Cacheable(value = "playlists", key = "#categoryCode")
    public Map<String, Object> generatePlaylist(String categoryCode, String syncUrl) {
        Category category = requireCategory(categoryCode);

        List<MapDifficulty> rankedDifficulties = "overall".equals(categoryCode)
                ? mapDifficultyRepository.findByCountForOverallAndStatusWithMap(MapDifficultyStatus.RANKED)
                : mapDifficultyRepository.findByCategoryIdAndStatusWithMap(category.getId(), MapDifficultyStatus.RANKED);

        return playlistAssembler.assemble(
                "AccSaber " + category.getName() + " Ranked Maps",
                playlistAssembler.loadCategoryImage(categoryCode),
                syncUrl,
                rankedDifficulties);
    }

    @Cacheable(value = "unrankedPlaylists", key = "#categoryCode")
    public Map<String, Object> generateUnrankedPlaylist(String categoryCode, String syncUrl) {
        Category category = requireCategory(categoryCode);

        List<MapDifficultyStatus> statuses = List.of(MapDifficultyStatus.QUEUE, MapDifficultyStatus.QUALIFIED);
        List<MapDifficulty> unrankedDifficulties = "overall".equals(categoryCode)
                ? mapDifficultyRepository.findByCountForOverallAndStatusInWithMap(statuses)
                : mapDifficultyRepository.findByCategoryIdAndStatusInWithMap(category.getId(), statuses);

        return playlistAssembler.assemble(
                "AccSaber " + category.getName() + " Queued Maps",
                playlistAssembler.loadCategoryImage("unranked"),
                syncUrl,
                unrankedDifficulties);
    }

    public Map<String, Object> generateSnipePlaylist(Long sniperId, Long targetId, String categoryCode, int size,
            String syncUrl) {
        if (sniperId.equals(targetId)) {
            throw new ValidationException("Sniper and target must be different players");
        }
        requireUser(sniperId);
        User target = requireUser(targetId);
        SnipeCategoryFilter filter = resolveSnipeCategoryFilter(categoryCode);

        int capped = Math.max(1, Math.min(size, SNIPE_PLAYLIST_MAX_SONGS));
        List<MapDifficulty> difficulties = scoreRepository
                .findClosestSnipePairs(sniperId, targetId, filter.categoryId, filter.overallOnly,
                        PageRequest.of(0, capped))
                .stream()
                .map(row -> ((Score) row[0]).getMapDifficulty())
                .toList();

        String title = "AccSaber: Snipe " + target.getName()
                    +(filter.label != null ? " (" + filter.label + ")" : "");

        return playlistAssembler.assemble(
                title,
                playlistAssembler.fetchAndEncodeImage(target.getAvatarUrl()),
                syncUrl,
                difficulties);
    }

    private SnipeCategoryFilter resolveSnipeCategoryFilter(String categoryCode) {
        if (categoryCode == null || categoryCode.isBlank()) {
            return new SnipeCategoryFilter(null, false, null);
        }
        if (OVERALL_CODE.equalsIgnoreCase(categoryCode)) {
            Category overall = requireCategory(OVERALL_CODE);
            return new SnipeCategoryFilter(null, true, overall.getName());
        }
        Category category = requireCategory(categoryCode);
        return new SnipeCategoryFilter(category.getId(), false, category.getName());
    }

    private record SnipeCategoryFilter(UUID categoryId, boolean overallOnly, String label) {
    }

    @CacheEvict(value = "playlists", allEntries = true)
    public void evictAllPlaylists() {
        log.info("Evicted all playlist caches");
    }

    @CacheEvict(value = "playlists", key = "#categoryCode")
    public void evictPlaylist(String categoryCode) {
        log.info("Evicted playlist cache for category: {}", categoryCode);
    }

    @CacheEvict(value = "unrankedPlaylists", allEntries = true)
    public void evictAllUnrankedPlaylists() {
        log.info("Evicted all unranked playlist caches");
    }

    @CacheEvict(value = "unrankedPlaylists", key = "#categoryCode")
    public void evictUnrankedPlaylist(String categoryCode) {
        log.info("Evicted unranked playlist cache for category: {}", categoryCode);
    }

    private Category requireCategory(String categoryCode) {
        return categoryRepository.findByCodeAndActiveTrue(categoryCode)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryCode));
    }

    private User requireUser(Long userId) {
        return userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}
