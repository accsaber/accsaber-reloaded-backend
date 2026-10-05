package com.accsaber.backend.service.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ComplexityDatasetServiceTest {

    static Stream<Arguments> fields() {
        return Stream.of(
                Arguments.of("null becomes an empty field", null, ""),
                Arguments.of("integers pass through", 42, "42"),
                Arguments.of("booleans pass through", true, "true"),
                Arguments.of("plain text passes through", "Lo-Fi Children", "Lo-Fi Children"),
                Arguments.of("commas get quoted", "Batthew, August", "\"Batthew, August\""),
                Arguments.of("quotes get doubled and quoted", "say \"hi\"", "\"say \"\"hi\"\"\""),
                Arguments.of("newlines get quoted", "two\nlines", "\"two\nlines\""),
                Arguments.of("timestamps become ISO instants",
                        Timestamp.from(Instant.parse("2026-09-04T12:34:56Z")), "2026-09-04T12:34:56Z"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fields")
    void writesEachValueAsOneCsvField(String scenario, Object value, String expected) {
        assertThat(ComplexityDatasetService.csvField(value)).isEqualTo(expected);
    }
}
