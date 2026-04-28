package com.accsaber.backend.service.playlist;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficulty;

class PlaylistAssemblerTest {

    private final PlaylistAssembler assembler = new PlaylistAssembler();

    @Test
    void assembleProducesExpectedEnvelope() {
        MapDifficulty diff = difficulty("hash-1", "Song One", Difficulty.EXPERT_PLUS, "Standard");

        Map<String, Object> playlist = assembler.assemble("My Title", "data:image/png;base64,AAA",
                "https://accsaber.test/sync", List.of(diff));

        assertThat(playlist)
                .containsEntry("playlistTitle", "My Title")
                .containsEntry("playlistAuthor", "AccSaber Reloaded")
                .containsEntry("image", "data:image/png;base64,AAA")
                .containsEntry("syncURL", "https://accsaber.test/sync");
        assertThat(playlist).containsKey("songs");
    }

    @Test
    void buildSongsGroupsByHashAndCollectsDifficulties() {
        MapDifficulty diffA1 = difficulty("hash-1", "Song A", Difficulty.EXPERT, "Standard");
        MapDifficulty diffA2 = difficulty("hash-1", "Song A", Difficulty.EXPERT_PLUS, "Standard");
        MapDifficulty diffB = difficulty("hash-2", "Song B", Difficulty.HARD, "OneSaber");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> songs = (List<Map<String, Object>>) assembler.assemble(
                "x", "x", "x", List.of(diffA1, diffA2, diffB)).get("songs");

        assertThat(songs).hasSize(2);

        Map<String, Object> songA = songs.get(0);
        assertThat(songA).containsEntry("hash", "hash-1").containsEntry("songName", "Song A");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> diffsA = (List<Map<String, String>>) songA.get("difficulties");
        assertThat(diffsA).hasSize(2);
        assertThat(diffsA.get(0)).containsEntry("characteristic", "Standard").containsEntry("name", "Expert");
        assertThat(diffsA.get(1)).containsEntry("characteristic", "Standard").containsEntry("name", "ExpertPlus");

        Map<String, Object> songB = songs.get(1);
        assertThat(songB).containsEntry("hash", "hash-2").containsEntry("songName", "Song B");
    }

    @Test
    void emptyDifficultyListProducesEmptySongs() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> songs = (List<Map<String, Object>>) assembler.assemble(
                "x", "x", "x", List.of()).get("songs");

        assertThat(songs).isEmpty();
    }

    @Test
    void loadCategoryImageReturnsEmptyWhenMissing() {
        String result = assembler.loadCategoryImage("does-not-exist");

        assertThat(result).isEmpty();
    }

    @Test
    void fetchAndEncodeImageReturnsEmptyForBlankUrl() {
        assertThat(assembler.fetchAndEncodeImage(null)).isEmpty();
        assertThat(assembler.fetchAndEncodeImage("")).isEmpty();
        assertThat(assembler.fetchAndEncodeImage("   ")).isEmpty();
    }

    private MapDifficulty difficulty(String hash, String songName, Difficulty diff, String characteristic) {
        com.accsaber.backend.model.entity.map.Map map = com.accsaber.backend.model.entity.map.Map.builder()
                .id(UUID.randomUUID())
                .songHash(hash)
                .songName(songName)
                .build();
        return MapDifficulty.builder()
                .id(UUID.randomUUID())
                .map(map)
                .difficulty(diff)
                .characteristic(characteristic)
                .build();
    }
}
