package com.accsaber.backend.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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

        @Test
        void nullReturnsNull() {
            assertThat(HmdMapper.fromBeatLeaderId(null)).isNull();
        }

        @Test
        void zeroReturnsNull() {
            assertThat(HmdMapper.fromBeatLeaderId(0)).isNull();
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

        @Test
        void nullReturnsNull() {
            assertThat(HmdMapper.normalize(null)).isNull();
        }

        @Test
        void blankReturnsNull() {
            assertThat(HmdMapper.normalize("")).isNull();
            assertThat(HmdMapper.normalize("  ")).isNull();
        }

        @Test
        void numericZeroReturnsNull() {
            assertThat(HmdMapper.normalize("0")).isNull();
        }

        @Test
        void unknownNumericReturnsUnknown() {
            assertThat(HmdMapper.normalize("9999")).isEqualTo("Unknown");
        }
    }
}
