package com.accsaber.backend.service.infra;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
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

    private record Snapshot(Map<String, UUID> codeToId, Set<UUID> bannedIds, Set<UUID> noFailIds,
            Set<UUID> speedUpIds) {

        static Snapshot of(Map<String, UUID> codeToId) {
            return new Snapshot(codeToId, ids(codeToId, PlatformScoreMapper.BANNED_MODIFIER_CODES),
                    ids(codeToId, Set.of("NF")), ids(codeToId, Set.of("FS", "SF")));
        }

        private static Set<UUID> ids(Map<String, UUID> codeToId, Set<String> codes) {
            return codes.stream().map(codeToId::get).filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
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

    public boolean containsSpeedUp(Collection<UUID> modifierIds) {
        return containsAny(modifierIds, snapshot().speedUpIds());
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
