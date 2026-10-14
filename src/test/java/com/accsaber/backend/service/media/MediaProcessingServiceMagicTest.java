package com.accsaber.backend.service.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class MediaProcessingServiceMagicTest {

    @Test
    void acceptsEveryAllowedImageFormat() {
        assertThat(MediaProcessingService.hasImageMagic(
                new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0 })).isTrue();
        assertThat(MediaProcessingService.hasImageMagic(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0 }))
                .isTrue();
        assertThat(MediaProcessingService.hasImageMagic(ascii("GIF89a......"))).isTrue();
        assertThat(MediaProcessingService.hasImageMagic(ascii("RIFF....WEBP"))).isTrue();
        assertThat(MediaProcessingService.hasImageMagic(ascii("....ftypavif"))).isTrue();
    }

    @Test
    void refusesAnythingElse() {
        assertThat(MediaProcessingService.hasImageMagic(ascii("<svg xmlns="))).isFalse();
        assertThat(MediaProcessingService.hasImageMagic(ascii("%PDF-1.7"))).isFalse();
        assertThat(MediaProcessingService.hasImageMagic(ascii("....ftyp"))).isFalse();
        assertThat(MediaProcessingService.hasImageMagic(new byte[0])).isFalse();
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
