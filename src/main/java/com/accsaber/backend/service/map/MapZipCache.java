package com.accsaber.backend.service.map;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.accsaber.backend.client.BeatSaverClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MapZipCache {

    private final BeatSaverClient beatSaverClient;
    private final Path directory;

    public MapZipCache(BeatSaverClient beatSaverClient,
            @Value("${accsaber.maps.zip-cache-path}") String directory) {
        this.beatSaverClient = beatSaverClient;
        this.directory = Path.of(directory);
    }

    public Optional<byte[]> get(String songHash) {
        if (songHash == null || songHash.isBlank()) {
            return Optional.empty();
        }
        Path file = directory.resolve(songHash.toLowerCase() + ".zip");
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
