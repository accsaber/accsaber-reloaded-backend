package com.accsaber.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.Curve;

public interface CurveRepository extends JpaRepository<Curve, UUID> {

    List<Curve> findByActiveTrue();

    Optional<Curve> findByIdAndActiveTrue(UUID id);
}
