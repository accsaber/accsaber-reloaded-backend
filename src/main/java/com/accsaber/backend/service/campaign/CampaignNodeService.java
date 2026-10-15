package com.accsaber.backend.service.campaign;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.campaign.AddCampaignDifficultyRequest;
import com.accsaber.backend.model.dto.request.campaign.CampaignTargetRequest;
import com.accsaber.backend.model.dto.request.campaign.UpdateCampaignDifficultyRequest;
import com.accsaber.backend.model.dto.request.map.ImportCampaignMapRequest;
import com.accsaber.backend.model.dto.response.campaign.CampaignDifficultyResponse;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignDifficulty;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyTarget;
import com.accsaber.backend.model.entity.campaign.CampaignNodeBorderLayer;
import com.accsaber.backend.model.entity.campaign.CampaignPrerequisiteMode;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.campaign.CampaignDifficultyRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.service.map.MapImportService;
import com.accsaber.backend.service.playlist.PlaylistService;
import com.accsaber.backend.service.score.CampaignScoreGate;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampaignNodeService {

    private static final int MAX_DIFFICULTIES_PER_CAMPAIGN = 200;

    private final CampaignDifficultyRepository campaignDifficultyRepository;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final CampaignEvaluationService campaignEvaluationService;
    private final CampaignScoreGate campaignScoreGate;
    private final MapImportService mapImportService;
    private final PlaylistService playlistService;
    private final TransactionTemplate transactionTemplate;
    private final CampaignAccess access;
    private final CampaignMapper mapper;
    private final CampaignNodeRequirements requirements;
    private final CampaignNodeGraph graph;

    @Transactional
    public CampaignDifficultyResponse addDifficulty(CampaignEditor editor, UUID campaignId,
            AddCampaignDifficultyRequest request) {
        Campaign campaign = access.editableDraftCampaign(editor, campaignId);
        CampaignEditRules.assertUnderCap(editor,
                () -> campaignDifficultyRepository.countByCampaign_IdAndBarrierFalseAndActiveTrue(campaignId),
                MAX_DIFFICULTIES_PER_CAMPAIGN, "difficulties");
        CampaignAccess.ensureEditable(campaign);

        MapDifficulty mapDifficulty = mapDifficultyRepository.findByIdAndActiveTrue(request.getMapDifficultyId())
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", request.getMapDifficultyId()));
        List<CampaignTargetRequest> requestedTargets = CampaignNodeRequirements.effectiveTargets(request.getTargets(),
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
        List<CampaignDifficultyTarget> targets = requirements.replaceTargets(difficulty, requestedTargets);
        difficulty = campaignDifficultyRepository.save(difficulty);
        graph.createPrerequisitePaths(difficulty, request.getPrerequisites());
        requirements.replaceDifficultyModifiers(difficulty, request.getModifiers());
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
        CampaignNodeRequirements.assertRequirementAvailable(difficulty.getRequirementType(), target);

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

    @Transactional
    public void removeDifficulty(CampaignEditor editor, UUID campaignId, UUID campaignDifficultyId) {
        access.editableDraftCampaign(editor, campaignId);
        graph.deleteNode(campaignId, access.loadActiveDifficulty(campaignDifficultyId));
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

        boolean requirementChanged = requirements.applyRequirementUpdate(difficulty, request);
        if (requirementChanged && difficulty.getCampaign().getStatus() == CampaignStatus.DRAFT) {
            difficulty.setRequirementDirty(true);
        }
        if (request.getPrerequisiteMode() != null) {
            difficulty.setPrerequisiteMode(request.getPrerequisiteMode());
        }
        boolean terminalChanged = applyTerminalUpdate(difficulty, request.getTerminal());
        applyNodeAppearance(difficulty, request);
        graph.moveNode(difficulty, request.getPositionX(), request.getPositionY(),
                "A difficulty already occupies that grid position");
        if (request.getXp() != null) {
            difficulty.setXp(CampaignEditRules.requireNonNegativeXp(request.getXp()));
        }

        if (request.getPrerequisites() != null) {
            graph.replacePrerequisitePaths(difficulty, request.getPrerequisites());
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

    private boolean applyTerminalUpdate(CampaignDifficulty difficulty, Boolean terminal) {
        if (terminal == null || terminal == difficulty.isTerminal()) {
            return false;
        }
        if (!terminal) {
            graph.assertNotLastTerminal(difficulty);
        }
        difficulty.setTerminal(terminal);
        return true;
    }

    private static void applyNodeAppearance(CampaignDifficulty difficulty, UpdateCampaignDifficultyRequest request) {
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
    }
}
