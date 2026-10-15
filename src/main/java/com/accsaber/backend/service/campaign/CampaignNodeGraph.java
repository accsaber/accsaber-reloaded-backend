package com.accsaber.backend.service.campaign;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.campaign.CampaignConnectionRequest;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignCompletionMode;
import com.accsaber.backend.model.entity.campaign.CampaignDifficulty;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyPath;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.repository.campaign.CampaignBarrierAffectedDifficultyRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyItemRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyModifierRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyPathRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyTargetRepository;
import com.accsaber.backend.repository.campaign.UserCampaignScoreRepository;
import com.accsaber.backend.service.playlist.PlaylistService;
import com.accsaber.backend.service.score.CampaignScoreGate;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class CampaignNodeGraph {

    private final CampaignDifficultyRepository campaignDifficultyRepository;
    private final CampaignDifficultyPathRepository campaignDifficultyPathRepository;
    private final CampaignBarrierAffectedDifficultyRepository barrierAffectedRepository;
    private final CampaignDifficultyItemRepository campaignDifficultyItemRepository;
    private final CampaignDifficultyModifierRepository campaignDifficultyModifierRepository;
    private final CampaignDifficultyTargetRepository campaignDifficultyTargetRepository;
    private final UserCampaignScoreRepository userCampaignScoreRepository;
    private final PlaylistService playlistService;
    private final CampaignScoreGate campaignScoreGate;

    void moveNode(CampaignDifficulty node, Double positionX, Double positionY, String clashMessage) {
        if (positionX == null && positionY == null) {
            return;
        }
        Double newX = positionX != null ? positionX : node.getPositionX();
        Double newY = positionY != null ? positionY : node.getPositionY();
        if ((CampaignEditRules.boundChanged(node.getPositionX(), newX)
                || CampaignEditRules.boundChanged(node.getPositionY(), newY))
                && campaignDifficultyRepository.isPositionTaken(node.getCampaign().getId(), newX, newY)) {
            throw new ValidationException(clashMessage);
        }
        node.setPositionX(newX);
        node.setPositionY(newY);
    }

    void deleteNode(UUID campaignId, CampaignDifficulty difficulty) {
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

    void assertNotLastTerminal(CampaignDifficulty difficulty) {
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

    void createPrerequisitePaths(CampaignDifficulty difficulty, List<CampaignConnectionRequest> prerequisites) {
        if (prerequisites == null || prerequisites.isEmpty()) {
            return;
        }
        Set<UUID> prerequisiteIds = prerequisites.stream()
                .map(CampaignConnectionRequest::getComesFromCampaignDifficultyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, CampaignDifficulty> prerequisitesById = campaignDifficultyRepository.findAllById(prerequisiteIds)
                .stream()
                .filter(CampaignDifficulty::isActive)
                .collect(Collectors.toMap(CampaignDifficulty::getId, d -> d));
        List<CampaignDifficultyPath> paths = new ArrayList<>(prerequisites.size());
        for (CampaignConnectionRequest connection : prerequisites) {
            UUID prereqId = connection.getComesFromCampaignDifficultyId();
            CampaignDifficulty prerequisite = prerequisitesById.get(prereqId);
            if (prerequisite == null) {
                throw new ResourceNotFoundException("CampaignDifficulty (prerequisite)", prereqId);
            }
            if (!prerequisite.getCampaign().getId().equals(difficulty.getCampaign().getId())) {
                throw new ValidationException("Prerequisite must belong to the same campaign");
            }
            if (prerequisite.getId().equals(difficulty.getId())) {
                throw new ValidationException("A difficulty cannot be its own prerequisite");
            }
            paths.add(CampaignDifficultyPath.builder()
                    .campaignDifficulty(difficulty)
                    .comesFromCampaignDifficulty(prerequisite)
                    .color(connection.getColor())
                    .build());
        }
        campaignDifficultyPathRepository.saveAll(paths);
    }

    void replacePrerequisitePaths(CampaignDifficulty difficulty, List<CampaignConnectionRequest> prerequisites) {
        campaignDifficultyPathRepository.deleteAllByCampaignDifficultyId(difficulty.getId());
        campaignDifficultyPathRepository.flush();
        createPrerequisitePaths(difficulty, prerequisites);
    }
}
