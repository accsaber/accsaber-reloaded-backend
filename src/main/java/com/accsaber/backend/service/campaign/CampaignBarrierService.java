package com.accsaber.backend.service.campaign;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.campaign.AddCampaignBarrierRequest;
import com.accsaber.backend.model.dto.request.campaign.CampaignBound;
import com.accsaber.backend.model.dto.request.campaign.UpdateCampaignBarrierRequest;
import com.accsaber.backend.model.dto.response.campaign.CampaignBarrierResponse;
import com.accsaber.backend.model.entity.campaign.BarrierConditionType;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignBarrierAffectedDifficulty;
import com.accsaber.backend.model.entity.campaign.CampaignDifficulty;
import com.accsaber.backend.model.entity.campaign.CampaignPrerequisiteMode;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.campaign.CampaignBarrierAffectedDifficultyRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampaignBarrierService {

    private static final int MAX_BARRIERS_PER_CAMPAIGN = 100;

    private static final Set<BarrierConditionType> RANKED_ONLY_BARRIER_CONDITIONS = Set.of(
            BarrierConditionType.AVERAGE_AP, BarrierConditionType.AP_MAX,
            BarrierConditionType.AVERAGE_RANK, BarrierConditionType.MAX_RANK);

    private final CampaignDifficultyRepository campaignDifficultyRepository;
    private final CampaignBarrierAffectedDifficultyRepository barrierAffectedRepository;
    private final CampaignEvaluationService campaignEvaluationService;
    private final CampaignAccess access;
    private final CampaignMapper mapper;
    private final CampaignNodeGraph graph;

    @Transactional
    public CampaignBarrierResponse addBarrier(CampaignEditor editor, UUID campaignId,
            AddCampaignBarrierRequest request) {
        Campaign campaign = access.editableDraftCampaign(editor, campaignId);
        CampaignEditRules.assertUnderCap(editor,
                () -> campaignDifficultyRepository.countByCampaign_IdAndBarrierTrueAndActiveTrue(campaignId),
                MAX_BARRIERS_PER_CAMPAIGN, "barriers");
        CampaignAccess.ensureEditable(campaign);
        if (campaign.isProgressionAgnostic()) {
            throw new ValidationException("Barriers cannot be added to a progression-agnostic campaign");
        }
        boolean valueless = isValuelessCondition(request.getConditionType());
        Double conditionValue = valueless ? null : request.getConditionValue();
        Double conditionValueMax = valueless ? null : request.getConditionValueMax();
        validateBarrierCondition(request.getConditionType(), conditionValue, conditionValueMax);
        validateCompletionTarget(request.getConditionType(), conditionValue,
                (int) safeIds(request.getAffectedCampaignDifficultyIds()).stream().distinct().count());
        if (campaignDifficultyRepository.isPositionTaken(campaignId, request.getPositionX(), request.getPositionY())) {
            throw new ValidationException("A node already occupies that grid position");
        }
        CampaignDifficulty barrier = CampaignDifficulty.builder()
                .campaign(campaign)
                .barrier(true)
                .barrierConditionType(request.getConditionType())
                .barrierConditionValue(conditionValue)
                .barrierConditionValueMax(conditionValueMax)
                .prerequisiteMode(request.getPrerequisiteMode() != null
                        ? request.getPrerequisiteMode()
                        : CampaignPrerequisiteMode.AND)
                .description(request.getDescription())
                .checkpointLabel(request.getCheckpointLabel())
                .checkpointLabelPosition(request.getCheckpointLabelPosition())
                .checkpointAvatarUrl(request.getCheckpointAvatarUrl())
                .checkpointColor(request.getCheckpointColor())
                .borderColor(request.getBorderColor())
                .borderShape(request.getBorderShape())
                .size(request.getSize())
                .checkpointSize(request.getCheckpointSize())
                .positionX(request.getPositionX())
                .positionY(request.getPositionY())
                .xp(request.getXp() != null ? request.getXp() : 0.0)
                .build();
        barrier = campaignDifficultyRepository.save(barrier);
        graph.createPrerequisitePaths(barrier, request.getPrerequisites());
        replaceAffectedNodes(barrier, request.getAffectedCampaignDifficultyIds());
        return CampaignMapper.toBarrierResponse(barrier,
                CampaignMapper.echoConnections(request.getPrerequisites()),
                safeIds(request.getAffectedCampaignDifficultyIds()),
                List.of());
    }

    @Transactional
    public CampaignBarrierResponse updateBarrier(CampaignEditor editor, UUID barrierId,
            UpdateCampaignBarrierRequest request) {
        CampaignDifficulty barrier = loadActiveBarrier(barrierId);
        access.assertCanEditDraft(barrier.getCampaign(), editor);
        CampaignAccess.ensureEditable(barrier.getCampaign());

        boolean conditionChanged = applyBarrierConditionUpdate(barrier, request);
        validateBarrierCondition(barrier.getBarrierConditionType(), barrier.getBarrierConditionValue(),
                barrier.getBarrierConditionValueMax());
        if (conditionChanged && barrier.getCampaign().getStatus() == CampaignStatus.DRAFT) {
            barrier.setRequirementDirty(true);
        }
        applyBarrierAppearance(barrier, request);
        graph.moveNode(barrier, request.getPositionX(), request.getPositionY(),
                "A node already occupies that grid position");
        if (request.getXp() != null) {
            barrier.setXp(CampaignEditRules.requireNonNegativeXp(request.getXp()));
        }
        if (request.getPrerequisites() != null) {
            graph.replacePrerequisitePaths(barrier, request.getPrerequisites());
        }
        if (request.getAffectedCampaignDifficultyIds() != null) {
            replaceAffectedNodes(barrier, request.getAffectedCampaignDifficultyIds());
        }
        List<UUID> affectedIds = loadBarrierAffectedIds(barrier.getId());
        validateCompletionTarget(barrier.getBarrierConditionType(), barrier.getBarrierConditionValue(),
                affectedIds.size());
        assertBarrierMetricAvailable(barrier.getBarrierConditionType(), affectedIds);

        barrier = campaignDifficultyRepository.save(barrier);
        if (conditionChanged && barrier.getCampaign().getStatus() != CampaignStatus.DRAFT) {
            campaignEvaluationService.recomputeAfterRequirementChange(barrier.getCampaign(),
                    Set.of(barrier.getId()));
        }
        return CampaignMapper.toBarrierResponse(barrier, mapper.loadConnections(barrier.getId()), affectedIds,
                mapper.loadDifficultyItems(barrier.getId()));
    }

    @Transactional
    public void removeBarrier(CampaignEditor editor, UUID campaignId, UUID barrierId) {
        access.editableDraftCampaign(editor, campaignId);
        graph.deleteNode(campaignId, loadActiveBarrier(barrierId));
    }

    private boolean applyBarrierConditionUpdate(CampaignDifficulty barrier, UpdateCampaignBarrierRequest request) {
        boolean conditionChanged = false;
        if (request.getConditionType() != null && request.getConditionType() != barrier.getBarrierConditionType()) {
            barrier.setBarrierConditionType(request.getConditionType());
            conditionChanged = true;
        }
        if (isValuelessCondition(barrier.getBarrierConditionType())) {
            barrier.setBarrierConditionValue(null);
            barrier.setBarrierConditionValueMax(null);
        } else {
            CampaignEditRules.assertNoClearConflict(request.getClear(), "conditionValue",
                    request.getConditionValue(), request.getConditionValueMax());
            if (request.getConditionValue() != null
                    && CampaignEditRules.boundChanged(barrier.getBarrierConditionValue(),
                            request.getConditionValue())) {
                barrier.setBarrierConditionValue(request.getConditionValue());
                conditionChanged = true;
            }
            if (request.getConditionValueMax() != null
                    && CampaignEditRules.boundChanged(barrier.getBarrierConditionValueMax(),
                            request.getConditionValueMax())) {
                barrier.setBarrierConditionValueMax(request.getConditionValueMax());
                conditionChanged = true;
            }
            if (CampaignEditRules.clears(request.getClear(), CampaignBound.VALUE)
                    && barrier.getBarrierConditionValue() != null) {
                barrier.setBarrierConditionValue(null);
                conditionChanged = true;
            }
            if (CampaignEditRules.clears(request.getClear(), CampaignBound.VALUE_MAX)
                    && barrier.getBarrierConditionValueMax() != null) {
                barrier.setBarrierConditionValueMax(null);
                conditionChanged = true;
            }
        }
        if (request.getPrerequisiteMode() != null
                && request.getPrerequisiteMode() != barrier.getPrerequisiteMode()) {
            barrier.setPrerequisiteMode(request.getPrerequisiteMode());
            conditionChanged = true;
        }
        return conditionChanged;
    }

    private static void applyBarrierAppearance(CampaignDifficulty barrier, UpdateCampaignBarrierRequest request) {
        if (request.getDescription() != null) {
            barrier.setDescription(request.getDescription());
        }
        if (request.getCheckpointLabel() != null) {
            barrier.setCheckpointLabel(request.getCheckpointLabel());
        }
        if (request.getCheckpointLabelPosition() != null) {
            barrier.setCheckpointLabelPosition(request.getCheckpointLabelPosition());
        }
        if (request.getCheckpointAvatarUrl() != null) {
            barrier.setCheckpointAvatarUrl(request.getCheckpointAvatarUrl());
        }
        if (request.getCheckpointColor() != null) {
            barrier.setCheckpointColor(request.getCheckpointColor());
        }
        if (request.getBorderColor() != null) {
            barrier.setBorderColor(request.getBorderColor());
        }
        if (request.getBorderShape() != null) {
            barrier.setBorderShape(request.getBorderShape());
        }
        if (request.getSize() != null) {
            barrier.setSize(request.getSize());
        }
        if (request.getCheckpointSize() != null) {
            barrier.setCheckpointSize(request.getCheckpointSize());
        }
    }

    private static boolean isValuelessCondition(BarrierConditionType type) {
        return type == BarrierConditionType.FC || type == BarrierConditionType.PASS;
    }

    private void validateBarrierCondition(BarrierConditionType type, Double value, Double valueMax) {
        if (type == null) {
            throw new ValidationException("conditionType", "is required");
        }
        if (isValuelessCondition(type)) {
            return;
        }
        CampaignEditRules.validateBounds("conditionValue", type.isLowerBetter(), value, valueMax);
        if (type == BarrierConditionType.COMPLETION_COUNT) {
            if (value == null) {
                throw new ValidationException("conditionValue", "is required for this condition");
            }

            if (value != Math.floor(value)) {
                throw new ValidationException("conditionValue", "must be a whole number of maps");
            }
            if (value.compareTo(1.0) < 0) {
                throw new ValidationException("conditionValue", "must be at least 1");
            }
        }
    }

    private void validateCompletionTarget(BarrierConditionType type, Double value, int affectedCount) {
        if (type != BarrierConditionType.COMPLETION_COUNT || affectedCount == 0) {
            return;
        }
        if (value.compareTo((double) (affectedCount)) > 0) {
            throw new ValidationException("conditionValue", "cannot exceed the number of affected nodes");
        }
    }

    private void assertBarrierMetricAvailable(BarrierConditionType type, CampaignDifficulty node) {
        if (type != null && RANKED_ONLY_BARRIER_CONDITIONS.contains(type)
                && node.getMapDifficulty() != null
                && node.getMapDifficulty().getStatus() == MapDifficultyStatus.CAMPAIGN) {
            throw new ValidationException(
                    "AP and RANK barrier conditions are not available for campaign-imported maps");
        }
    }

    private void assertBarrierMetricAvailable(BarrierConditionType type, List<UUID> affectedIds) {
        if (type != null && RANKED_ONLY_BARRIER_CONDITIONS.contains(type) && !affectedIds.isEmpty()
                && campaignDifficultyRepository.existsByIdInAndMapDifficulty_StatusAndActiveTrue(
                        affectedIds, MapDifficultyStatus.CAMPAIGN)) {
            throw new ValidationException(
                    "AP and RANK barrier conditions are not available for campaign-imported maps");
        }
    }

    private void replaceAffectedNodes(CampaignDifficulty barrier, List<UUID> affectedIds) {
        barrierAffectedRepository.deleteByBarrier_Id(barrier.getId());
        barrierAffectedRepository.flush();
        if (affectedIds == null || affectedIds.isEmpty()) {
            return;
        }
        Set<UUID> seen = new HashSet<>();
        for (UUID nodeId : affectedIds) {
            if (!seen.add(nodeId)) {
                continue;
            }
            if (nodeId.equals(barrier.getId())) {
                throw new ValidationException("A barrier cannot affect itself");
            }
            CampaignDifficulty node = campaignDifficultyRepository.findByIdAndActiveTrue(nodeId)
                    .orElseThrow(() -> new ResourceNotFoundException("CampaignDifficulty (affected)", nodeId));
            if (!node.getCampaign().getId().equals(barrier.getCampaign().getId())) {
                throw new ValidationException("Affected node must belong to the same campaign");
            }
            if (node.isBarrier()) {
                throw new ValidationException("A barrier cannot affect another barrier");
            }
            assertBarrierMetricAvailable(barrier.getBarrierConditionType(), node);
            CampaignBarrierAffectedDifficulty link = CampaignBarrierAffectedDifficulty.builder()
                    .id(new CampaignBarrierAffectedDifficulty.CampaignBarrierAffectedDifficultyId(
                            barrier.getId(), nodeId))
                    .barrier(barrier)
                    .affectedDifficulty(node)
                    .build();
            barrierAffectedRepository.save(link);
        }
    }

    private CampaignDifficulty loadActiveBarrier(UUID id) {
        CampaignDifficulty difficulty = access.loadActiveDifficulty(id);
        if (!difficulty.isBarrier()) {
            throw new ResourceNotFoundException("CampaignBarrier", id);
        }
        return difficulty;
    }

    private List<UUID> loadBarrierAffectedIds(UUID barrierId) {
        return barrierAffectedRepository.findByBarrier_Id(barrierId).stream()
                .map(a -> a.getAffectedDifficulty().getId())
                .toList();
    }

    private static List<UUID> safeIds(List<UUID> ids) {
        return ids == null ? List.of() : ids;
    }
}
