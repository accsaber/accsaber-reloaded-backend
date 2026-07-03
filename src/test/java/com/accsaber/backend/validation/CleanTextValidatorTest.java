package com.accsaber.backend.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.accsaber.backend.service.moderation.TextModerationService;

class CleanTextValidatorTest {

    private final CleanTextValidator validator = new CleanTextValidator(new TextModerationService());

    @Test
    void rejectsSlurs() {
        assertThat(validator.isValid("you faggot", null)).isFalse();
    }

    @Test
    void allowsGeneralProfanityCleanTextAndNull() {
        assertThat(validator.isValid("this map is fucking hard", null)).isTrue();
        assertThat(validator.isValid("a well designed campaign", null)).isTrue();
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
