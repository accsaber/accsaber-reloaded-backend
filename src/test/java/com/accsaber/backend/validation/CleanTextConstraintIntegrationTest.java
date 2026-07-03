package com.accsaber.backend.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.accsaber.backend.model.dto.request.campaign.SendCampaignChatMessageRequest;
import com.accsaber.backend.service.moderation.TextModerationService;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class CleanTextConstraintIntegrationTest {

    private final Validator validator = buildValidator();

    private static Validator buildValidator() {
        TextModerationService moderation = new TextModerationService();
        ValidatorFactory factory = Validation.byDefaultProvider().configure()
                .constraintValidatorFactory(new ConstraintValidatorFactory() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
                        if (key.equals(CleanTextValidator.class)) {
                            return (T) new CleanTextValidator(moderation);
                        }
                        try {
                            return key.getDeclaredConstructor().newInstance();
                        } catch (ReflectiveOperationException e) {
                            throw new IllegalStateException(e);
                        }
                    }

                    @Override
                    public void releaseInstance(ConstraintValidator<?, ?> instance) {
                    }
                })
                .buildValidatorFactory();
        return factory.getValidator();
    }

    @Test
    void rejectsSlurOnAnnotatedField() {
        SendCampaignChatMessageRequest request = new SendCampaignChatMessageRequest();
        request.setContent("you faggot");
        Set<ConstraintViolation<SendCampaignChatMessageRequest>> violations = validator.validate(request);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("content"));
    }

    @Test
    void allowsGeneralProfanityOnAnnotatedField() {
        SendCampaignChatMessageRequest request = new SendCampaignChatMessageRequest();
        request.setContent("this map is fucking hard");
        Set<ConstraintViolation<SendCampaignChatMessageRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }
}
