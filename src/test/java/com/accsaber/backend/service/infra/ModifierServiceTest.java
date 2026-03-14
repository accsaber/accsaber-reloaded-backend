package com.accsaber.backend.service.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.dto.response.ModifierResponse;
import com.accsaber.backend.model.entity.Modifier;
import com.accsaber.backend.repository.ModifierRepository;

@ExtendWith(MockitoExtension.class)
class ModifierServiceTest {

    @Mock
    private ModifierRepository modifierRepository;

    @InjectMocks
    private ModifierService modifierService;

    @Test
    void findAllActive_returnsModifierResponses() {
        Modifier noFail = Modifier.builder().id(UUID.randomUUID()).name("No Fail").code("NF")
                .multiplier(new BigDecimal("0.50")).build();
        Modifier fasterSong = Modifier.builder().id(UUID.randomUUID()).name("Faster Song").code("FS")
                .multiplier(new BigDecimal("1.08")).build();

        when(modifierRepository.findByActiveTrue()).thenReturn(List.of(noFail, fasterSong));

        List<ModifierResponse> responses = modifierService.findAllActive();

        assertThat(responses).hasSize(2);
        assertThat(responses.getFirst().getName()).isEqualTo("No Fail");
        assertThat(responses.get(1).getName()).isEqualTo("Faster Song");
    }

}
