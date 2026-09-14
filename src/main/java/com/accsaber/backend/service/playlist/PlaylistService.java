package com.accsaber.backend.service.playlist;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignDifficulty;
import com.accsaber.backend.model.entity.map.Batch;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.clan.war.ClanWarPoolService;
import com.accsaber.backend.service.score.ScoreService;
import com.accsaber.backend.service.snipe.SnipeQuery;
import com.accsaber.backend.service.snipe.SnipeSelection;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaylistService {

    private static final String OVERALL_CODE = "overall";

    private static final Logger log = LoggerFactory.getLogger(PlaylistService.class);

    private final CategoryRepository categoryRepository;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final ScoreRepository scoreRepository;
    private final UserRepository userRepository;
    private final CampaignDifficultyRepository campaignDifficultyRepository;
    private final ScoreService scoreService;
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

    @Cacheable(value = "missingPlaylists", key = "#userId + ':' + #categoryCode")
    public Map<String, Object> generateMissingPlaylist(Long userId, String categoryCode, String syncUrl) {
        Category category = requireCategory(categoryCode);
        User user = requireUser(userId);

        List<MapDifficulty> rankedDifficulties = OVERALL_CODE.equals(categoryCode)
                ? mapDifficultyRepository.findByCountForOverallAndStatusWithMap(MapDifficultyStatus.RANKED)
                : mapDifficultyRepository.findByCategoryIdAndStatusWithMap(category.getId(), MapDifficultyStatus.RANKED);

        Set<UUID> playedIds = new HashSet<>(scoreRepository.findDistinctMapDifficultyIdsByUser(userId));
        List<MapDifficulty> missing = rankedDifficulties.stream()
                .filter(d -> !playedIds.contains(d.getId()))
                .toList();

        return playlistAssembler.assemble(
                "AccSaber " + category.getName() + " Missing Maps - " + user.getName(),
                playlistAssembler.loadCategoryImage(categoryCode),
                syncUrl,
                missing);
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

    @Cacheable(value = "batchPlaylists", key = "#batch.id")
    public Map<String, Object> generateBatchPlaylist(Batch batch, String syncUrl) {
        List<MapDifficulty> difficulties = mapDifficultyRepository.findByBatch_IdAndActiveTrue(batch.getId());

        return playlistAssembler.assemble(
                "AccSaber " + batch.getName(),
                playlistAssembler.loadCategoryImage(OVERALL_CODE),
                syncUrl,
                difficulties);
    }

    public Map<String, Object> generateClanWarPlaylist(ClanWarPoolService.PlaylistSource source, String syncUrl) {
        return playlistAssembler.assemble(source.title(), playlistAssembler.loadCategoryImage(OVERALL_CODE), syncUrl,
                source.difficulties());
    }

    @Cacheable(value = "campaignPlaylists", key = "#campaign.id")
    public Map<String, Object> generateCampaignPlaylist(Campaign campaign, String syncUrl) {
        List<MapDifficulty> difficulties = campaignDifficultyRepository
                .findActiveWithMapByCampaignId(campaign.getId())
                .stream()
                .filter(cd -> !cd.isBarrier())
                .map(CampaignDifficulty::getMapDifficulty)
                .distinct()
                .toList();

        String image = campaign.getIconUrl() != null && !campaign.getIconUrl().isBlank()
                ? playlistAssembler.fetchAndEncodeImage(campaign.getIconUrl())
                : playlistAssembler.loadCategoryImage(OVERALL_CODE);

        return playlistAssembler.assemble(
                "AccSaber Campaign: " + campaign.getName(),
                image,
                syncUrl,
                difficulties);
    }

    public Map<String, Object> generateUserScoresPlaylist(Long userId, UUID categoryId, String search,
            Pageable pageable, String syncUrl) {
        User user = requireUser(userId);
        List<MapDifficulty> difficulties = scoreService.findDifficultiesByUser(userId, categoryId, search, pageable);

        String label = categoryId == null ? null
                : categoryRepository.findById(categoryId)
                        .map(Category::getName)
                        .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));

        String title = "AccSaber Scores - " + user.getName() + (label != null ? " (" + label + ")" : "");

        return playlistAssembler.assemble(
                title,
                playlistAssembler.fetchAndEncodeImage(user.getAvatarUrl()),
                syncUrl,
                difficulties);
    }

    public Map<String, Object> generateSnipePlaylist(SnipeSelection selection, SnipeQuery query, String syncUrl) {
        String title = "AccSaber: Snipe " + selection.target().getName()
                + (selection.categoryLabel() != null ? " (" + selection.categoryLabel() + ")" : "")
                + (query.isDefaultOrder() ? "" : " - " + query.orderLabel())
                + (query.unplayed().isDefault() ? "" : " - " + query.unplayed().getLabel());

        return playlistAssembler.assemble(
                title,
                playlistAssembler.fetchAndEncodeImage(selection.target().getAvatarUrl()),
                syncUrl,
                selection.difficulties());
    }

    @Caching(evict = {
            @CacheEvict(value = "playlists", allEntries = true),
            @CacheEvict(value = "missingPlaylists", allEntries = true)
    })
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

    @CacheEvict(value = "campaignPlaylists", key = "#campaignId")
    public void evictCampaignPlaylist(UUID campaignId) {
        log.info("Evicted campaign playlist cache for campaign: {}", campaignId);
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
