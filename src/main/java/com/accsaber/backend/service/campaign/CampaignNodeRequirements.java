package com.accsaber.backend.service.campaign;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.campaign.CampaignBound;
import com.accsaber.backend.model.dto.request.campaign.CampaignModifierRequirementRequest;
import com.accsaber.backend.model.dto.request.campaign.CampaignTargetRequest;
import com.accsaber.backend.model.dto.request.campaign.UpdateCampaignDifficultyRequest;
import com.accsaber.backend.model.entity.Modifier;
import com.accsaber.backend.model.entity.campaign.CampaignDifficulty;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyModifier;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyModifier.CampaignDifficultyModifierId;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyTarget;
import com.accsaber.backend.model.entity.campaign.CampaignModifierRequirement;
import com.accsaber.backend.model.entity.campaign.CampaignRequirementType;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.ModifierRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyModifierRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyTargetRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class CampaignNodeRequirements {

    private static final Set<CampaignRequirementType> RANKED_ONLY_REQUIREMENTS = Set.of(
            CampaignRequirementType.AP, CampaignRequirementType.RANK);

    private final CampaignDifficultyTargetRepository campaignDifficultyTargetRepository;
    private final CampaignDifficultyModifierRepository campaignDifficultyModifierRepository;
    private final ModifierRepository modifierRepository;

    boolean applyRequirementUpdate(CampaignDifficulty difficulty, UpdateCampaignDifficultyRequest request) {
        boolean requirementChanged = false;
        if (request.getRequirementType() != null
                && request.getRequirementType() != difficulty.getRequirementType()) {
            assertRequirementAvailable(request.getRequirementType(), difficulty.getMapDifficulty());
            difficulty.setRequirementType(request.getRequirementType());
            requirementChanged = true;
        }
        CampaignEditRules.assertNoClearConflict(request.getClear(), "requirementValue",
                request.getRequirementValue(), request.getRequirementValueMax());
        if (request.getRequirementValue() != null
                && CampaignEditRules.boundChanged(difficulty.getRequirementValue(), request.getRequirementValue())) {
            difficulty.setRequirementValue(request.getRequirementValue());
            requirementChanged = true;
        }
        if (request.getRequirementValueMax() != null
                && CampaignEditRules.boundChanged(difficulty.getRequirementValueMax(),
                        request.getRequirementValueMax())) {
            difficulty.setRequirementValueMax(request.getRequirementValueMax());
            requirementChanged = true;
        }
        if (CampaignEditRules.clears(request.getClear(), CampaignBound.VALUE)
                && difficulty.getRequirementValue() != null) {
            difficulty.setRequirementValue(null);
            requirementChanged = true;
        }
        if (CampaignEditRules.clears(request.getClear(), CampaignBound.VALUE_MAX)
                && difficulty.getRequirementValueMax() != null) {
            difficulty.setRequirementValueMax(null);
            requirementChanged = true;
        }
        if (request.getTargetMode() != null && request.getTargetMode() != difficulty.getTargetMode()) {
            difficulty.setTargetMode(request.getTargetMode());
            requirementChanged = true;
        }
        if (request.getTargets() != null) {
            replaceTargets(difficulty, request.getTargets());
            requirementChanged = true;
        } else if (requirementChanged) {
            CampaignEditRules.validateBounds("requirementValue", difficulty.getRequirementType().isLowerBetter(),
                    difficulty.getRequirementValue(), difficulty.getRequirementValueMax());
            replaceTargets(difficulty, effectiveTargets(null, difficulty.getRequirementType(),
                    difficulty.getRequirementValue(), difficulty.getRequirementValueMax()));
        }
        if (request.getModifiers() != null && replaceDifficultyModifiers(difficulty, request.getModifiers())) {
            requirementChanged = true;
        }
        return requirementChanged;
    }

    static List<CampaignTargetRequest> effectiveTargets(List<CampaignTargetRequest> requested,
            CampaignRequirementType type, Double value, Double valueMax) {
        if (requested != null && !requested.isEmpty()) {
            return requested;
        }
        if (type == null) {
            return List.of();
        }
        CampaignTargetRequest legacy = new CampaignTargetRequest();
        legacy.setRequirementType(type);
        legacy.setRequirementValue(value);
        legacy.setRequirementValueMax(valueMax);
        return List.of(legacy);
    }

    List<CampaignDifficultyTarget> replaceTargets(CampaignDifficulty difficulty,
            List<CampaignTargetRequest> requested) {
        if (requested.isEmpty()) {
            throw new ValidationException("targets", "at least one target is required");
        }
        int ordinal = 0;
        List<CampaignDifficultyTarget> targets = new ArrayList<>(requested.size());
        for (CampaignTargetRequest entry : requested) {
            assertRequirementAvailable(entry.getRequirementType(), difficulty.getMapDifficulty());
            CampaignEditRules.validateBounds("targets", entry.getRequirementType().isLowerBetter(),
                    entry.getRequirementValue(), entry.getRequirementValueMax());
            targets.add(CampaignDifficultyTarget.builder()
                    .campaignDifficulty(difficulty)
                    .requirementType(entry.getRequirementType())
                    .requirementValue(entry.getRequirementValue())
                    .requirementValueMax(entry.getRequirementValueMax())
                    .ordinal(ordinal++)
                    .build());
        }
        campaignDifficultyTargetRepository.deleteByCampaignDifficulty_Id(difficulty.getId());
        campaignDifficultyTargetRepository.flush();
        campaignDifficultyTargetRepository.saveAll(targets);
        applyPrimaryTarget(difficulty, targets.get(0));
        return targets;
    }

    private static void applyPrimaryTarget(CampaignDifficulty difficulty, CampaignDifficultyTarget primary) {
        difficulty.setRequirementType(primary.getRequirementType());
        difficulty.setRequirementValue(primary.getRequirementValue());
        difficulty.setRequirementValueMax(primary.getRequirementValueMax());
    }

    static void assertRequirementAvailable(CampaignRequirementType type, MapDifficulty mapDifficulty) {
        if (type != null && RANKED_ONLY_REQUIREMENTS.contains(type)
                && mapDifficulty.getStatus() == MapDifficultyStatus.CAMPAIGN) {
            throw new ValidationException(
                    "AP and RANK requirements are not available for campaign-imported maps");
        }
    }

    boolean replaceDifficultyModifiers(CampaignDifficulty difficulty,
            List<CampaignModifierRequirementRequest> requested) {
        Map<UUID, CampaignModifierRequirement> target = resolveModifierRequirements(requested);
        List<CampaignDifficultyModifier> existing = campaignDifficultyModifierRepository
                .findByCampaignDifficulty_Id(difficulty.getId());
        Map<UUID, CampaignModifierRequirement> current = existing.stream()
                .collect(Collectors.toMap(link -> link.getId().getModifierId(),
                        CampaignDifficultyModifier::getRequirement));
        if (current.equals(target)) {
            return false;
        }

        for (CampaignDifficultyModifier link : existing) {
            if (!target.containsKey(link.getId().getModifierId())) {
                campaignDifficultyModifierRepository.delete(link);
            }
        }
        for (Map.Entry<UUID, CampaignModifierRequirement> entry : target.entrySet()) {
            campaignDifficultyModifierRepository.save(CampaignDifficultyModifier.builder()
                    .id(new CampaignDifficultyModifierId(difficulty.getId(), entry.getKey()))
                    .campaignDifficulty(difficulty)
                    .modifier(modifierRepository.getReferenceById(entry.getKey()))
                    .requirement(entry.getValue())
                    .build());
        }
        return true;
    }

    private Map<UUID, CampaignModifierRequirement> resolveModifierRequirements(
            List<CampaignModifierRequirementRequest> requested) {
        if (requested == null || requested.isEmpty()) {
            return Map.of();
        }
        Map<UUID, CampaignModifierRequirement> target = new HashMap<>(requested.size() * 2);
        for (CampaignModifierRequirementRequest entry : requested) {
            if (target.put(entry.modifierId(), entry.requirement()) != null) {
                throw new ValidationException("modifiers", "a modifier may only be listed once per node");
            }
        }
        long known = modifierRepository.findAllById(target.keySet()).stream()
                .filter(Modifier::isActive)
                .count();
        if (known != target.size()) {
            throw new ValidationException("modifiers", "one or more modifiers do not exist or are inactive");
        }
        return target;
    }
}
