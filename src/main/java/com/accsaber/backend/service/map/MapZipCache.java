package com.accsaber.backend.service.map;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.accsaber.backend.client.BeatSaverClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MapZipCache {

    private static final Pattern SONG_HASH = Pattern.compile("[0-9a-fA-F]{40}");

    private final BeatSaverClient beatSaverClient;
    private final Path directory;
    private final Map<String, Object> downloads = new ConcurrentHashMap<>();

    public MapZipCache(BeatSaverClient beatSaverClient,
            @Value("${accsaber.maps.zip-cache-path}") String directory) {
        this.beatSaverClient = beatSaverClient;
        this.directory = Path.of(directory);
    }

    public Optional<byte[]> get(String songHash) {
        if (songHash == null || !SONG_HASH.matcher(songHash).matches()) {
            return Optional.empty();
        }
        String key = songHash.toLowerCase();
        Object lock = downloads.computeIfAbsent(key, hash -> new Object());
        synchronized (lock) {
            try {
                return read(directory.resolve(key + ".zip"), songHash);
            } finally {
                downloads.remove(key, lock);
            }
        }
    }

    private Optional<byte[]> read(Path file, String songHash) {
        if (Files.isRegularFile(file)) {
            try {
                return Optional.of(Files.readAllBytes(file));
            } catch (IOException e) {
                log.warn("Could not read cached zip {}: {}", file, e.getMessage());
            }
        }
        Optional<byte[]> downloaded = beatSaverClient.downloadMapZip(songHash);
        downloaded.ifPresent(bytes -> store(file, bytes));
        return downloaded;
    }

    private void store(Path file, byte[] bytes) {
        try {
            Files.createDirectories(directory);
            Path temp = Files.createTempFile(directory, "download-", ".part");
            Files.write(temp, bytes);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("Could not cache zip {}: {}", file, e.getMessage());
        }
    }
}
