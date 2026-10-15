package com.accsaber.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.CurvePoint;

public interface CurvePointRepository extends JpaRepository<CurvePoint, UUID> {

    List<CurvePoint> findByCurveIdOrderByXAsc(UUID curveId);
}
