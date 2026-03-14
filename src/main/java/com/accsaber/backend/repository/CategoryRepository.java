package com.accsaber.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.accsaber.backend.model.entity.Category;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    @EntityGraph(attributePaths = { "scoreCurve", "weightCurve" })
    List<Category> findByActiveTrue();

    @EntityGraph(attributePaths = { "scoreCurve", "weightCurve" })
    Optional<Category> findByIdAndActiveTrue(UUID id);

    @EntityGraph(attributePaths = { "scoreCurve", "weightCurve" })
    Optional<Category> findByCodeAndActiveTrue(String code);

    @EntityGraph(attributePaths = { "scoreCurve", "weightCurve" })
    List<Category> findByWeightCurve_IdAndActiveTrue(UUID weightCurveId);
}
