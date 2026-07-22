package com.accsaber.backend.service.item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.item.UnusualEffectGroupsResponse;
import com.accsaber.backend.model.dto.response.item.UnusualEffectResponse;
import com.accsaber.backend.model.entity.item.CrateUnusualEffect;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.UnusualEffect;
import com.accsaber.backend.repository.item.CrateUnusualEffectRepository;
import com.accsaber.backend.repository.item.UnusualEffectRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UnusualEffectService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final UnusualEffectRepository unusualEffectRepository;
    private final CrateUnusualEffectRepository crateUnusualEffectRepository;

    public List<UnusualEffect> findAll(boolean includeInactive) {
        return includeInactive ? unusualEffectRepository.findAll() : unusualEffectRepository.findByActiveTrue();
    }

    public UnusualEffectGroupsResponse findAllGrouped(boolean includeHidden) {
        List<CrateUnusualEffect> attachments = crateUnusualEffectRepository.findAllHydrated();

        Map<UUID, List<UnusualEffect>> byCrate = new LinkedHashMap<>();
        Map<UUID, Item> cratesById = new LinkedHashMap<>();
        Set<UUID> attachedEffectIds = new HashSet<>();

        for (CrateUnusualEffect attachment : attachments) {
            Item crate = attachment.getCrateItem();
            attachedEffectIds.add(attachment.getEffect().getId());
            if (!includeHidden && !crate.isVisible()) {
                continue;
            }
            cratesById.putIfAbsent(crate.getId(), crate);
            byCrate.computeIfAbsent(crate.getId(), k -> new ArrayList<>()).add(attachment.getEffect());
        }

        List<UnusualEffectGroupsResponse.CrateGroup> groups = cratesById.values().stream()
                .map(crate -> UnusualEffectGroupsResponse.CrateGroup.builder()
                        .crateId(crate.getId())
                        .crateName(crate.getName())
                        .crateIconUrl(crate.getIconUrl())
                        .effects(byCrate.get(crate.getId()).stream()
                                .map(ItemMapper::toUnusualEffectResponse)
                                .toList())
                        .build())
                .toList();

        List<UnusualEffectResponse> ungrouped = unusualEffectRepository.findByActiveTrue().stream()
                .filter(effect -> !attachedEffectIds.contains(effect.getId()))
                .sorted(Comparator.comparing(UnusualEffect::getName))
                .map(ItemMapper::toUnusualEffectResponse)
                .toList();

        return UnusualEffectGroupsResponse.builder()
                .groups(groups)
                .ungrouped(ungrouped)
                .build();
    }

    public UnusualEffect findById(UUID id) {
        return unusualEffectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UnusualEffect", id));
    }

    @Transactional
    public UnusualEffect create(String key, String name, String description, Object effectSpec) {
        if (unusualEffectRepository.existsByKey(key)) {
            throw new ConflictException("An unusual effect with key '" + key + "' already exists");
        }
        if (effectSpec == null) {
            throw new ValidationException("effectSpec", "effectSpec is required");
        }
        UnusualEffect effect = UnusualEffect.builder()
                .key(key)
                .name(name)
                .description(description)
                .effectSpec(MAPPER.valueToTree(effectSpec))
                .active(true)
                .build();
        return unusualEffectRepository.save(effect);
    }

    @Transactional
    public UnusualEffect update(UUID id, String name, String description, Object effectSpec) {
        UnusualEffect effect = findById(id);
        if (name != null) {
            effect.setName(name);
        }
        if (description != null) {
            effect.setDescription(description);
        }
        if (effectSpec != null) {
            effect.setEffectSpec(MAPPER.valueToTree(effectSpec));
        }
        return unusualEffectRepository.save(effect);
    }

    @Transactional
    public void deactivate(UUID id) {
        UnusualEffect effect = findById(id);
        effect.setActive(false);
        unusualEffectRepository.save(effect);
    }

    @Transactional
    public UnusualEffect reactivate(UUID id) {
        UnusualEffect effect = findById(id);
        effect.setActive(true);
        return unusualEffectRepository.save(effect);
    }
}
