package com.accsaber.backend.service.infra;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.accsaber.backend.model.entity.Modifier;
import com.accsaber.backend.repository.ModifierRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ModifierCacheService {

    private final ModifierRepository modifierRepository;
    private volatile Map<String, UUID> modifierCodeToId;

    public Map<String, UUID> getModifierCodeToId() {
        if (modifierCodeToId == null) {
            synchronized (this) {
                if (modifierCodeToId == null) {
                    modifierCodeToId = modifierRepository.findByActiveTrue().stream()
                            .collect(Collectors.toMap(Modifier::getCode, Modifier::getId));
                }
            }
        }
        return modifierCodeToId;
    }

    public void invalidate() {
        modifierCodeToId = null;
    }
}
