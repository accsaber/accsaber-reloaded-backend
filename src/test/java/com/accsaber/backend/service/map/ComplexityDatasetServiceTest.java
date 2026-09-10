package com.accsaber.backend.service.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class ComplexityDatasetServiceTest {

    @Test
    void nullBecomesEmptyField() {
        assertThat(ComplexityDatasetService.csvField(null)).isEmpty();
    }

    @Test
    void plainValuesPassThrough() {
        assertThat(ComplexityDatasetService.csvField(42)).isEqualTo("42");
        assertThat(ComplexityDatasetService.csvField(true)).isEqualTo("true");
        assertThat(ComplexityDatasetService.csvField("Lo-Fi Children")).isEqualTo("Lo-Fi Children");
    }

    @Test
    void quotesFieldsThatWouldBreakTheRow() {
        assertThat(ComplexityDatasetService.csvField("Batthew, August")).isEqualTo("\"Batthew, August\"");
        assertThat(ComplexityDatasetService.csvField("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(ComplexityDatasetService.csvField("two\nlines")).isEqualTo("\"two\nlines\"");
    }

    @Test
    void timestampsBecomeIsoInstants() {
        Instant instant = Instant.parse("2026-09-04T12:34:56Z");
        assertThat(ComplexityDatasetService.csvField(Timestamp.from(instant))).isEqualTo("2026-09-04T12:34:56Z");
    }
}
