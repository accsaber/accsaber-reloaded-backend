package com.accsaber.backend.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.accsaber.backend.config.PlatformProperties;
import com.accsaber.backend.model.dto.platform.beatsaver.BeatSaverMapResponse;

@Tag("integration")
class BeatSaverClientIntegrationTest {

    private static final String KNOWN_HASH = "47a4e1e2aec435105ceeedda437bdccc7db18058";

    private static BeatSaverClient client;

    @BeforeAll
    static void setUp() {
        PlatformProperties properties = new PlatformProperties();
        PlatformProperties.PlatformConfig config = new PlatformProperties.PlatformConfig();
        config.setBaseUrl("https://api.beatsaver.com");
        config.setTimeoutMs(15000);
        config.setMaxRetries(2);
        properties.setBeatsaver(config);

        WebClient webClient = WebClient.builder()
                .baseUrl(config.getBaseUrl())
                .build();

        client = new BeatSaverClient(webClient, properties);
    }

    @Nested
    class GetMapByHash {

        @Test
        void returnsMapWithAllMappedFields() {
            Optional<BeatSaverMapResponse> result = client.getMapByHash(KNOWN_HASH);

            assertThat(result).isPresent();
            BeatSaverMapResponse map = result.get();
            assertThat(map.getId()).isNotBlank();

            assertThat(map.getMetadata()).isNotNull();
            assertThat(map.getMetadata().getSongName()).isNotBlank();
            assertThat(map.getMetadata().getSongAuthorName()).isNotNull();
            assertThat(map.getMetadata().getLevelAuthorName()).isNotBlank();

            assertThat(map.getVersions()).isNotEmpty();
            BeatSaverMapResponse.Version version = map.getVersions().getFirst();
            assertThat(version.getHash()).isNotBlank();
            assertThat(version.getCoverURL()).isNotBlank();
            assertThat(version.getDiffs()).isNotEmpty();

            for (BeatSaverMapResponse.Diff diff : version.getDiffs()) {
                assertThat(diff.getDifficulty()).as("difficulty").isNotBlank();
                assertThat(diff.getCharacteristic()).as("characteristic").isNotBlank();
                assertThat(diff.getMaxScore()).as("maxScore").isGreaterThan(0);
            }
        }

        @Test
        void returnsEmpty_forNonexistentHash() {
            assertThat(client.getMapByHash("0000000000000000000000000000000000000000")).isEmpty();
        }
    }
}
