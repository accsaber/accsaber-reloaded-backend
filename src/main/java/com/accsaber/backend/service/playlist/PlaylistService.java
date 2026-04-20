package com.accsaber.backend.service.playlist;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaylistService {

    private static final Logger log = LoggerFactory.getLogger(PlaylistService.class);

    private final CategoryRepository categoryRepository;
    private final MapDifficultyRepository mapDifficultyRepository;

    @Cacheable(value = "playlists", key = "#categoryCode")
    public Map<String, Object> generatePlaylist(String categoryCode, String syncUrl) {
        Category category = categoryRepository.findByCodeAndActiveTrue(categoryCode)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryCode));

        List<MapDifficulty> rankedDifficulties;
        if ("overall".equals(categoryCode)) {
            rankedDifficulties = mapDifficultyRepository
                    .findByCountForOverallAndStatusWithMap(MapDifficultyStatus.RANKED);
        } else {
            rankedDifficulties = mapDifficultyRepository
                    .findByCategoryIdAndStatusWithMap(category.getId(), MapDifficultyStatus.RANKED);
        }

        Map<String, Object> playlist = new LinkedHashMap<>();
        playlist.put("playlistTitle", "AccSaber " + category.getName() + " Ranked Maps");
        playlist.put("playlistAuthor", "AccSaber Reloaded");
        playlist.put("image", loadImageBase64(categoryCode));
        playlist.put("syncURL", syncUrl);
        playlist.put("songs", buildSongs(rankedDifficulties));

        return playlist;
    }

    @Cacheable(value = "unrankedPlaylists", key = "#categoryCode")
    public Map<String, Object> generateUnrankedPlaylist(String categoryCode, String syncUrl) {
        Category category = categoryRepository.findByCodeAndActiveTrue(categoryCode)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryCode));

        List<MapDifficultyStatus> statuses = List.of(MapDifficultyStatus.QUEUE, MapDifficultyStatus.QUALIFIED);
        List<MapDifficulty> unrankedDifficulties;
        if ("overall".equals(categoryCode)) {
            unrankedDifficulties = mapDifficultyRepository
                    .findByCountForOverallAndStatusInWithMap(statuses);
        } else {
            unrankedDifficulties = mapDifficultyRepository
                    .findByCategoryIdAndStatusInWithMap(category.getId(), statuses);
        }

        Map<String, Object> playlist = new LinkedHashMap<>();
        playlist.put("playlistTitle", "AccSaber " + category.getName() + " Queued Maps");
        playlist.put("playlistAuthor", "AccSaber Reloaded");
        playlist.put("image", loadImageBase64("unranked"));
        playlist.put("syncURL", syncUrl);
        playlist.put("songs", buildSongs(unrankedDifficulties));

        return playlist;
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

    private List<Map<String, Object>> buildSongs(List<MapDifficulty> difficulties) {
        LinkedHashMap<String, Map<String, Object>> songsByHash = new LinkedHashMap<>();

        for (MapDifficulty diff : difficulties) {
            var map = diff.getMap();
            String hash = map.getSongHash();

            songsByHash.computeIfAbsent(hash, h -> {
                Map<String, Object> song = new LinkedHashMap<>();
                song.put("hash", h);
                song.put("songName", map.getSongName());
                song.put("difficulties", new ArrayList<Map<String, String>>());
                return song;
            });

            @SuppressWarnings("unchecked")
            List<Map<String, String>> diffs = (List<Map<String, String>>) songsByHash.get(hash).get("difficulties");
            Map<String, String> diffEntry = new LinkedHashMap<>();
            diffEntry.put("characteristic", diff.getCharacteristic());
            diffEntry.put("name", diff.getDifficulty().getDbValue());
            diffs.add(diffEntry);
        }

        return new ArrayList<>(songsByHash.values());
    }

    private String loadImageBase64(String categoryCode) {
        String path = "playlist-images/" + categoryCode + ".png";
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                log.warn("Playlist image not found: {}", path);
                return "";
            }
            try (InputStream is = resource.getInputStream()) {
                byte[] bytes = is.readAllBytes();
                return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
            }
        } catch (IOException e) {
            log.error("Failed to load playlist image: {}", path, e);
            return "";
        }
    }
}
