package com.accsaber.backend.validation;

import com.accsaber.backend.service.moderation.TextModerationService;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CleanTextValidator implements ConstraintValidator<CleanText, String> {

    private final TextModerationService moderationService;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return moderationService.isClean(value);
    }
}
