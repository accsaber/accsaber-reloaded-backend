package com.accsaber.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.accsaber.backend.model.entity.CurvePoint;

@Repository
public interface CurvePointRepository extends JpaRepository<CurvePoint, UUID> {

    List<CurvePoint> findByCurveIdOrderByXAsc(UUID curveId);
}
