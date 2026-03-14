package com.accsaber.backend.service.infra;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.model.dto.response.ModifierResponse;
import com.accsaber.backend.model.entity.Modifier;
import com.accsaber.backend.repository.ModifierRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ModifierService {

    private final ModifierRepository modifierRepository;

    public List<ModifierResponse> findAllActive() {
        return modifierRepository.findByActiveTrue().stream()
                .map(ModifierService::toResponse)
                .toList();
    }

    private static ModifierResponse toResponse(Modifier modifier) {
        return ModifierResponse.builder()
                .id(modifier.getId())
                .name(modifier.getName())
                .code(modifier.getCode())
                .multiplier(modifier.getMultiplier())
                .build();
    }
}
