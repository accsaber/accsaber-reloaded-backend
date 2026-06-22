package com.accsaber.backend.controller;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.APResult;
import com.accsaber.backend.model.dto.response.APCalculationResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.service.score.APCalculationService;

import org.springframework.transaction.annotation.Transactional;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/calculate")
@RequiredArgsConstructor
@Tag(name = "Calculate")
public class CalculateController {

        private static final MathContext MATH_CONTEXT = new MathContext(16, RoundingMode.HALF_UP);

        private final APCalculationService apCalculationService;
        private final CategoryRepository categoryRepository;
        private final ScoreRepository scoreRepository;

        @GetMapping
        @Transactional(readOnly = true)
        @Operation(summary = "Calculate AP", description = "Calculate raw AP from a score percentage, complexity, and category. "
                        + "Optionally provide a userId to also compute the weighted AP for that score's position.")
        public ResponseEntity<APCalculationResponse> calculateAP(
                        @RequestParam BigDecimal scorePercentage,
                        @RequestParam BigDecimal complexity,
                        @RequestParam(defaultValue = "standard_acc") String categoryCode,
                        @RequestParam(required = false) Long userId) {

                Category category = categoryRepository.findByCodeAndActiveTrue(categoryCode)
                                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryCode));

                BigDecimal accuracy = scorePercentage.divide(BigDecimal.valueOf(100), MATH_CONTEXT);
                APResult result = apCalculationService.calculateRawAP(accuracy, complexity, category.getScoreCurve());

                BigDecimal weightedAp = null;
                if (userId != null) {
                        int position = findPosition(result.rawAP(), userId, category);
                        weightedAp = apCalculationService.calculateWeightedAP(result.rawAP(), position,
                                        category.getWeightCurve());
                }

                return ResponseEntity.ok(APCalculationResponse.builder()
                                .ap(result.rawAP())
                                .weightedAp(weightedAp)
                                .build());
        }

        private int findPosition(BigDecimal rawAP, Long userId, Category category) {
                return (int) scoreRepository.countActiveByUserAndCategoryWithApAtLeast(
                                userId, category.getId(), rawAP);
        }
}
