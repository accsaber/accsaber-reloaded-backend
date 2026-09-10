package com.accsaber.backend.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SearchTextTest {

    @Test
    void ignoresCaseAndAccentsAndMatchesAnyField() {
        assertThat(SearchText.matches("lapiz", "El Lápiz", "someone")).isTrue();
        assertThat(SearchText.matches("LÁPIZ", "el lapiz")).isTrue();
        assertThat(SearchText.matches("taoh", "Easy Breezy", "Taoh")).isTrue();
        assertThat(SearchText.matches("nothing", "Easy Breezy", null)).isFalse();
    }

    @Test
    void blankSearchMatchesEverything() {
        assertThat(SearchText.matches(null, "anything")).isTrue();
        assertThat(SearchText.matches("   ", "anything")).isTrue();
    }
}
