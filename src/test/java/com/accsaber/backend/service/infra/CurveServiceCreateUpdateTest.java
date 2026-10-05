package com.accsaber.backend.service.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.dto.request.curve.CreateCurveRequest;
import com.accsaber.backend.model.dto.request.curve.UpdateCurveRequest;
import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.CurveType;
import com.accsaber.backend.repository.CurveRepository;

@ExtendWith(MockitoExtension.class)
class CurveServiceCreateUpdateTest {

    @Mock
    private CurveRepository curveRepository;

    @InjectMocks
    private CurveService curveService;

    @Nested
    class CreateCurve {

        @Test
        void allParameters_areMappedToEntity() {
            CreateCurveRequest request = new CreateCurveRequest();
            request.setName("Full Curve");
            request.setType(CurveType.FORMULA);
            request.setFormula("CUSTOM");
            request.setXParameterName("x");
            request.setXParameterValue(1.0);
            request.setYParameterName("y");
            request.setYParameterValue(10.0);
            request.setZParameterName("z");
            request.setZParameterValue(3.14);
            request.setScale(100.0);
            request.setShift(0.0);

            ArgumentCaptor<Curve> captor = ArgumentCaptor.forClass(Curve.class);
            when(curveRepository.save(captor.capture())).thenAnswer(inv -> {
                Curve c = inv.getArgument(0);
                c.setId(UUID.randomUUID());
                return c;
            });

            curveService.createCurve(request);

            Curve captured = captor.getValue();
            assertThat(captured.getName()).isEqualTo("Full Curve");
            assertThat(captured.getType()).isEqualTo(CurveType.FORMULA);
            assertThat(captured.getZParameterName()).isEqualTo("z");
            assertThat(captured.getZParameterValue()).isEqualByComparingTo(3.14);
            assertThat(captured.getScale()).isEqualByComparingTo(100.0);
        }
    }

    @Nested
    class UpdateCurve {

        @Test
        void partialUpdate_onlyChangesProvidedFields() {
            Curve existing = Curve.builder()
                    .id(UUID.randomUUID())
                    .name("Original")
                    .type(CurveType.FORMULA)
                    .formula("EXPONENTIAL_DECAY")
                    .xParameterName("position")
                    .xParameterValue(1.0)
                    .yParameterName("base")
                    .yParameterValue(0.965)
                    .build();

            UpdateCurveRequest request = new UpdateCurveRequest();
            request.setName("Updated Name");

            when(curveRepository.findByIdAndActiveTrue(existing.getId())).thenReturn(Optional.of(existing));
            when(curveRepository.save(any())).thenReturn(existing);

            curveService.updateCurve(existing.getId(), request);

            assertThat(existing.getName()).isEqualTo("Updated Name");
            assertThat(existing.getFormula()).isEqualTo("EXPONENTIAL_DECAY");
            assertThat(existing.getYParameterValue()).isEqualByComparingTo(0.965);
        }

        @Test
        void nullFields_areNotOverwritten() {
            Curve existing = Curve.builder()
                    .id(UUID.randomUUID())
                    .name("Keep Me")
                    .type(CurveType.FORMULA)
                    .formula("EXPONENTIAL_DECAY")
                    .xParameterName("x")
                    .xParameterValue(1.0)
                    .yParameterName("y")
                    .yParameterValue(10.0)
                    .zParameterName("z")
                    .zParameterValue(3.0)
                    .scale(100.0)
                    .shift(5.0)
                    .build();

            UpdateCurveRequest request = new UpdateCurveRequest();

            when(curveRepository.findByIdAndActiveTrue(existing.getId())).thenReturn(Optional.of(existing));
            when(curveRepository.save(any())).thenReturn(existing);

            curveService.updateCurve(existing.getId(), request);

            assertThat(existing.getName()).isEqualTo("Keep Me");
            assertThat(existing.getFormula()).isEqualTo("EXPONENTIAL_DECAY");
            assertThat(existing.getXParameterName()).isEqualTo("x");
            assertThat(existing.getYParameterName()).isEqualTo("y");
            assertThat(existing.getZParameterName()).isEqualTo("z");
            assertThat(existing.getScale()).isEqualByComparingTo(100.0);
            assertThat(existing.getShift()).isEqualByComparingTo(5.0);
        }
    }
}
