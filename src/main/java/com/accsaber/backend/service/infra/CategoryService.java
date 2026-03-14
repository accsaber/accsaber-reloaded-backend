package com.accsaber.backend.service.infra;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.response.CategoryResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.repository.CategoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryResponse> findAllActive() {
        return categoryRepository.findByActiveTrue().stream()
                .map(this::toResponse)
                .toList();
    }

    public CategoryResponse findById(UUID id) {
        Category category = categoryRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
        return toResponse(category);
    }

    private CategoryResponse toResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .code(category.getCode())
                .name(category.getName())
                .description(category.getDescription())
                .scoreCurve(CurveService.toResponse(category.getScoreCurve()))
                .weightCurve(CurveService.toResponse(category.getWeightCurve()))
                .countForOverall(category.isCountForOverall())
                .build();
    }
}
