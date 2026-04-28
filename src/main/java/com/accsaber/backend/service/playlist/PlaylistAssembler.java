package com.accsaber.backend.service.playlist;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.accsaber.backend.model.entity.map.MapDifficulty;

@Component
public class PlaylistAssembler {

    private static final Logger log = LoggerFactory.getLogger(PlaylistAssembler.class);
    private static final String AUTHOR = "AccSaber Reloaded";
    private static final String IMAGE_DIR = "playlist-images/";
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(FETCH_TIMEOUT).build();

    public Map<String, Object> assemble(String title, String imageDataUri, String syncUrl,
            List<MapDifficulty> difficulties) {
        Map<String, Object> playlist = new LinkedHashMap<>();
        playlist.put("playlistTitle", title);
        playlist.put("playlistAuthor", AUTHOR);
        playlist.put("image", imageDataUri);
        playlist.put("syncURL", syncUrl);
        playlist.put("songs", buildSongs(difficulties));
        return playlist;
    }

    public String loadCategoryImage(String imageKey) {
        String path = IMAGE_DIR + imageKey + ".png";
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                log.warn("Playlist image not found: {}", path);
                return "";
            }
            try (InputStream is = resource.getInputStream()) {
                return encodePng(is.readAllBytes());
            }
        } catch (IOException e) {
            log.error("Failed to load playlist image: {}", path, e);
            return "";
        }
    }

    public String fetchAndEncodeImage(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(FETCH_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                log.warn("Failed to fetch playlist image {}: HTTP {}", url, response.statusCode());
                return "";
            }
            return encodePng(response.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Failed to fetch playlist image {}: {}", url, e.getMessage());
            return "";
        }
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

    private String encodePng(byte[] bytes) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
    }
}
