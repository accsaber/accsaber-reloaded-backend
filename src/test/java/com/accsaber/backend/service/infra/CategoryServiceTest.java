package com.accsaber.backend.service.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.response.CategoryResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.CurveType;
import com.accsaber.backend.repository.CategoryRepository;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void findAllActive_returnsCategoriesWithCurves() {
        Curve scoreCurve = buildCurve("Score AP");
        Curve weightCurve = buildCurve("Weighted AP");
        Category category = buildCategory("true_acc", "True Acc", scoreCurve, weightCurve);

        when(categoryRepository.findByActiveTrue()).thenReturn(List.of(category));

        List<CategoryResponse> responses = categoryService.findAllActive();

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().getCode()).isEqualTo("true_acc");
        assertThat(responses.getFirst().getScoreCurve()).isNotNull();
        assertThat(responses.getFirst().getWeightCurve()).isNotNull();
    }

    @Test
    void findById_returnsCategoryResponse() {
        Category category = buildCategory("tech_acc", "Tech Acc", null, null);

        when(categoryRepository.findByIdAndActiveTrue(category.getId())).thenReturn(Optional.of(category));

        CategoryResponse response = categoryService.findById(category.getId());

        assertThat(response.getCode()).isEqualTo("tech_acc");
        assertThat(response.getScoreCurve()).isNull();
    }

    @Test
    void findById_throwsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(categoryRepository.findByIdAndActiveTrue(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.findById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Category buildCategory(String code, String name, Curve scoreCurve, Curve weightCurve) {
        return Category.builder()
                .id(UUID.randomUUID())
                .code(code)
                .name(name)
                .description(name + " Category")
                .scoreCurve(scoreCurve)
                .weightCurve(weightCurve)
                .countForOverall(true)
                .build();
    }

    private Curve buildCurve(String name) {
        return Curve.builder()
                .id(UUID.randomUUID())
                .name(name)
                .type(CurveType.FORMULA)
                .formula("PLACEHOLDER")
                .xParameterName("score")
                .xParameterValue(BigDecimal.valueOf(100))
                .yParameterName("scoreWeight")
                .yParameterValue(BigDecimal.ONE)
                .build();
    }
}
