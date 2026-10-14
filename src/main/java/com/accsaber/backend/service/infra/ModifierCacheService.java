package com.accsaber.backend.service.infra;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.accsaber.backend.model.entity.Modifier;
import com.accsaber.backend.repository.ModifierRepository;
import com.accsaber.backend.util.PlatformScoreMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ModifierCacheService {

    private record Snapshot(Map<String, UUID> codeToId, Set<UUID> bannedIds, Set<UUID> noFailIds) {

        static Snapshot of(Map<String, UUID> codeToId) {
            Set<UUID> banned = codeToId.entrySet().stream()
                    .filter(e -> PlatformScoreMapper.BANNED_MODIFIER_CODES.contains(e.getKey()))
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toUnmodifiableSet());
            UUID noFail = codeToId.get("NF");
            return new Snapshot(codeToId, banned, noFail == null ? Set.of() : Set.of(noFail));
        }
    }

    private final ModifierRepository modifierRepository;
    private volatile Snapshot snapshot;

    public Map<String, UUID> getModifierCodeToId() {
        return snapshot().codeToId();
    }

    public boolean containsBannedModifier(Collection<UUID> modifierIds) {
        return containsAny(modifierIds, snapshot().bannedIds());
    }

    public boolean containsNoFail(Collection<UUID> modifierIds) {
        return containsAny(modifierIds, snapshot().noFailIds());
    }

    private static boolean containsAny(Collection<UUID> modifierIds, Set<UUID> targets) {
        return modifierIds != null && modifierIds.stream().anyMatch(targets::contains);
    }

    private Snapshot snapshot() {
        Snapshot current = snapshot;
        if (current == null) {
            current = Snapshot.of(modifierRepository.findByActiveTrue().stream()
                    .collect(Collectors.toMap(Modifier::getCode, Modifier::getId)));
            snapshot = current;
        }
        return current;
    }
}
