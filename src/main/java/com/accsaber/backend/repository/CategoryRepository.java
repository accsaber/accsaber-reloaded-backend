package com.accsaber.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    @EntityGraph(attributePaths = { "scoreCurve", "weightCurve" })
    List<Category> findByActiveTrue();

    @EntityGraph(attributePaths = { "scoreCurve", "weightCurve" })
    Optional<Category> findByIdAndActiveTrue(UUID id);

    default Category requireActive(UUID id) {
        return findByIdAndActiveTrue(id).orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    @EntityGraph(attributePaths = { "scoreCurve", "weightCurve" })
    Optional<Category> findByCodeAndActiveTrue(String code);

    default Category requireActiveByCode(String code) {
        return findByCodeAndActiveTrue(code).orElseThrow(() -> new ResourceNotFoundException("Category", code));
    }

    @EntityGraph(attributePaths = { "scoreCurve", "weightCurve" })
    List<Category> findByActiveTrueAndCountForOverallTrue();
}
