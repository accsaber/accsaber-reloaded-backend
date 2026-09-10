package com.accsaber.backend.service.map;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void downloadsOnceAndServesTheDiskCopyAfterwards() {
        when(beatSaverClient.downloadMapZip("ABC")).thenReturn(Optional.of(new byte[] { 1, 2, 3 }));
        MapZipCache cache = new MapZipCache(beatSaverClient, directory.resolve("zips").toString());

        assertThat(cache.get("ABC")).contains(new byte[] { 1, 2, 3 });
        assertThat(cache.get("abc")).contains(new byte[] { 1, 2, 3 });
        assertThat(Files.exists(directory.resolve("zips").resolve("abc.zip"))).isTrue();
        verify(beatSaverClient, times(1)).downloadMapZip("ABC");
    }

    @Test
    void failedDownloadsLeaveNothingBehind() {
        when(beatSaverClient.downloadMapZip("missing")).thenReturn(Optional.empty());
        MapZipCache cache = new MapZipCache(beatSaverClient, directory.toString());

        assertThat(cache.get("missing")).isEmpty();
        assertThat(cache.get("")).isEmpty();
        assertThat(directory.toFile().list()).isEmpty();
    }
}
