package com.accsaber.backend.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class HmdMapperTest {

    @Nested
    class FromBeatLeaderId {

        @Test
        void mapsKnownIds() {
            assertThat(HmdMapper.fromBeatLeaderId(1)).isEqualTo("Rift");
            assertThat(HmdMapper.fromBeatLeaderId(32)).isEqualTo("Quest");
            assertThat(HmdMapper.fromBeatLeaderId(64)).isEqualTo("Index");
            assertThat(HmdMapper.fromBeatLeaderId(256)).isEqualTo("Quest 2");
            assertThat(HmdMapper.fromBeatLeaderId(512)).isEqualTo("Quest 3");
            assertThat(HmdMapper.fromBeatLeaderId(513)).isEqualTo("Quest 3S");
            assertThat(HmdMapper.fromBeatLeaderId(128)).isEqualTo("Vive Cosmos");
            assertThat(HmdMapper.fromBeatLeaderId(16)).isEqualTo("Rift S");
            assertThat(HmdMapper.fromBeatLeaderId(70)).isEqualTo("PSVR 2");
            assertThat(HmdMapper.fromBeatLeaderId(75)).isEqualTo("Steam Frame");
        }

        @ParameterizedTest(name = "id {0}")
        @NullSource
        @ValueSource(ints = 0)
        void nullOrZeroReturnsNull(Integer id) {
            assertThat(HmdMapper.fromBeatLeaderId(id)).isNull();
        }

        @Test
        void unknownIdReturnsUnknown() {
            assertThat(HmdMapper.fromBeatLeaderId(9999)).isEqualTo("Unknown");
        }
    }

    @Nested
    class Normalize {

        @Test
        void convertsNumericStringToName() {
            assertThat(HmdMapper.normalize("64")).isEqualTo("Index");
            assertThat(HmdMapper.normalize("256")).isEqualTo("Quest 2");
        }

        @Test
        void passesNonNumericStringThrough() {
            assertThat(HmdMapper.normalize("Valve Index")).isEqualTo("Valve Index");
        }

        @ParameterizedTest(name = "value \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = "  ")
        void nullOrBlankReturnsNull(String value) {
            assertThat(HmdMapper.normalize(value)).isNull();
        }
    }
}
