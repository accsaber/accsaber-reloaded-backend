package com.accsaber.backend.service.campaign;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.campaign.CampaignTextRequest;
import com.accsaber.backend.model.dto.request.campaign.MoveCampaignElementsRequest;
import com.accsaber.backend.model.dto.response.campaign.CampaignTextResponse;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignDifficulty;
import com.accsaber.backend.model.entity.campaign.CampaignText;
import com.accsaber.backend.repository.campaign.CampaignDifficultyRepository;
import com.accsaber.backend.repository.campaign.CampaignTextRepository;
import com.accsaber.backend.service.player.RichTextSanitizer;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampaignCanvasService {

    private static final int MAX_TEXTS_PER_CAMPAIGN = 100;
    private static final int MAX_TEXT_CONTENT_LENGTH = 4000;

    private final CampaignDifficultyRepository campaignDifficultyRepository;
    private final CampaignTextRepository campaignTextRepository;
    private final RichTextSanitizer richTextSanitizer;
    private final CampaignAccess access;

    @Transactional
    public CampaignTextResponse addText(CampaignEditor editor, UUID campaignId, CampaignTextRequest request) {
        Campaign campaign = access.editableDraftCampaign(editor, campaignId);
        CampaignEditRules.assertUnderCap(editor,
                () -> campaignTextRepository.countByCampaign_IdAndActiveTrue(campaignId), MAX_TEXTS_PER_CAMPAIGN, "text elements");
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
}
