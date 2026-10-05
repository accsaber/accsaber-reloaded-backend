package com.accsaber.backend.service.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.repository.CategoryRepository;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void findById_throwsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(categoryRepository.findByIdAndActiveTrue(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.findById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resolveId_parsesUuidWithoutLookup() {
        UUID id = UUID.randomUUID();

        assertThat(categoryService.resolveId(id.toString())).isEqualTo(id);
    }

    @Test
    void resolveId_resolvesCode() {
        Category category = Category.builder().id(UUID.randomUUID()).code("true_acc").build();
        when(categoryRepository.findByCodeAndActiveTrue("true_acc")).thenReturn(Optional.of(category));

        assertThat(categoryService.resolveId("true_acc")).isEqualTo(category.getId());
    }

    @Test
    void resolveId_returnsNullForNullOrBlank() {
        assertThat(categoryService.resolveId(null)).isNull();
        assertThat(categoryService.resolveId(" ")).isNull();
    }

    @Test
    void resolveId_throwsForUnknownCode() {
        when(categoryRepository.findByCodeAndActiveTrue("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.resolveId("nope"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
