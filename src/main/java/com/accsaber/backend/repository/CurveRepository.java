package com.accsaber.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.accsaber.backend.model.entity.Curve;

@Repository
public interface CurveRepository extends JpaRepository<Curve, UUID> {

    List<Curve> findByActiveTrue();

    Optional<Curve> findByIdAndActiveTrue(UUID id);

    Optional<Curve> findByNameAndActiveTrue(String name);
}
