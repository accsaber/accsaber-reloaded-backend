package com.accsaber.backend.service.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.client.BeatSaverClient;

@ExtendWith(MockitoExtension.class)
class MapZipCacheTest {

    @Mock
    private BeatSaverClient beatSaverClient;

    @TempDir
    Path directory;

    private static final String HASH = "ABCDEF0123456789ABCDEF0123456789ABCDEF01";

    @Test
    void downloadsOnceAndServesTheDiskCopyAfterwards() {
        when(beatSaverClient.downloadMapZip(HASH)).thenReturn(Optional.of(new byte[] { 1, 2, 3 }));
        MapZipCache cache = new MapZipCache(beatSaverClient, directory.resolve("zips").toString());

        assertThat(cache.get(HASH)).contains(new byte[] { 1, 2, 3 });
        assertThat(cache.get(HASH.toLowerCase())).contains(new byte[] { 1, 2, 3 });
        assertThat(Files.exists(directory.resolve("zips").resolve(HASH.toLowerCase() + ".zip"))).isTrue();
        verify(beatSaverClient, times(1)).downloadMapZip(HASH);
    }

    @Test
    void anythingButASongHashIsRefusedBeforeTouchingTheDisk() {
        MapZipCache cache = new MapZipCache(beatSaverClient, directory.toString());

        assertThat(cache.get("../../etc/passwd")).isEmpty();
        assertThat(cache.get("abc")).isEmpty();
        verify(beatSaverClient, never()).downloadMapZip(any());
        assertThat(directory.toFile().list()).isEmpty();
    }

    @Test
    void failedDownloadsLeaveNothingBehind() {
        String missing = "0000000000000000000000000000000000000000";
        when(beatSaverClient.downloadMapZip(missing)).thenReturn(Optional.empty());
        MapZipCache cache = new MapZipCache(beatSaverClient, directory.toString());

        assertThat(cache.get(missing)).isEmpty();
        assertThat(cache.get("")).isEmpty();
        assertThat(directory.toFile().list()).isEmpty();
    }
}
