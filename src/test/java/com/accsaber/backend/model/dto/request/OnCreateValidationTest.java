package com.accsaber.backend.model.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.accsaber.backend.model.dto.request.milestone.MilestoneSetRequest;
import com.accsaber.backend.model.dto.request.news.NewsRequest;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class OnCreateValidationTest {

    @Test
    void createRequiresWhatItDidAndUpdateStaysPartial() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            NewsRequest news = new NewsRequest();
            news.setSlug("x".repeat(256));

            assertThat(validator.validate(news, OnCreate.class))
                    .extracting(v -> v.getPropertyPath().toString())
                    .containsExactlyInAnyOrder("title", "content", "slug");
            assertThat(validator.validate(news))
                    .extracting(v -> v.getPropertyPath().toString()).containsExactly("slug");

            MilestoneSetRequest set = new MilestoneSetRequest();
            assertThat(validator.validate(set, OnCreate.class))
                    .extracting(v -> v.getPropertyPath().toString()).containsExactly("title");
            assertThat(validator.validate(set)).isEmpty();
        }
    }
}
