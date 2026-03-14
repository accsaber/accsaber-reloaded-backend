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
import com.accsaber.backend.model.dto.response.CurveResponse;
import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.CurveType;
import com.accsaber.backend.repository.CurveRepository;

@ExtendWith(MockitoExtension.class)
class CurveServiceTest {

    @Mock
    private CurveRepository curveRepository;

    @InjectMocks
    private CurveService curveService;

    @Test
    void findAllActive_returnsCurveResponses() {
        Curve curve = buildWeightCurve();

        when(curveRepository.findByActiveTrue()).thenReturn(List.of(curve));

        List<CurveResponse> responses = curveService.findAllActive();

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().getName()).isEqualTo("AccSaber Weight Curve");
        assertThat(responses.getFirst().getFormula()).isEqualTo("EXPONENTIAL_DECAY");
        assertThat(responses.getFirst().getType()).isEqualTo("FORMULA");
    }

    @Test
    void findById_weightCurve_mapsParametersCorrectly() {
        Curve curve = buildWeightCurve();

        when(curveRepository.findByIdAndActiveTrue(curve.getId())).thenReturn(Optional.of(curve));

        CurveResponse response = curveService.findById(curve.getId());

        assertThat(response.getXParameterName()).isEqualTo("position");
        assertThat(response.getYParameterName()).isEqualTo("base");
        assertThat(response.getYParameterValue()).isEqualByComparingTo(new BigDecimal("0.965"));
    }

    @Test
    void findById_scoreCurve_mapsScaleAndShiftCorrectly() {
        Curve curve = buildScoreCurve();

        when(curveRepository.findByIdAndActiveTrue(curve.getId())).thenReturn(Optional.of(curve));

        CurveResponse response = curveService.findById(curve.getId());

        assertThat(response.getScale()).isEqualByComparingTo(new BigDecimal("61"));
        assertThat(response.getShift()).isEqualByComparingTo(new BigDecimal("-18"));
    }

    @Test
    void findById_throwsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(curveRepository.findByIdAndActiveTrue(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> curveService.findById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void toResponse_returnsNullForNullInput() {
        assertThat(CurveService.toResponse(null)).isNull();
    }

    private Curve buildWeightCurve() {
        return Curve.builder()
                .id(UUID.randomUUID())
                .name("AccSaber Weight Curve")
                .type(CurveType.FORMULA)
                .formula("EXPONENTIAL_DECAY")
                .xParameterName("position")
                .xParameterValue(BigDecimal.ONE)
                .yParameterName("base")
                .yParameterValue(new BigDecimal("0.965"))
                .build();
    }

    private Curve buildScoreCurve() {
        return Curve.builder()
                .id(UUID.randomUUID())
                .name("AccSaber Score Curve")
                .type(CurveType.POINT_LOOKUP)
                .scale(new BigDecimal("61"))
                .shift(new BigDecimal("-18"))
                .build();
    }
}
