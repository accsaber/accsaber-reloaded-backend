package com.accsaber.backend.service.campaign;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.campaign.AddCampaignBarrierRequest;
import com.accsaber.backend.model.dto.request.campaign.AddCampaignDifficultyRequest;
import com.accsaber.backend.model.dto.request.campaign.CampaignBound;
import com.accsaber.backend.model.dto.request.campaign.CampaignConnectionRequest;
import com.accsaber.backend.model.dto.request.campaign.CampaignModifierRequirementRequest;
import com.accsaber.backend.model.dto.request.campaign.CampaignTargetRequest;
import com.accsaber.backend.model.dto.request.campaign.CampaignTextRequest;
import com.accsaber.backend.model.dto.request.campaign.MoveCampaignElementsRequest;
import com.accsaber.backend.model.dto.request.campaign.UpdateCampaignBarrierRequest;
import com.accsaber.backend.model.dto.request.campaign.UpdateCampaignDifficultyRequest;
import com.accsaber.backend.model.dto.request.map.ImportCampaignMapRequest;
import com.accsaber.backend.model.dto.response.campaign.CampaignBarrierResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignDifficultyResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignTextResponse;
import com.accsaber.backend.model.entity.Modifier;
import com.accsaber.backend.model.entity.campaign.BarrierConditionType;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignBarrierAffectedDifficulty;
import com.accsaber.backend.model.entity.campaign.CampaignCompletionMode;
import com.accsaber.backend.model.entity.campaign.CampaignDifficulty;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyModifier;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyModifier.CampaignDifficultyModifierId;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyPath;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyTarget;
import com.accsaber.backend.model.entity.campaign.CampaignModifierRequirement;
import com.accsaber.backend.model.entity.campaign.CampaignNodeBorderLayer;
import com.accsaber.backend.model.entity.campaign.CampaignPrerequisiteMode;
import com.accsaber.backend.model.entity.campaign.CampaignRequirementType;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.campaign.CampaignText;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.ModifierRepository;
import com.accsaber.backend.repository.campaign.CampaignBarrierAffectedDifficultyRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyItemRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyModifierRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyPathRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyTargetRepository;
import com.accsaber.backend.repository.campaign.CampaignTextRepository;
import com.accsaber.backend.repository.campaign.UserCampaignScoreRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.service.map.MapImportService;
import com.accsaber.backend.service.player.RichTextSanitizer;
import com.accsaber.backend.service.playlist.PlaylistService;
import com.accsaber.backend.service.score.CampaignScoreGate;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampaignNodeService {

    private static final int MAX_DIFFICULTIES_PER_CAMPAIGN = 200;
    private static final int MAX_BARRIERS_PER_CAMPAIGN = 100;
    private static final int MAX_TEXTS_PER_CAMPAIGN = 100;
    private static final int MAX_TEXT_CONTENT_LENGTH = 4000;

    private static final Set<CampaignRequirementType> RANKED_ONLY_REQUIREMENTS = Set.of(
            CampaignRequirementType.AP, CampaignRequirementType.RANK);

    private static final Set<BarrierConditionType> RANKED_ONLY_BARRIER_CONDITIONS = Set.of(
            BarrierConditionType.AVERAGE_AP, BarrierConditionType.AP_MAX,
            BarrierConditionType.AVERAGE_RANK, BarrierConditionType.MAX_RANK);

    private final CampaignDifficultyRepository campaignDifficultyRepository;
    private final CampaignDifficultyPathRepository campaignDifficultyPathRepository;
    private final CampaignBarrierAffectedDifficultyRepository barrierAffectedRepository;
    private final CampaignTextRepository campaignTextRepository;
    private final CampaignDifficultyItemRepository campaignDifficultyItemRepository;
    private final CampaignDifficultyModifierRepository campaignDifficultyModifierRepository;
    private final CampaignDifficultyTargetRepository campaignDifficultyTargetRepository;
    private final UserCampaignScoreRepository userCampaignScoreRepository;
    private final ModifierRepository modifierRepository;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final RichTextSanitizer richTextSanitizer;
    private final CampaignEvaluationService campaignEvaluationService;
    private final CampaignScoreGate campaignScoreGate;
    private final MapImportService mapImportService;
    private final PlaylistService playlistService;
    private final TransactionTemplate transactionTemplate;
    private final CampaignAccess access;
    private final CampaignMapper mapper;

    @Transactional
    public CampaignDifficultyResponse addDifficulty(CampaignEditor editor, UUID campaignId,
            AddCampaignDifficultyRequest request) {
        Campaign campaign = access.editableDraftCampaign(editor, campaignId);
        assertUnderCap(editor,
                () -> campaignDifficultyRepository.countByCampaign_IdAndBarrierFalseAndActiveTrue(campaignId),
                MAX_DIFFICULTIES_PER_CAMPAIGN, "difficulties");
        CampaignAccess.ensureEditable(campaign);

        MapDifficulty mapDifficulty = mapDifficultyRepository.findByIdAndActiveTrue(request.getMapDifficultyId())
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", request.getMapDifficultyId()));
        List<CampaignTargetRequest> requestedTargets = effectiveTargets(request.getTargets(),
                request.getRequirementType(), request.getRequirementValue(), request.getRequirementValueMax());

        if (campaignDifficultyRepository.isPositionTaken(campaignId, request.getPositionX(), request.getPositionY())) {
            throw new ValidationException("A difficulty already occupies that grid position");
        }

        CampaignDifficulty difficulty = CampaignDifficulty.builder()
                .campaign(campaign)
                .mapDifficulty(mapDifficulty)
                .requirementType(request.getRequirementType())
                .requirementValue(request.getRequirementValue())
                .requirementValueMax(request.getRequirementValueMax())
                .targetMode(request.getTargetMode() != null
                        ? request.getTargetMode()
                        : CampaignPrerequisiteMode.AND)
                .prerequisiteMode(request.getPrerequisiteMode() != null
                        ? request.getPrerequisiteMode()
                        : CampaignPrerequisiteMode.OR)
                .terminal(Boolean.TRUE.equals(request.getTerminal()))
                .description(request.getDescription())
                .checkpointLabel(request.getCheckpointLabel())
                .checkpointLabelPosition(request.getCheckpointLabelPosition())
                .checkpointAvatarUrl(request.getCheckpointAvatarUrl())
                .checkpointColor(request.getCheckpointColor())
                .borderColor(request.getBorderColor())
                .borderShape(request.getBorderShape())
                .nodeBorderUrl(request.getNodeBorderUrl())
                .nodeBorderLayer(request.getNodeBorderLayer() != null
                        ? request.getNodeBorderLayer()
                        : CampaignNodeBorderLayer.ABOVE)
                .size(request.getSize())
                .checkpointSize(request.getCheckpointSize())
                .positionX(request.getPositionX())
                .positionY(request.getPositionY())
                .xp(request.getXp() != null ? request.getXp() : 0.0)
                .build();

        difficulty = campaignDifficultyRepository.save(difficulty);
        List<CampaignDifficultyTarget> targets = replaceTargets(difficulty, requestedTargets);
        difficulty = campaignDifficultyRepository.save(difficulty);
        createPrerequisitePaths(difficulty, request.getPrerequisites());
        replaceDifficultyModifiers(difficulty, request.getModifiers());
        playlistService.evictCampaignPlaylist(campaignId);
        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            campaignScoreGate.refresh();
        }

        return mapper.toNewNodeResponse(difficulty, request.getPrerequisites(), targets);
    }

    @Transactional
    public CampaignDifficultyResponse updateDifficulty(CampaignEditor editor, UUID campaignDifficultyId,
            UpdateCampaignDifficultyRequest request) {
        return applyDifficultyUpdate(access.editableDraftDifficulty(editor, campaignDifficultyId), request);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CampaignDifficultyResponse replaceNodeMedia(CampaignEditor editor, UUID campaignDifficultyId,
            CampaignMediaKind kind, MultipartFile file) {
        if (!kind.node()) {
            throw new IllegalArgumentException(kind + " is not node media");
        }
        String url = file != null ? access.storeMedia(editor, campaignDifficultyId, kind, file) : "";
        UpdateCampaignDifficultyRequest request = new UpdateCampaignDifficultyRequest();
        if (kind == CampaignMediaKind.CHECKPOINT_AVATAR) {
            request.setCheckpointAvatarUrl(url);
        } else {
            request.setNodeBorderUrl(url);
        }
        CampaignDifficultyResponse response = transactionTemplate.execute(status -> applyDifficultyUpdate(
                access.editableDraftDifficulty(editor, campaignDifficultyId), request));
        if (file == null) {
            access.deleteMedia(kind, campaignDifficultyId);
        }
        return response;
    }

    @Transactional
    public CampaignDifficultyResponse updateDifficultyMap(CampaignEditor editor, UUID campaignDifficultyId,
            ImportCampaignMapRequest request) {
        CampaignDifficulty difficulty = access.editableDraftDifficulty(editor, campaignDifficultyId);
        if (difficulty.isBarrier()) {
            throw new ValidationException("Barriers do not reference a map difficulty");
        }
        Campaign campaign = difficulty.getCampaign();
        CampaignAccess.ensureEditable(campaign);

        MapDifficulty current = difficulty.getMapDifficulty();
        MapDifficulty target = mapImportService.resolveCampaignMap(
                editor.privileged() ? null : access.resolveEditorId(editor), request,
                current != null ? current.getId() : null);
        if (current != null && target.getId().equals(current.getId())) {
            return mapper.toNodeResponse(difficulty);
        }
        assertRequirementAvailable(difficulty.getRequirementType(), target);

        difficulty.setMapDifficulty(target);
        if (campaign.getStatus() == CampaignStatus.DRAFT) {
            difficulty.setRequirementDirty(true);
        }
        difficulty = campaignDifficultyRepository.save(difficulty);

        releaseOrphanedCampaignImport(current);
        playlistService.evictCampaignPlaylist(campaign.getId());
        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            campaignEvaluationService.recomputeAfterRequirementChange(campaign, Set.of(difficulty.getId()));
            campaignScoreGate.refresh();
        }
        return mapper.toNodeResponse(difficulty);
    }

    private void releaseOrphanedCampaignImport(MapDifficulty previous) {
        if (previous == null || previous.getStatus() != MapDifficultyStatus.CAMPAIGN) {
            return;
        }
        if (campaignDifficultyRepository.existsByMapDifficulty_IdAndActiveTrue(previous.getId())) {
            return;
        }
        previous.setActive(false);
        mapDifficultyRepository.save(previous);
    }

    private CampaignDifficultyResponse applyDifficultyUpdate(CampaignDifficulty difficulty,
            UpdateCampaignDifficultyRequest request) {
        if (difficulty.isBarrier()) {
            throw new ValidationException("Use the barrier endpoints to edit a barrier");
        }
        CampaignAccess.ensureEditable(difficulty.getCampaign());

        boolean requirementChanged = false;
        boolean terminalChanged = false;
        if (request.getRequirementType() != null
                && request.getRequirementType() != difficulty.getRequirementType()) {
            assertRequirementAvailable(request.getRequirementType(), difficulty.getMapDifficulty());
            difficulty.setRequirementType(request.getRequirementType());
            requirementChanged = true;
        }
        assertNoClearConflict(request.getClear(), "requirementValue",
                request.getRequirementValue(), request.getRequirementValueMax());
        if (request.getRequirementValue() != null
                && boundChanged(difficulty.getRequirementValue(), request.getRequirementValue())) {
            difficulty.setRequirementValue(request.getRequirementValue());
            requirementChanged = true;
        }
        if (request.getRequirementValueMax() != null
                && boundChanged(difficulty.getRequirementValueMax(), request.getRequirementValueMax())) {
            difficulty.setRequirementValueMax(request.getRequirementValueMax());
            requirementChanged = true;
        }
        if (clears(request.getClear(), CampaignBound.VALUE) && difficulty.getRequirementValue() != null) {
            difficulty.setRequirementValue(null);
            requirementChanged = true;
        }
        if (clears(request.getClear(), CampaignBound.VALUE_MAX) && difficulty.getRequirementValueMax() != null) {
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
            validateBounds("requirementValue", difficulty.getRequirementType().isLowerBetter(),
                    difficulty.getRequirementValue(), difficulty.getRequirementValueMax());
            replaceTargets(difficulty, effectiveTargets(null, difficulty.getRequirementType(),
                    difficulty.getRequirementValue(), difficulty.getRequirementValueMax()));
        }
        if (request.getModifiers() != null && replaceDifficultyModifiers(difficulty, request.getModifiers())) {
            requirementChanged = true;
        }
        if (requirementChanged && difficulty.getCampaign().getStatus() == CampaignStatus.DRAFT) {
            difficulty.setRequirementDirty(true);
        }
        if (request.getPrerequisiteMode() != null) {
            difficulty.setPrerequisiteMode(request.getPrerequisiteMode());
        }
        if (request.getTerminal() != null && request.getTerminal() != difficulty.isTerminal()) {
            if (!request.getTerminal()) {
                assertNotLastTerminal(difficulty);
            }
            difficulty.setTerminal(request.getTerminal());
            terminalChanged = true;
        }
        if (request.getDescription() != null) {
            difficulty.setDescription(request.getDescription());
        }
        if (request.getCheckpointLabel() != null) {
            difficulty.setCheckpointLabel(request.getCheckpointLabel());
        }
        if (request.getCheckpointLabelPosition() != null) {
            difficulty.setCheckpointLabelPosition(request.getCheckpointLabelPosition());
        }
        if (request.getCheckpointAvatarUrl() != null) {
            difficulty.setCheckpointAvatarUrl(request.getCheckpointAvatarUrl());
        }
        if (request.getCheckpointColor() != null) {
            difficulty.setCheckpointColor(request.getCheckpointColor());
        }
        if (request.getBorderColor() != null) {
            difficulty.setBorderColor(request.getBorderColor());
        }
        if (request.getBorderShape() != null) {
            difficulty.setBorderShape(request.getBorderShape());
        }
        if (request.getNodeBorderUrl() != null) {
            difficulty.setNodeBorderUrl(request.getNodeBorderUrl().isBlank() ? null : request.getNodeBorderUrl());
        }
        if (request.getNodeBorderLayer() != null) {
            difficulty.setNodeBorderLayer(request.getNodeBorderLayer());
        }
        if (request.getSize() != null) {
            difficulty.setSize(request.getSize());
        }
        if (request.getCheckpointSize() != null) {
            difficulty.setCheckpointSize(request.getCheckpointSize());
        }
        moveNode(difficulty, request.getPositionX(), request.getPositionY(),
                "A difficulty already occupies that grid position");
        if (request.getXp() != null) {
            difficulty.setXp(requireNonNegativeXp(request.getXp()));
        }

        if (request.getPrerequisites() != null) {
            replacePrerequisitePaths(difficulty, request.getPrerequisites());
        }

        difficulty = campaignDifficultyRepository.save(difficulty);
        if (difficulty.getCampaign().getStatus() != CampaignStatus.DRAFT) {
            if (requirementChanged) {
                campaignEvaluationService.recomputeAfterRequirementChange(difficulty.getCampaign(),
                        Set.of(difficulty.getId()));
            }
            if (terminalChanged) {
                campaignEvaluationService.recomputeCampaignCompletion(difficulty.getCampaign().getId());
            }
        }
        return mapper.toNodeResponse(difficulty);
    }

    private void moveNode(CampaignDifficulty node, Double positionX, Double positionY, String clashMessage) {
        if (positionX == null && positionY == null) {
            return;
        }
        Double newX = positionX != null ? positionX : node.getPositionX();
        Double newY = positionY != null ? positionY : node.getPositionY();
        if ((boundChanged(node.getPositionX(), newX) || boundChanged(node.getPositionY(), newY))
                && campaignDifficultyRepository.isPositionTaken(node.getCampaign().getId(), newX, newY)) {
            throw new ValidationException(clashMessage);
        }
        node.setPositionX(newX);
        node.setPositionY(newY);
    }

    private static Double requireNonNegativeXp(Double xp) {
        if (Math.signum(xp) < 0) {
            throw new ValidationException("xp", "must be non-negative");
        }
        return xp;
    }

    @Transactional
    public void removeDifficulty(CampaignEditor editor, UUID campaignId, UUID campaignDifficultyId) {
        access.editableDraftCampaign(editor, campaignId);
        deleteNode(campaignId, access.loadActiveDifficulty(campaignDifficultyId));
    }

    private void deleteNode(UUID campaignId, CampaignDifficulty difficulty) {
        if (!difficulty.getCampaign().getId().equals(campaignId)) {
            throw new ResourceNotFoundException("CampaignDifficulty", difficulty.getId());
        }
        CampaignAccess.ensureEditable(difficulty.getCampaign());
        assertNotLastTerminal(difficulty);
        campaignDifficultyPathRepository.deleteAllTouching(difficulty.getId());
        barrierAffectedRepository.deleteAllTouching(difficulty.getId());
        campaignDifficultyItemRepository.deleteByCampaignDifficulty_Id(difficulty.getId());
        campaignDifficultyTargetRepository.deleteByCampaignDifficulty_Id(difficulty.getId());
        campaignDifficultyModifierRepository.deleteByCampaignDifficulty_Id(difficulty.getId());
        userCampaignScoreRepository.deleteByCampaignDifficulty_Id(difficulty.getId());
        campaignDifficultyRepository.delete(difficulty);
        playlistService.evictCampaignPlaylist(campaignId);
        if (difficulty.getCampaign().getStatus() != CampaignStatus.DRAFT) {
            campaignScoreGate.refresh();
        }
    }

    @Transactional
    public CampaignBarrierResponse addBarrier(CampaignEditor editor, UUID campaignId,
            AddCampaignBarrierRequest request) {
        Campaign campaign = access.editableDraftCampaign(editor, campaignId);
        assertUnderCap(editor,
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
        createPrerequisitePaths(barrier, request.getPrerequisites());
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

        boolean conditionChanged = false;
        if (request.getConditionType() != null && request.getConditionType() != barrier.getBarrierConditionType()) {
            barrier.setBarrierConditionType(request.getConditionType());
            conditionChanged = true;
        }
        if (isValuelessCondition(barrier.getBarrierConditionType())) {
            barrier.setBarrierConditionValue(null);
            barrier.setBarrierConditionValueMax(null);
        } else {
            assertNoClearConflict(request.getClear(), "conditionValue",
                    request.getConditionValue(), request.getConditionValueMax());
            if (request.getConditionValue() != null
                    && boundChanged(barrier.getBarrierConditionValue(), request.getConditionValue())) {
                barrier.setBarrierConditionValue(request.getConditionValue());
                conditionChanged = true;
            }
            if (request.getConditionValueMax() != null
                    && boundChanged(barrier.getBarrierConditionValueMax(), request.getConditionValueMax())) {
                barrier.setBarrierConditionValueMax(request.getConditionValueMax());
                conditionChanged = true;
            }
            if (clears(request.getClear(), CampaignBound.VALUE)
                    && barrier.getBarrierConditionValue() != null) {
                barrier.setBarrierConditionValue(null);
                conditionChanged = true;
            }
            if (clears(request.getClear(), CampaignBound.VALUE_MAX)
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
        validateBarrierCondition(barrier.getBarrierConditionType(), barrier.getBarrierConditionValue(),
                barrier.getBarrierConditionValueMax());
        if (conditionChanged && barrier.getCampaign().getStatus() == CampaignStatus.DRAFT) {
            barrier.setRequirementDirty(true);
        }
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
        moveNode(barrier, request.getPositionX(), request.getPositionY(),
                "A node already occupies that grid position");
        if (request.getXp() != null) {
            barrier.setXp(requireNonNegativeXp(request.getXp()));
        }
        if (request.getPrerequisites() != null) {
            replacePrerequisitePaths(barrier, request.getPrerequisites());
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
        deleteNode(campaignId, loadActiveBarrier(barrierId));
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
        validateBounds("conditionValue", type.isLowerBetter(), value, valueMax);
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

    private static void validateBounds(String field, boolean lowerBetter, Double bound, Double cap) {
        if (bound == null && cap == null) {
            throw new ValidationException(field, "a requirement value is required");
        }
        if (!lowerBetter && bound != null && cap != null && bound.compareTo(cap) > 0) {
            throw new ValidationException(field, "the lower bound cannot exceed the upper bound");
        }
    }

    private static List<CampaignTargetRequest> effectiveTargets(List<CampaignTargetRequest> requested,
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

    private List<CampaignDifficultyTarget> replaceTargets(CampaignDifficulty difficulty,
            List<CampaignTargetRequest> requested) {
        if (requested.isEmpty()) {
            throw new ValidationException("targets", "at least one target is required");
        }
        int ordinal = 0;
        List<CampaignDifficultyTarget> targets = new ArrayList<>(requested.size());
        for (CampaignTargetRequest entry : requested) {
            assertRequirementAvailable(entry.getRequirementType(), difficulty.getMapDifficulty());
            validateBounds("targets", entry.getRequirementType().isLowerBetter(),
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

    private static boolean clears(Set<CampaignBound> clear, CampaignBound bound) {
        return clear != null && clear.contains(bound);
    }

    private static void assertNoClearConflict(Set<CampaignBound> clear, String field,
            Double value, Double valueMax) {
        if (value != null && clears(clear, CampaignBound.VALUE)) {
            throw new ValidationException(field, "cannot be set and cleared in the same request");
        }
        if (valueMax != null && clears(clear, CampaignBound.VALUE_MAX)) {
            throw new ValidationException(field + "Max", "cannot be set and cleared in the same request");
        }
    }

    private static boolean boundChanged(Double current, Double replacement) {
        if (current == null || replacement == null) {
            return current != replacement;
        }
        return current.compareTo(replacement) != 0;
    }

    private void validateCompletionTarget(BarrierConditionType type, Double value, int affectedCount) {
        if (type != BarrierConditionType.COMPLETION_COUNT || affectedCount == 0) {
            return;
        }
        if (value.compareTo((double) (affectedCount)) > 0) {
            throw new ValidationException("conditionValue", "cannot exceed the number of affected nodes");
        }
    }

    private void assertRequirementAvailable(CampaignRequirementType type, MapDifficulty mapDifficulty) {
        if (type != null && RANKED_ONLY_REQUIREMENTS.contains(type)
                && mapDifficulty.getStatus() == MapDifficultyStatus.CAMPAIGN) {
            throw new ValidationException(
                    "AP and RANK requirements are not available for campaign-imported maps");
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

    @Transactional
    public CampaignTextResponse addText(CampaignEditor editor, UUID campaignId, CampaignTextRequest request) {
        Campaign campaign = access.editableDraftCampaign(editor, campaignId);
        assertUnderCap(editor, () -> campaignTextRepository.countByCampaign_IdAndActiveTrue(campaignId),
                MAX_TEXTS_PER_CAMPAIGN, "text elements");
        CampaignAccess.ensureEditable(campaign);
        CampaignText text = CampaignText.builder()
                .campaign(campaign)
                .content(sanitizeTextContent(request.getContent()))
                .positionX(request.getPositionX())
                .positionY(request.getPositionY())
                .font(request.getFont())
                .scale(request.getScale())
                .color(request.getColor())
                .effects(request.getEffects())
                .build();
        return CampaignMapper.toTextResponse(campaignTextRepository.save(text));
    }

    @Transactional
    public CampaignTextResponse updateText(CampaignEditor editor, UUID textId, CampaignTextRequest request) {
        CampaignText text = loadActiveText(textId);
        access.assertCanEditDraft(text.getCampaign(), editor);
        CampaignAccess.ensureEditable(text.getCampaign());
        if (request.getContent() != null) {
            text.setContent(sanitizeTextContent(request.getContent()));
        }
        if (request.getPositionX() != null) {
            text.setPositionX(request.getPositionX());
        }
        if (request.getPositionY() != null) {
            text.setPositionY(request.getPositionY());
        }
        if (request.getFont() != null) {
            text.setFont(request.getFont());
        }
        if (request.getScale() != null) {
            text.setScale(request.getScale());
        }
        if (request.getColor() != null) {
            text.setColor(request.getColor());
        }
        if (request.getEffects() != null) {
            text.setEffects(request.getEffects());
        }
        return CampaignMapper.toTextResponse(campaignTextRepository.save(text));
    }

    @Transactional
    public void removeText(CampaignEditor editor, UUID campaignId, UUID textId) {
        access.editableDraftCampaign(editor, campaignId);
        CampaignText text = loadActiveText(textId);
        if (!text.getCampaign().getId().equals(campaignId)) {
            throw new ResourceNotFoundException("CampaignText", textId);
        }
        CampaignAccess.ensureEditable(text.getCampaign());
        campaignTextRepository.delete(text);
    }

    private CampaignText loadActiveText(UUID id) {
        return campaignTextRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("CampaignText", id));
    }

    private String sanitizeTextContent(String content) {
        return content == null ? "" : richTextSanitizer.sanitize(content, MAX_TEXT_CONTENT_LENGTH);
    }

    @Transactional
    public void moveElements(CampaignEditor editor, UUID campaignId, MoveCampaignElementsRequest request) {
        CampaignAccess.ensureEditable(access.editableDraftCampaign(editor, campaignId));

        List<CampaignDifficulty> difficulties = campaignDifficultyRepository
                .findByCampaign_IdAndActiveTrue(campaignId);
        Map<UUID, CampaignDifficulty> difficultyById = difficulties.stream()
                .collect(Collectors.toMap(CampaignDifficulty::getId, d -> d));
        Map<UUID, CampaignText> textById = campaignTextRepository.findByCampaign_IdAndActiveTrue(campaignId).stream()
                .collect(Collectors.toMap(CampaignText::getId, t -> t));

        List<CampaignDifficulty> movedDifficulties = new ArrayList<>();
        List<CampaignText> movedTexts = new ArrayList<>();
        for (MoveCampaignElementsRequest.Move move : request.getMoves()) {
            CampaignDifficulty difficulty = difficultyById.get(move.getId());
            if (difficulty != null) {
                difficulty.setPositionX(move.getPositionX());
                difficulty.setPositionY(move.getPositionY());
                movedDifficulties.add(difficulty);
                continue;
            }
            CampaignText text = textById.get(move.getId());
            if (text == null) {
                throw new ResourceNotFoundException("CampaignElement", move.getId());
            }
            text.setPositionX(move.getPositionX());
            text.setPositionY(move.getPositionY());
            movedTexts.add(text);
        }

        assertNoGridClash(difficulties);
        campaignDifficultyRepository.saveAll(movedDifficulties);
        campaignTextRepository.saveAll(movedTexts);
    }

    private static void assertNoGridClash(List<CampaignDifficulty> difficulties) {
        Set<String> cells = HashSet.newHashSet(difficulties.size());
        for (CampaignDifficulty difficulty : difficulties) {
            String cell = difficulty.getPositionX()
                    + "," + difficulty.getPositionY();
            if (!cells.add(cell)) {
                throw new ValidationException("A difficulty already occupies that grid position");
            }
        }
    }

    private void assertUnderCap(CampaignEditor editor, LongSupplier current, int cap, String what) {
        if (editor.privileged()) {
            return;
        }
        if (current.getAsLong() >= cap) {
            throw new ValidationException("Campaign has reached the maximum of " + cap + " " + what);
        }
    }

    private void assertNotLastTerminal(CampaignDifficulty difficulty) {
        Campaign campaign = difficulty.getCampaign();
        if (!difficulty.isTerminal() || campaign.getStatus() == CampaignStatus.DRAFT
                || campaign.getCompletionMode() == CampaignCompletionMode.ALL) {
            return;
        }
        boolean anotherTerminal = campaignDifficultyRepository
                .findByCampaign_IdAndActiveTrue(campaign.getId()).stream()
                .anyMatch(d -> d.isTerminal() && !d.isBarrier() && !d.getId().equals(difficulty.getId()));
        if (!anotherTerminal) {
            throw new ValidationException(
                    "This is the only terminal node, removing it would leave the campaign impossible to finish");
        }
    }

    private void createPrerequisitePaths(CampaignDifficulty difficulty, List<CampaignConnectionRequest> prerequisites) {
        if (prerequisites == null || prerequisites.isEmpty()) {
            return;
        }
        for (CampaignConnectionRequest connection : prerequisites) {
            UUID prereqId = connection.getComesFromCampaignDifficultyId();
            CampaignDifficulty prerequisite = campaignDifficultyRepository.findByIdAndActiveTrue(prereqId)
                    .orElseThrow(() -> new ResourceNotFoundException("CampaignDifficulty (prerequisite)", prereqId));
            if (!prerequisite.getCampaign().getId().equals(difficulty.getCampaign().getId())) {
                throw new ValidationException("Prerequisite must belong to the same campaign");
            }
            if (prerequisite.getId().equals(difficulty.getId())) {
                throw new ValidationException("A difficulty cannot be its own prerequisite");
            }
            CampaignDifficultyPath path = CampaignDifficultyPath.builder()
                    .campaignDifficulty(difficulty)
                    .comesFromCampaignDifficulty(prerequisite)
                    .color(connection.getColor())
                    .build();
            campaignDifficultyPathRepository.save(path);
        }
    }

    private void replacePrerequisitePaths(CampaignDifficulty difficulty,
            List<CampaignConnectionRequest> prerequisites) {
        campaignDifficultyPathRepository.deleteAllByCampaignDifficultyId(difficulty.getId());
        campaignDifficultyPathRepository.flush();
        createPrerequisitePaths(difficulty, prerequisites);
    }

    private boolean replaceDifficultyModifiers(CampaignDifficulty difficulty,
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

    private static List<UUID> safeIds(List<UUID> ids) {
        return ids == null ? List.of() : ids;
    }
}
