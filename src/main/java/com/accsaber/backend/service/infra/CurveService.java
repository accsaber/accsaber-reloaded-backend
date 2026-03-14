package com.accsaber.backend.service.infra;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.response.CurveResponse;
import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.repository.CurveRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurveService {

    private final CurveRepository curveRepository;

    public List<CurveResponse> findAllActive() {
        return curveRepository.findByActiveTrue().stream()
                .map(CurveService::toResponse)
                .toList();
    }

    public CurveResponse findById(UUID id) {
        Curve curve = curveRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Curve", id));
        return toResponse(curve);
    }

    public static CurveResponse toResponse(Curve curve) {
        if (curve == null) {
            return null;
        }
        return CurveResponse.builder()
                .id(curve.getId())
                .name(curve.getName())
                .type(curve.getType().name())
                .formula(curve.getFormula())
                .xParameterName(curve.getXParameterName())
                .xParameterValue(curve.getXParameterValue())
                .yParameterName(curve.getYParameterName())
                .yParameterValue(curve.getYParameterValue())
                .scale(curve.getScale())
                .shift(curve.getShift())
                .build();
    }
}
