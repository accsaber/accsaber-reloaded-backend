package com.accsaber.backend.service.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.response.CurvePointResponse;
import com.accsaber.backend.model.dto.response.CurveResponse;
import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.CurvePoint;
import com.accsaber.backend.model.entity.CurveType;
import com.accsaber.backend.repository.CurvePointRepository;
import com.accsaber.backend.repository.CurveRepository;

@ExtendWith(MockitoExtension.class)
class CurveServiceTest {

    @Mock
    private CurveRepository curveRepository;

    @Mock
    private CurvePointRepository curvePointRepository;

    @InjectMocks
    private CurveService curveService;

    @Test
    void findById_throwsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(curveRepository.findByIdAndActiveTrue(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> curveService.findById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findById_pointLookupCurve_loadsItsPointsInOrder() {
        Curve curve = Curve.builder().id(UUID.randomUUID()).name("Score Curve").type(CurveType.POINT_LOOKUP).build();
        when(curveRepository.findByIdAndActiveTrue(curve.getId())).thenReturn(Optional.of(curve));
        when(curvePointRepository.findByCurveIdOrderByXAsc(curve.getId())).thenReturn(List.of(
                CurvePoint.builder().curve(curve).x(0.9).y(0.2).build(),
                CurvePoint.builder().curve(curve).x(0.99).y(0.8).build()));

        CurveResponse response = curveService.findById(curve.getId());

        assertThat(response.getType()).isEqualTo("POINT_LOOKUP");
        assertThat(response.getPoints()).extracting(CurvePointResponse::getX, CurvePointResponse::getY)
                .containsExactly(tuple(0.9, 0.2), tuple(0.99, 0.8));
    }

    @Test
    void findById_formulaCurve_doesNotLoadPoints() {
        Curve curve = Curve.builder().id(UUID.randomUUID()).name("Weight Curve").type(CurveType.FORMULA)
                .formula("EXPONENTIAL_DECAY").build();
        when(curveRepository.findByIdAndActiveTrue(curve.getId())).thenReturn(Optional.of(curve));

        CurveResponse response = curveService.findById(curve.getId());

        assertThat(response.getFormula()).isEqualTo("EXPONENTIAL_DECAY");
        assertThat(response.getPoints()).isNull();
        verifyNoInteractions(curvePointRepository);
    }

    @Test
    void toResponse_returnsNullForNullInput() {
        assertThat(CurveService.toResponse(null)).isNull();
    }
}
