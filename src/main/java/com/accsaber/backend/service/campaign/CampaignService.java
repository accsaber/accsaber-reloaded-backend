package com.accsaber.backend.service.campaign;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.config.CdnProperties;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.projection.UserMapDifficultyBests;
import com.accsaber.backend.model.dto.request.campaign.AddCampaignBarrierRequest;
import com.accsaber.backend.model.dto.request.campaign.AddCampaignDifficultyRequest;
import com.accsaber.backend.model.dto.request.campaign.CampaignTextRequest;
import com.accsaber.backend.model.dto.request.campaign.CreateCampaignRequest;
import com.accsaber.backend.model.dto.request.campaign.CreateCampaignTagRequest;
import com.accsaber.backend.model.dto.request.campaign.SetCampaignItemRequest;
import com.accsaber.backend.model.dto.request.campaign.UpdateCampaignBarrierRequest;
import com.accsaber.backend.model.dto.request.campaign.UpdateCampaignDifficultyRequest;
import com.accsaber.backend.model.dto.request.campaign.UpdateCampaignRequest;
import com.accsaber.backend.model.dto.response.campaign.BarrierProgressResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignBarrierResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignDetailResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignDifficultyProgressResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignDifficultyResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignItemAwardResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignProgressResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignTagResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignTextResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignVoteResponse;
import com.accsaber.backend.model.dto.response.campaign.UserCampaignResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.campaign.BarrierConditionType;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignBarrierAffectedDifficulty;
import com.accsaber.backend.model.entity.campaign.CampaignCollaboratorStatus;
import com.accsaber.backend.model.entity.campaign.CampaignCompletionItem;
import com.accsaber.backend.model.entity.campaign.CampaignCompletionMode;
import com.accsaber.backend.model.entity.campaign.CampaignDifficulty;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyItem;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyPath;
import com.accsaber.backend.model.entity.campaign.CampaignPrerequisiteMode;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.campaign.CampaignTag;
import com.accsaber.backend.model.entity.campaign.CampaignTagKind;
import com.accsaber.backend.model.entity.campaign.CampaignTagLink;
import com.accsaber.backend.model.entity.campaign.CampaignText;
import com.accsaber.backend.model.entity.campaign.CampaignVote;
import com.accsaber.backend.model.entity.campaign.CampaignVoteDirection;
import com.accsaber.backend.model.entity.campaign.UserCampaign;
import com.accsaber.backend.model.entity.campaign.UserCampaignScore;
import com.accsaber.backend.model.entity.campaign.UserCampaignStatus;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.staff.StaffRole;
import com.accsaber.backend.model.entity.staff.StaffUser;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.campaign.CampaignBarrierAffectedDifficultyRepository;
import com.accsaber.backend.repository.campaign.CampaignCollaboratorRepository;
import com.accsaber.backend.repository.campaign.CampaignCompletionItemRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyItemRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyPathRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyRepository;
import com.accsaber.backend.repository.campaign.CampaignRepository;
import com.accsaber.backend.repository.campaign.CampaignTagLinkRepository;
import com.accsaber.backend.repository.campaign.CampaignTagRepository;
import com.accsaber.backend.repository.campaign.CampaignTextRepository;
import com.accsaber.backend.repository.campaign.CampaignVoteRepository;
import com.accsaber.backend.repository.campaign.UserCampaignRepository;
import com.accsaber.backend.repository.campaign.UserCampaignScoreRepository;
import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.player.RichTextSanitizer;
import com.accsaber.backend.service.playlist.PlaylistService;
import com.accsaber.backend.util.CampaignScoreMetrics;
import com.accsaber.backend.util.WilsonScore;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampaignService {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final int MAX_PROGRESS_BULK_IDS = 50;
    private static final int MAX_DIFFICULTIES_PER_CAMPAIGN = 100;
    private static final int MAX_BARRIERS_PER_CAMPAIGN = 50;
    private static final int MAX_TEXTS_PER_CAMPAIGN = 50;
    private static final int MAX_TEXT_CONTENT_LENGTH = 4000;

    private final CampaignRepository campaignRepository;
    private final CampaignCollaboratorRepository campaignCollaboratorRepository;
    private final CampaignDifficultyRepository campaignDifficultyRepository;
    private final CampaignDifficultyPathRepository campaignDifficultyPathRepository;
    private final CampaignBarrierAffectedDifficultyRepository barrierAffectedRepository;
    private final CampaignTextRepository campaignTextRepository;
    private final RichTextSanitizer richTextSanitizer;
    private final CampaignDifficultyItemRepository campaignDifficultyItemRepository;
    private final CampaignCompletionItemRepository campaignCompletionItemRepository;
    private final CampaignTagRepository campaignTagRepository;
    private final CampaignTagLinkRepository campaignTagLinkRepository;
    private final UserCampaignRepository userCampaignRepository;
    private final UserCampaignScoreRepository userCampaignScoreRepository;
    private final CampaignVoteRepository campaignVoteRepository;
    private final UserRepository userRepository;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final ScoreRepository scoreRepository;
    private final ItemRepository itemRepository;
    private final CategoryRepository categoryRepository;
    private final DuplicateUserService duplicateUserService;
    private final CampaignEvaluationService campaignEvaluationService;
    private final PlaylistService playlistService;
    private final CdnProperties cdnProperties;

    public Page<CampaignResponse> findCampaigns(Collection<CampaignStatus> statuses,
            Collection<UUID> tagIds,
            Long creatorId,
            String search,
            Boolean official,
            Long viewerId,
            boolean privileged,
            Pageable pageable) {
        boolean hasStatus = statuses != null && !statuses.isEmpty();
        boolean hasTags = tagIds != null && !tagIds.isEmpty();
        Collection<CampaignStatus> statusArg = hasStatus ? statuses : List.of(CampaignStatus.DRAFT);
        Collection<UUID> tagArg = hasTags ? tagIds : List.of(new UUID(0L, 0L));
        String searchArg = search != null && !search.isBlank() ? search.trim() : null;
        Long resolvedViewerId = viewerId != null ? duplicateUserService.resolvePrimaryUserId(viewerId) : null;
        return paginateAsResponses(
                campaignRepository.findFiltered(hasStatus, statusArg, creatorId, hasTags, tagArg,
                        CampaignStatus.DRAFT, resolvedViewerId, privileged,
                        CampaignCollaboratorStatus.ACCEPTED, searchArg, official, pageable),
                resolvedViewerId);
    }

    public Page<CampaignResponse> findCurationQueue(Pageable pageable) {
        return paginateAsResponses(campaignRepository.findByActiveTrueAndSeekingCurationTrue(pageable), null);
    }

    private Page<CampaignResponse> paginateAsResponses(Page<Campaign> page, Long resolvedViewerId) {
        if (!page.hasContent()) {
            return page.map(c -> toCampaignResponse(c, List.of(), 0));
        }
        List<UUID> ids = page.getContent().stream().map(Campaign::getId).distinct().toList();
        Map<UUID, List<CampaignTagResponse>> tagsByCampaign = loadTagsByCampaignIds(ids);
        Map<UUID, Integer> diffCountByCampaign = countMap(campaignDifficultyRepository.countActiveByCampaignIds(ids));
        Map<UUID, CampaignVoteDirection> votesByCampaign = loadViewerVotes(resolvedViewerId, ids);
        return page.map(c -> toCampaignResponse(c,
                tagsByCampaign.getOrDefault(c.getId(), List.of()),
                diffCountByCampaign.getOrDefault(c.getId(), 0),
                votesByCampaign.get(c.getId())));
    }

    private Map<UUID, CampaignVoteDirection> loadViewerVotes(Long resolvedViewerId, Collection<UUID> campaignIds) {
        if (resolvedViewerId == null || campaignIds.isEmpty()) {
            return Map.of();
        }
        return campaignVoteRepository.findByUser_IdAndCampaign_IdIn(resolvedViewerId, campaignIds).stream()
                .collect(Collectors.toMap(v -> v.getCampaign().getId(), CampaignVote::getVote));
    }

    private CampaignVoteDirection viewerVoteFor(UUID campaignId, Long viewerId) {
        if (viewerId == null) {
            return null;
        }
        Long resolvedViewerId = duplicateUserService.resolvePrimaryUserId(viewerId);
        return campaignVoteRepository.findByCampaign_IdAndUser_Id(campaignId, resolvedViewerId)
                .map(CampaignVote::getVote)
                .orElse(null);
    }

    public CampaignDetailResponse findCampaignById(UUID campaignId, Long viewerId, boolean privileged) {
        Campaign campaign = loadActiveCampaign(campaignId);
        if (isDraftHiddenFrom(campaign, viewerId, privileged)) {
            throw new ResourceNotFoundException("Campaign", campaignId);
        }
        return buildDetailResponse(campaign, viewerId);
    }

    public CampaignDetailResponse findCampaignBySlug(String slug, Long viewerId, boolean privileged) {
        Campaign campaign = campaignRepository.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", slug));
        if (isDraftHiddenFrom(campaign, viewerId, privileged)) {
            throw new ResourceNotFoundException("Campaign", slug);
        }
        return buildDetailResponse(campaign, viewerId);
    }

    private boolean isDraftHiddenFrom(Campaign campaign, Long viewerId, boolean privileged) {
        if (privileged || campaign.getStatus() != CampaignStatus.DRAFT) {
            return false;
        }
        if (viewerId == null || campaign.getCreator() == null) {
            return true;
        }
        Long resolvedViewerId = duplicateUserService.resolvePrimaryUserId(viewerId);
        if (resolvedViewerId.equals(campaign.getCreator().getId())) {
            return false;
        }
        return !isAcceptedCollaborator(campaign.getId(), resolvedViewerId);
    }

    @Transactional
    public CampaignResponse createCampaign(CreateCampaignRequest request) {
        User creator = null;
        if (request.getCreatorId() != null) {
            creator = userRepository.findByIdAndActiveTrue(request.getCreatorId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", request.getCreatorId()));
        }
        if (creator == null && (request.getCreatorAlias() == null || request.getCreatorAlias().isBlank())) {
            throw new ValidationException("creatorId or creatorAlias is required");
        }

        String slug = normalizeAndValidateSlug(request.getSlug(), request.getName(), null);

        Campaign campaign = Campaign.builder()
                .creator(creator)
                .creatorAlias(request.getCreatorAlias())
                .name(request.getName())
                .slug(slug)
                .summary(request.getSummary())
                .description(request.getDescription())
                .progressionAgnostic(Boolean.TRUE.equals(request.getProgressionAgnostic()))
                .completionMode(request.getCompletionMode() != null
                        ? request.getCompletionMode()
                        : CampaignCompletionMode.TERMINAL)
                .playlistExportEnabled(request.getPlaylistExportEnabled() == null
                        || request.getPlaylistExportEnabled())
                .backgroundUrl(request.getBackgroundUrl())
                .backgroundColor(request.getBackgroundColor())
                .iconUrl(request.getIconUrl())
                .build();

        campaign = campaignRepository.save(campaign);

        if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
            replaceTagLinks(campaign, request.getTagIds());
        }

        return toCampaignResponse(campaign);
    }

    @Transactional
    public CampaignResponse updateCampaign(UUID campaignId, UpdateCampaignRequest request) {
        Campaign campaign = loadActiveCampaign(campaignId);
        ensureEditable(campaign);

        if (request.getName() != null) {
            campaign.setName(request.getName());
        }
        if (request.getSlug() != null) {
            campaign.setSlug(normalizeAndValidateSlug(request.getSlug(), campaign.getName(), campaign.getId()));
        }
        if (request.getSummary() != null) {
            campaign.setSummary(request.getSummary());
        }
        if (request.getDescription() != null) {
            campaign.setDescription(request.getDescription());
        }
        if (request.getProgressionAgnostic() != null) {
            if (Boolean.TRUE.equals(request.getProgressionAgnostic())
                    && campaignDifficultyRepository.countByCampaign_IdAndBarrierTrueAndActiveTrue(
                            campaign.getId()) > 0) {
                throw new ValidationException("progressionAgnostic",
                        "Remove all barriers before making the campaign progression-agnostic");
            }
            campaign.setProgressionAgnostic(request.getProgressionAgnostic());
        }
        if (request.getCompletionMode() != null) {
            campaign.setCompletionMode(request.getCompletionMode());
        }
        if (request.getPlaylistExportEnabled() != null) {
            campaign.setPlaylistExportEnabled(request.getPlaylistExportEnabled());
        }
        if (request.getBackgroundUrl() != null) {
            campaign.setBackgroundUrl(request.getBackgroundUrl());
        }
        if (request.getBackgroundColor() != null) {
            campaign.setBackgroundColor(request.getBackgroundColor());
        }
        if (request.getIconUrl() != null) {
            campaign.setIconUrl(request.getIconUrl());
        }
        if (request.getCompletionXp() != null) {
            if (request.getCompletionXp().signum() < 0) {
                throw new ValidationException("completionXp", "must be non-negative");
            }
            campaign.setCompletionXp(request.getCompletionXp());
        }
        if (request.getCreatorAlias() != null) {
            campaign.setCreatorAlias(request.getCreatorAlias());
        }
        if (request.getSeekingCuration() != null) {
            campaign.setSeekingCuration(request.getSeekingCuration());
            if (request.getSeekingCuration() && campaign.getSubmittedAt() == null) {
                campaign.setSubmittedAt(Instant.now());
            }
        }
        if (request.getTagIds() != null) {
            replaceTagLinks(campaign, request.getTagIds());
        }

        playlistService.evictCampaignPlaylist(campaign.getId());
        return toCampaignResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignResponse publish(UUID campaignId) {
        Campaign campaign = loadActiveCampaign(campaignId);
        if (campaign.getStatus() != CampaignStatus.DRAFT && campaign.getStatus() != CampaignStatus.EDITING) {
            throw new ValidationException("Only draft or editing campaigns can be published");
        }
        validateGraphSingleSink(campaign.getId());
        List<CampaignDifficulty> dirty = campaignDifficultyRepository
                .findByCampaign_IdAndActiveTrueAndRequirementDirtyTrue(campaign.getId());
        if (!dirty.isEmpty()) {
            Set<UUID> changed = dirty.stream().map(CampaignDifficulty::getId).collect(Collectors.toSet());
            campaignEvaluationService.recomputeAfterRequirementChange(campaign, changed);
            dirty.forEach(d -> d.setRequirementDirty(false));
            campaignDifficultyRepository.saveAll(dirty);
        }
        campaign.setStatus(CampaignStatus.PUBLISHED);
        return toCampaignResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignResponse startEditing(UUID campaignId) {
        Campaign campaign = loadActiveCampaign(campaignId);
        if (campaign.getStatus() != CampaignStatus.PUBLISHED) {
            throw new ValidationException("Only published campaigns can enter editing mode");
        }
        campaign.setStatus(CampaignStatus.EDITING);
        return toCampaignResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignResponse markCurated(UUID campaignId, StaffUser curator) {
        if (curator == null
                || (curator.getRole() != StaffRole.CAMPAIGN_CURATOR && curator.getRole() != StaffRole.ADMIN)) {
            throw new ValidationException("Only campaign curators or admins can curate");
        }
        Campaign campaign = loadActiveCampaign(campaignId);
        if (campaign.getStatus() != CampaignStatus.PUBLISHED && campaign.getStatus() != CampaignStatus.EDITING) {
            throw new ValidationException("Only published or editing campaigns can be curated");
        }
        validateGraphSingleSink(campaign.getId());
        campaign.setStatus(CampaignStatus.CURATED);
        campaign.setCuratedAt(Instant.now());
        campaign.setCuratedBy(curator);
        campaign.setSeekingCuration(false);
        Campaign saved = campaignRepository.save(campaign);
        campaignEvaluationService.applyCuratedTransition(saved.getId());
        return toCampaignResponse(saved);
    }

    @Transactional
    public CampaignResponse uncurate(UUID campaignId, StaffUser curator) {
        if (curator == null
                || (curator.getRole() != StaffRole.CAMPAIGN_CURATOR && curator.getRole() != StaffRole.ADMIN)) {
            throw new ValidationException("Only campaign curators or admins can uncurate");
        }
        Campaign campaign = loadActiveCampaign(campaignId);
        if (campaign.getStatus() != CampaignStatus.CURATED) {
            throw new ValidationException("Only curated campaigns can be uncurated");
        }
        campaign.setStatus(CampaignStatus.PUBLISHED);
        campaign.setCuratedAt(null);
        campaign.setCuratedBy(null);
        return toCampaignResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public void deactivateCampaign(UUID campaignId) {
        Campaign campaign = loadActiveCampaign(campaignId);
        campaign.setActive(false);
        campaignRepository.save(campaign);
    }

    @Transactional
    public CampaignResponse setOfficial(UUID campaignId, boolean official) {
        Campaign campaign = loadActiveCampaign(campaignId);
        campaign.setOfficial(official);
        return toCampaignResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignResponse setBackgroundUrl(UUID campaignId, String backgroundUrl) {
        Campaign campaign = loadActiveCampaign(campaignId);
        campaign.setBackgroundUrl(backgroundUrl);
        return toCampaignResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignResponse setBackgroundUrlAsPlayer(Long playerId, UUID campaignId, String backgroundUrl) {
        Campaign campaign = editableDraftCampaign(playerId, campaignId);
        campaign.setBackgroundUrl(backgroundUrl);
        return toCampaignResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignResponse setIconUrl(UUID campaignId, String iconUrl) {
        Campaign campaign = loadActiveCampaign(campaignId);
        campaign.setIconUrl(iconUrl);
        return toCampaignResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignResponse setIconUrlAsPlayer(Long playerId, UUID campaignId, String iconUrl) {
        Campaign campaign = editableDraftCampaign(playerId, campaignId);
        campaign.setIconUrl(iconUrl);
        return toCampaignResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignResponse createCampaignAsPlayer(Long playerId, CreateCampaignRequest request) {
        Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(playerId);
        assertPlayerImageryIsCdnHosted(request.getBackgroundUrl(), request.getIconUrl());
        request.setCreatorId(resolvedUserId);
        request.setCreatorAlias(null);
        return createCampaign(request);
    }

    @Transactional
    public CampaignResponse updateCampaignAsPlayer(Long playerId, UUID campaignId, UpdateCampaignRequest request) {
        Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(playerId);
        assertPlayerImageryIsCdnHosted(request.getBackgroundUrl(), request.getIconUrl());
        assertPlayerCanEditDraft(loadActiveCampaign(campaignId), resolvedUserId);
        return updateCampaign(campaignId, request);
    }

    @Transactional
    public CampaignResponse publishAsPlayer(Long playerId, UUID campaignId) {
        Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(playerId);
        assertPlayerOwnsDraft(loadActiveCampaign(campaignId), resolvedUserId);
        return publish(campaignId);
    }

    @Transactional
    public CampaignResponse unpublishAsPlayer(Long playerId, UUID campaignId) {
        Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(playerId);
        Campaign campaign = loadActiveCampaign(campaignId);
        if (campaign.getCreator() == null || !resolvedUserId.equals(campaign.getCreator().getId())) {
            throw new ValidationException("Only the campaign creator can perform this action");
        }
        if (campaign.getStatus() != CampaignStatus.PUBLISHED && campaign.getStatus() != CampaignStatus.EDITING) {
            throw new ValidationException("Only a published campaign can be unpublished");
        }
        campaign.setStatus(CampaignStatus.DRAFT);
        return toCampaignResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public void deactivateCampaignAsPlayer(Long playerId, UUID campaignId) {
        Campaign campaign = ownedDraftCampaign(playerId, campaignId);
        campaign.setActive(false);
        campaignRepository.save(campaign);
    }

    @Transactional
    public CampaignResponse submitForCurationAsPlayer(Long playerId, UUID campaignId, boolean seeking) {
        Campaign campaign = ownedDraftCampaign(playerId, campaignId);
        campaign.setSeekingCuration(seeking);
        if (seeking && campaign.getSubmittedAt() == null) {
            campaign.setSubmittedAt(Instant.now());
        }
        return toCampaignResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignDifficultyResponse addDifficultyAsPlayer(Long playerId, UUID campaignId,
            AddCampaignDifficultyRequest request) {
        editableDraftCampaign(playerId, campaignId);
        if (campaignDifficultyRepository.countByCampaign_IdAndBarrierFalseAndActiveTrue(campaignId) >= MAX_DIFFICULTIES_PER_CAMPAIGN) {
            throw new ValidationException("Campaign has reached the maximum of "
                    + MAX_DIFFICULTIES_PER_CAMPAIGN + " difficulties");
        }
        return addDifficulty(campaignId, request);
    }

    @Transactional
    public CampaignDifficultyResponse updateDifficultyAsPlayer(Long playerId, UUID campaignDifficultyId,
            UpdateCampaignDifficultyRequest request) {
        Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(playerId);
        CampaignDifficulty difficulty = loadActiveDifficulty(campaignDifficultyId);
        assertPlayerCanEditDraft(difficulty.getCampaign(), resolvedUserId);
        return applyDifficultyUpdate(difficulty, request);
    }

    @Transactional
    public void removeDifficultyAsPlayer(Long playerId, UUID campaignId, UUID campaignDifficultyId) {
        editableDraftCampaign(playerId, campaignId);
        removeDifficulty(campaignId, campaignDifficultyId);
    }

    private Campaign ownedDraftCampaign(Long playerId, UUID campaignId) {
        Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(playerId);
        Campaign campaign = loadActiveCampaign(campaignId);
        assertPlayerOwnsDraft(campaign, resolvedUserId);
        return campaign;
    }

    private Campaign editableDraftCampaign(Long playerId, UUID campaignId) {
        Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(playerId);
        Campaign campaign = loadActiveCampaign(campaignId);
        assertPlayerCanEditDraft(campaign, resolvedUserId);
        return campaign;
    }

    private CampaignDifficulty editableDraftDifficulty(Long playerId, UUID campaignDifficultyId) {
        Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(playerId);
        CampaignDifficulty difficulty = loadActiveDifficulty(campaignDifficultyId);
        assertPlayerCanEditDraft(difficulty.getCampaign(), resolvedUserId);
        return difficulty;
    }

    private CampaignDifficulty loadActiveDifficulty(UUID campaignDifficultyId) {
        return campaignDifficultyRepository.findByIdAndActiveTrue(campaignDifficultyId)
                .orElseThrow(() -> new ResourceNotFoundException("CampaignDifficulty", campaignDifficultyId));
    }

    private void assertPlayerOwnsDraft(Campaign campaign, Long playerId) {
        if (campaign.getCreator() == null || !playerId.equals(campaign.getCreator().getId())) {
            throw new ValidationException("Only the campaign creator can perform this action");
        }
        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new ValidationException("Players can only edit campaigns in draft status; unpublish first");
        }
    }

    private void assertPlayerCanEditDraft(Campaign campaign, Long playerId) {
        boolean isOwner = campaign.getCreator() != null && playerId.equals(campaign.getCreator().getId());
        if (!isOwner && !isAcceptedCollaborator(campaign.getId(), playerId)) {
            throw new ValidationException("Only the campaign owner or a collaborator can perform this action");
        }
        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new ValidationException("Players can only edit campaigns in draft status; unpublish first");
        }
    }

    private boolean isAcceptedCollaborator(UUID campaignId, Long playerId) {
        return campaignCollaboratorRepository.existsByCampaign_IdAndUser_IdAndStatusAndActiveTrue(
                campaignId, playerId, CampaignCollaboratorStatus.ACCEPTED);
    }

    private void assertPlayerImageryIsCdnHosted(String... urls) {
        for (String url : urls) {
            if (url != null && !url.isBlank() && !isCdnHosted(url)) {
                throw new ValidationException(
                        "Campaign imagery must be uploaded through the campaign image endpoints");
            }
        }
    }

    private boolean isCdnHosted(String url) {
        String base = cdnProperties.getBaseUrl();
        return base != null && !base.isBlank() && url.startsWith(base);
    }

    @Transactional
    public CampaignDifficultyResponse addDifficulty(UUID campaignId, AddCampaignDifficultyRequest request) {
        Campaign campaign = loadActiveCampaign(campaignId);
        ensureEditable(campaign);

        MapDifficulty mapDifficulty = mapDifficultyRepository.findByIdAndActiveTrue(request.getMapDifficultyId())
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", request.getMapDifficultyId()));

        if (campaignDifficultyRepository.existsByCampaign_IdAndPositionXAndPositionYAndActiveTrue(
                campaignId, request.getPositionX(), request.getPositionY())) {
            throw new ValidationException("A difficulty already occupies that grid position");
        }

        if (campaignDifficultyRepository
                .findByCampaign_IdAndMapDifficulty_IdAndActiveTrue(campaignId, mapDifficulty.getId())
                .isPresent()) {
            throw new ValidationException("Map difficulty is already part of this campaign");
        }

        CampaignDifficulty difficulty = CampaignDifficulty.builder()
                .campaign(campaign)
                .mapDifficulty(mapDifficulty)
                .requirementType(request.getRequirementType())
                .requirementValue(request.getRequirementValue())
                .prerequisiteMode(request.getPrerequisiteMode() != null
                        ? request.getPrerequisiteMode()
                        : CampaignPrerequisiteMode.OR)
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
                .xp(request.getXp() != null ? request.getXp() : BigDecimal.ZERO)
                .build();

        difficulty = campaignDifficultyRepository.save(difficulty);
        createPrerequisitePaths(difficulty, request.getPrerequisiteCampaignDifficultyIds());
        playlistService.evictCampaignPlaylist(campaignId);

        return toCampaignDifficultyResponse(difficulty,
                safePrereqIds(request.getPrerequisiteCampaignDifficultyIds()),
                List.of());
    }

    @Transactional
    public CampaignDifficultyResponse updateDifficulty(UUID campaignDifficultyId,
            UpdateCampaignDifficultyRequest request) {
        return applyDifficultyUpdate(loadActiveDifficulty(campaignDifficultyId), request);
    }

    private CampaignDifficultyResponse applyDifficultyUpdate(CampaignDifficulty difficulty,
            UpdateCampaignDifficultyRequest request) {
        if (difficulty.isBarrier()) {
            throw new ValidationException("Use the barrier endpoints to edit a barrier");
        }
        ensureEditable(difficulty.getCampaign());

        boolean requirementChanged = false;
        if (request.getRequirementType() != null
                && request.getRequirementType() != difficulty.getRequirementType()) {
            difficulty.setRequirementType(request.getRequirementType());
            requirementChanged = true;
        }
        if (request.getRequirementValue() != null
                && difficulty.getRequirementValue().compareTo(request.getRequirementValue()) != 0) {
            difficulty.setRequirementValue(request.getRequirementValue());
            requirementChanged = true;
        }
        if (requirementChanged && difficulty.getCampaign().getStatus() == CampaignStatus.DRAFT) {
            difficulty.setRequirementDirty(true);
        }
        if (request.getPrerequisiteMode() != null) {
            difficulty.setPrerequisiteMode(request.getPrerequisiteMode());
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
        if (request.getSize() != null) {
            difficulty.setSize(request.getSize());
        }
        if (request.getCheckpointSize() != null) {
            difficulty.setCheckpointSize(request.getCheckpointSize());
        }
        if (request.getPositionX() != null || request.getPositionY() != null) {
            int newX = request.getPositionX() != null ? request.getPositionX() : difficulty.getPositionX();
            int newY = request.getPositionY() != null ? request.getPositionY() : difficulty.getPositionY();
            if ((newX != difficulty.getPositionX() || newY != difficulty.getPositionY())
                    && campaignDifficultyRepository.existsByCampaign_IdAndPositionXAndPositionYAndActiveTrue(
                            difficulty.getCampaign().getId(), newX, newY)) {
                throw new ValidationException("A difficulty already occupies that grid position");
            }
            difficulty.setPositionX(newX);
            difficulty.setPositionY(newY);
        }
        if (request.getXp() != null) {
            if (request.getXp().signum() < 0) {
                throw new ValidationException("xp", "must be non-negative");
            }
            difficulty.setXp(request.getXp());
        }

        if (request.getPrerequisiteCampaignDifficultyIds() != null) {
            replacePrerequisitePaths(difficulty, request.getPrerequisiteCampaignDifficultyIds());
        }

        difficulty = campaignDifficultyRepository.save(difficulty);
        if (requirementChanged && difficulty.getCampaign().getStatus() != CampaignStatus.DRAFT) {
            campaignEvaluationService.recomputeAfterRequirementChange(difficulty.getCampaign(),
                    Set.of(difficulty.getId()));
        }
        List<UUID> currentPrereqIds = campaignDifficultyPathRepository
                .findByCampaignDifficulty_IdAndActiveTrue(difficulty.getId()).stream()
                .map(p -> p.getComesFromCampaignDifficulty().getId())
                .toList();
        List<CampaignItemAwardResponse> items = loadDifficultyItems(difficulty.getId());
        return toCampaignDifficultyResponse(difficulty, currentPrereqIds, items);
    }

    @Transactional
    public void removeDifficulty(UUID campaignId, UUID campaignDifficultyId) {
        CampaignDifficulty difficulty = loadActiveDifficulty(campaignDifficultyId);
        if (!difficulty.getCampaign().getId().equals(campaignId)) {
            throw new ResourceNotFoundException("CampaignDifficulty", campaignDifficultyId);
        }
        ensureEditable(difficulty.getCampaign());
        campaignDifficultyPathRepository.deleteAllTouching(difficulty.getId());
        barrierAffectedRepository.deleteAllTouching(difficulty.getId());
        campaignDifficultyItemRepository.deleteByCampaignDifficulty_Id(difficulty.getId());
        userCampaignScoreRepository.deleteByCampaignDifficulty_Id(difficulty.getId());
        campaignDifficultyRepository.delete(difficulty);
        playlistService.evictCampaignPlaylist(campaignId);
    }

    @Transactional
    public List<CampaignItemAwardResponse> setDifficultyItemAsPlayer(Long playerId, UUID campaignDifficultyId,
            SetCampaignItemRequest request) {
        CampaignDifficulty difficulty = editableDraftDifficulty(playerId, campaignDifficultyId);
        return setDifficultyItem(difficulty, request);
    }

    @Transactional
    public List<CampaignItemAwardResponse> removeDifficultyItemAsPlayer(Long playerId, UUID campaignDifficultyId,
            UUID itemId) {
        CampaignDifficulty difficulty = editableDraftDifficulty(playerId, campaignDifficultyId);
        campaignDifficultyItemRepository.deleteByCampaignDifficulty_IdAndItem_Id(difficulty.getId(), itemId);
        return loadDifficultyItems(difficulty.getId());
    }

    @Transactional
    public List<CampaignItemAwardResponse> setCompletionItemAsPlayer(Long playerId, UUID campaignId,
            SetCampaignItemRequest request) {
        return setCompletionItem(editableDraftCampaign(playerId, campaignId), request);
    }

    @Transactional
    public List<CampaignItemAwardResponse> removeCompletionItemAsPlayer(Long playerId, UUID campaignId, UUID itemId) {
        Campaign campaign = editableDraftCampaign(playerId, campaignId);
        campaignCompletionItemRepository.deleteByCampaign_IdAndItem_Id(campaign.getId(), itemId);
        return loadCompletionItems(campaign.getId());
    }

    private void assertRewardItemAllowed(Item item, Campaign campaign) {
        if (!item.isTradeable() && !campaign.isOfficial()) {
            throw new ValidationException(
                    "Only official campaigns can reward untradeable items");
        }
    }

    private List<CampaignItemAwardResponse> setDifficultyItem(CampaignDifficulty difficulty,
            SetCampaignItemRequest request) {
        ensureEditable(difficulty.getCampaign());
        Item item = itemRepository.findByIdAndActiveTrue(request.getItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Item", request.getItemId()));
        assertRewardItemAllowed(item, difficulty.getCampaign());
        int quantity = request.getQuantity() != null ? request.getQuantity() : 1;

        CampaignDifficultyItem.CampaignDifficultyItemId key = new CampaignDifficultyItem.CampaignDifficultyItemId(
                difficulty.getId(), item.getId());
        CampaignDifficultyItem link = campaignDifficultyItemRepository.findById(key)
                .orElseGet(() -> CampaignDifficultyItem.builder()
                        .id(key)
                        .campaignDifficulty(difficulty)
                        .item(item)
                        .build());
        link.setQuantity(quantity);
        campaignDifficultyItemRepository.save(link);
        return loadDifficultyItems(difficulty.getId());
    }

    private List<CampaignItemAwardResponse> setCompletionItem(Campaign campaign, SetCampaignItemRequest request) {
        ensureEditable(campaign);
        Item item = itemRepository.findByIdAndActiveTrue(request.getItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Item", request.getItemId()));
        assertRewardItemAllowed(item, campaign);
        int quantity = request.getQuantity() != null ? request.getQuantity() : 1;

        CampaignCompletionItem.CampaignCompletionItemId key = new CampaignCompletionItem.CampaignCompletionItemId(
                campaign.getId(), item.getId());
        CampaignCompletionItem link = campaignCompletionItemRepository.findById(key)
                .orElseGet(() -> CampaignCompletionItem.builder()
                        .id(key)
                        .campaign(campaign)
                        .item(item)
                        .build());
        link.setQuantity(quantity);
        campaignCompletionItemRepository.save(link);
        return loadCompletionItems(campaign.getId());
    }

    @Transactional
    public UserCampaignResponse startCampaign(Long userId, UUID campaignId) {
        Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(userId);
        Campaign campaign = loadActiveCampaign(campaignId);
        if (campaign.getStatus() == CampaignStatus.DRAFT) {
            throw new ValidationException("Cannot start a draft campaign");
        }
        User user = userRepository.findByIdAndActiveTrue(resolvedUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", resolvedUserId));

        UserCampaign existing = userCampaignRepository
                .findByUser_IdAndCampaign_IdAndActiveTrue(resolvedUserId, campaignId)
                .orElse(null);
        if (existing != null) {
            if (existing.getStatus() == UserCampaignStatus.ABANDONED) {
                existing.setStatus(UserCampaignStatus.IN_PROGRESS);
                existing.setStartedAt(Instant.now());
                existing.setCompletedAt(null);
                UserCampaign revived = userCampaignRepository.save(existing);
                campaignEvaluationService.importLegacyScores(resolvedUserId, campaignId);
                return toUserCampaignResponse(revived);
            }
            return toUserCampaignResponse(existing);
        }

        UserCampaign userCampaign = UserCampaign.builder()
                .user(user)
                .campaign(campaign)
                .status(UserCampaignStatus.IN_PROGRESS)
                .startedAt(Instant.now())
                .build();
        UserCampaign saved = userCampaignRepository.save(userCampaign);
        campaignEvaluationService.importLegacyScores(resolvedUserId, campaignId);
        return toUserCampaignResponse(saved);
    }

    @Transactional
    public void abandonCampaign(Long userId, UUID campaignId) {
        Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(userId);
        UserCampaign userCampaign = userCampaignRepository
                .findByUser_IdAndCampaign_IdAndActiveTrue(resolvedUserId, campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("UserCampaign", campaignId));
        if (userCampaign.getStatus() == UserCampaignStatus.COMPLETED) {
            throw new ValidationException("Completed campaigns cannot be abandoned");
        }
        userCampaign.setStatus(UserCampaignStatus.ABANDONED);
        userCampaignRepository.save(userCampaign);
    }

    @Transactional
    public CampaignVoteResponse vote(Long userId, UUID campaignId, CampaignVoteDirection direction) {
        Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(userId);
        Campaign campaign = campaignRepository.findByIdAndActiveTrueForUpdate(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));
        if (campaign.getStatus() == CampaignStatus.DRAFT) {
            throw new ValidationException("Cannot vote on a draft campaign");
        }
        User user = userRepository.findByIdAndActiveTrue(resolvedUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", resolvedUserId));
        CampaignVote vote = campaignVoteRepository.findByCampaign_IdAndUser_Id(campaignId, resolvedUserId)
                .orElseGet(() -> CampaignVote.builder().campaign(campaign).user(user).build());
        vote.setVote(direction);
        campaignVoteRepository.save(vote);
        return recountVotes(campaign, direction);
    }

    @Transactional
    public CampaignVoteResponse clearVote(Long userId, UUID campaignId) {
        Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(userId);
        Campaign campaign = campaignRepository.findByIdAndActiveTrueForUpdate(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));
        campaignVoteRepository.deleteByCampaign_IdAndUser_Id(campaignId, resolvedUserId);
        return recountVotes(campaign, null);
    }

    private CampaignVoteResponse recountVotes(Campaign campaign, CampaignVoteDirection myVote) {
        long up = campaignVoteRepository.countByCampaign_IdAndVote(campaign.getId(), CampaignVoteDirection.UP);
        long down = campaignVoteRepository.countByCampaign_IdAndVote(campaign.getId(), CampaignVoteDirection.DOWN);
        campaign.setTotalUpvotes((int) up);
        campaign.setTotalDownvotes((int) down);
        campaign.setVoteScore(WilsonScore.lowerBound(up, up + down));
        campaignRepository.save(campaign);
        return CampaignVoteResponse.builder()
                .campaignId(campaign.getId())
                .totalUpvotes((int) up)
                .totalDownvotes((int) down)
                .voteScore(campaign.getVoteScore())
                .myVote(myVote)
                .build();
    }

    public Page<UserCampaignResponse> listUserCampaigns(Long userId, Pageable pageable) {
        Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(userId);
        Page<UserCampaign> page = userCampaignRepository.findActiveByUserExcludingStatus(
                resolvedUserId, UserCampaignStatus.ABANDONED, pageable);
        List<UUID> campaignIds = page.getContent().stream()
                .map(uc -> uc.getCampaign().getId())
                .distinct()
                .toList();
        Map<UUID, List<CampaignTagResponse>> tagsByCampaign = loadTagsByCampaignIds(campaignIds);
        Map<UUID, Integer> totalByCampaign = countMap(
                campaignIds.isEmpty()
                        ? List.<Object[]>of()
                        : campaignDifficultyRepository.countActiveByCampaignIds(campaignIds));
        Map<UUID, Integer> completedByCampaign = countMap(
                campaignIds.isEmpty()
                        ? List.<Object[]>of()
                        : userCampaignScoreRepository.countActiveByUserAndCampaignIds(resolvedUserId, campaignIds));
        return page.map(uc -> {
            UUID cid = uc.getCampaign().getId();
            CampaignResponse campaign = toCampaignResponse(uc.getCampaign(),
                    tagsByCampaign.getOrDefault(cid, List.of()),
                    totalByCampaign.getOrDefault(cid, 0));
            return toUserCampaignResponse(uc, campaign, completedByCampaign.getOrDefault(cid, 0));
        });
    }

    public CampaignProgressResponse getUserProgress(Long userId, UUID campaignId) {
        Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(userId);
        Campaign campaign = loadActiveCampaign(campaignId);
        if (isDraftHiddenFrom(campaign, resolvedUserId, false)) {
            throw new ResourceNotFoundException("Campaign", campaignId);
        }
        List<UUID> campaignIds = List.of(campaignId);
        ProgressContext ctx = loadProgressContext(resolvedUserId, campaignIds);
        return buildProgress(campaign, resolvedUserId, ctx);
    }

    public List<CampaignProgressResponse> getUserProgressBulk(Long userId, List<UUID> campaignIds) {
        if (campaignIds == null || campaignIds.isEmpty()) {
            return List.of();
        }
        if (campaignIds.size() > MAX_PROGRESS_BULK_IDS) {
            throw new ValidationException("ids", "max " + MAX_PROGRESS_BULK_IDS + " campaign ids per request");
        }
        Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(userId);
        List<Campaign> campaigns = campaignRepository.findByIdInAndActiveTrue(campaignIds);
        if (campaigns.isEmpty()) {
            return List.of();
        }
        Map<UUID, Campaign> campaignById = campaigns.stream()
                .collect(Collectors.toMap(Campaign::getId, c -> c));
        ProgressContext ctx = loadProgressContext(resolvedUserId,
                campaigns.stream().map(Campaign::getId).toList());
        List<CampaignProgressResponse> results = new ArrayList<>(campaignIds.size());
        for (UUID id : campaignIds) {
            Campaign campaign = campaignById.get(id);
            if (campaign == null || isDraftHiddenFrom(campaign, resolvedUserId, false)) {
                continue;
            }
            results.add(buildProgress(campaign, resolvedUserId, ctx));
        }
        return results;
    }

    private ProgressContext loadProgressContext(Long resolvedUserId, Collection<UUID> campaignIds) {
        List<CampaignDifficulty> difficulties = campaignDifficultyRepository
                .findActiveWithMapByCampaignIds(campaignIds);
        Map<UUID, List<CampaignDifficulty>> difficultiesByCampaign = difficulties.stream()
                .collect(Collectors.groupingBy(d -> d.getCampaign().getId()));

        List<CampaignDifficulty> barriers = campaignDifficultyRepository
                .findByCampaign_IdInAndBarrierTrueAndActiveTrue(campaignIds);
        Map<UUID, List<CampaignDifficulty>> barriersByCampaign = barriers.stream()
                .collect(Collectors.groupingBy(b -> b.getCampaign().getId()));

        List<CampaignDifficultyPath> paths = campaignDifficultyPathRepository
                .findByCampaignDifficulty_Campaign_IdInAndActiveTrue(campaignIds);
        Map<UUID, List<UUID>> prereqsByDifficulty = paths.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getCampaignDifficulty().getId(),
                        Collectors.mapping(p -> p.getComesFromCampaignDifficulty().getId(), Collectors.toList())));

        Map<UUID, UserCampaign> userCampaignByCampaign = userCampaignRepository
                .findByUser_IdAndCampaign_IdInAndActiveTrue(resolvedUserId, campaignIds).stream()
                .collect(Collectors.toMap(uc -> uc.getCampaign().getId(), uc -> uc));

        List<UserCampaignScore> campaignScores = userCampaignScoreRepository
                .findWithScoreByUser_IdAndCampaign_IdInAndActiveTrue(resolvedUserId, campaignIds);
        Map<UUID, Set<UUID>> completedByCampaign = campaignScores.stream()
                .collect(Collectors.groupingBy(
                        ucs -> ucs.getCampaign().getId(),
                        Collectors.mapping(ucs -> ucs.getCampaignDifficulty().getId(), Collectors.toSet())));
        Map<UUID, Map<UUID, Score>> campaignScoreByDifficulty = new HashMap<>();
        for (UserCampaignScore ucs : campaignScores) {
            if (ucs.getScore() == null) {
                continue;
            }
            campaignScoreByDifficulty
                    .computeIfAbsent(ucs.getCampaign().getId(), k -> new HashMap<>())
                    .put(ucs.getCampaignDifficulty().getId(), ucs.getScore());
        }

        Map<UUID, List<CampaignTagResponse>> tagsByCampaign = loadTagsByCampaignIds(campaignIds);

        List<UUID> barrierIds = barriers.stream().map(CampaignDifficulty::getId).toList();
        Map<UUID, List<UUID>> affectedByBarrier = barrierIds.isEmpty()
                ? Map.of()
                : barrierAffectedRepository.findByBarrier_IdIn(barrierIds).stream()
                        .collect(Collectors.groupingBy(a -> a.getBarrier().getId(),
                                Collectors.mapping(a -> a.getAffectedDifficulty().getId(), Collectors.toList())));

        List<UUID> nodeIds = new ArrayList<>(difficulties.size() + barrierIds.size());
        difficulties.forEach(d -> nodeIds.add(d.getId()));
        nodeIds.addAll(barrierIds);
        Map<UUID, List<CampaignItemAwardResponse>> itemsByNode = loadDifficultyItemsBulk(nodeIds);

        return new ProgressContext(difficultiesByCampaign, barriersByCampaign, prereqsByDifficulty,
                campaignScoreByDifficulty, userCampaignByCampaign, completedByCampaign, tagsByCampaign,
                affectedByBarrier, itemsByNode);
    }

    private CampaignProgressResponse buildProgress(Campaign campaign, Long resolvedUserId, ProgressContext ctx) {
        UUID campaignId = campaign.getId();
        List<CampaignDifficulty> difficulties = ctx.difficultiesByCampaign.getOrDefault(campaignId, List.of());
        Set<UUID> completedIds = ctx.completedByCampaign.getOrDefault(campaignId, Set.of());
        Map<UUID, Score> campaignScores = ctx.campaignScoreByDifficulty.getOrDefault(campaignId, Map.of());
        UserCampaign uc = ctx.userCampaignByCampaign.get(campaignId);
        boolean agnostic = campaign.isProgressionAgnostic();

        Map<UUID, UserMapDifficultyBests> bestsByMapDifficulty = loadProgressBests(campaign, uc, difficulties,
                resolvedUserId);
        Map<UUID, UUID> mapDifficultyByNode = new HashMap<>();
        for (CampaignDifficulty d : difficulties) {
            mapDifficultyByNode.put(d.getId(), d.getMapDifficulty().getId());
        }

        List<CampaignDifficultyProgressResponse> progress = new ArrayList<>(difficulties.size());
        for (CampaignDifficulty d : difficulties) {
            List<UUID> prereqs = ctx.prereqsByDifficulty.getOrDefault(d.getId(), List.of());
            CampaignPrerequisiteMode mode = d.getPrerequisiteMode() != null
                    ? d.getPrerequisiteMode()
                    : CampaignPrerequisiteMode.OR;
            boolean unlocked = agnostic || prereqsSatisfied(prereqs, mode, completedIds);
            UserMapDifficultyBests bests = bestsByMapDifficulty.get(d.getMapDifficulty().getId());
            BigDecimal userValue = bests != null
                    ? CampaignScoreMetrics.requirementValue(bests, d.getRequirementType())
                    : null;
            Score score = campaignScores.get(d.getId());
            Integer userScore = score != null ? score.getScore() : null;
            progress.add(CampaignDifficultyProgressResponse.builder()
                    .node(toCampaignDifficultyResponse(d, prereqs,
                            ctx.itemsByNode.getOrDefault(d.getId(), List.of())))
                    .userValue(userValue)
                    .userScore(userScore)
                    .completed(completedIds.contains(d.getId()))
                    .unlocked(unlocked)
                    .build());
        }

        List<BarrierProgressResponse> barrierProgress = buildBarrierProgress(campaignId, ctx, completedIds, agnostic,
                bestsByMapDifficulty, mapDifficultyByNode);
        Set<UUID> mapNodeIds = difficulties.stream().map(CampaignDifficulty::getId).collect(Collectors.toSet());
        int completedMapNodes = (int) completedIds.stream().filter(mapNodeIds::contains).count();

        CampaignResponse campaignResponse = toCampaignResponse(campaign,
                ctx.tagsByCampaign.getOrDefault(campaignId, List.of()), difficulties.size());

        return CampaignProgressResponse.builder()
                .id(uc != null ? uc.getId() : null)
                .campaign(campaignResponse)
                .progressStatus(uc != null ? uc.getStatus() : null)
                .startedAt(uc != null ? uc.getStartedAt() : null)
                .completedAt(uc != null ? uc.getCompletedAt() : null)
                .completedDifficulties(completedMapNodes)
                .difficulties(progress)
                .barriers(barrierProgress)
                .build();
    }

    private List<BarrierProgressResponse> buildBarrierProgress(UUID campaignId, ProgressContext ctx,
            Set<UUID> completedIds, boolean agnostic,
            Map<UUID, UserMapDifficultyBests> bestsByMapDifficulty, Map<UUID, UUID> mapDifficultyByNode) {
        List<CampaignDifficulty> barriers = ctx.barriersByCampaign.getOrDefault(campaignId, List.of());
        if (barriers.isEmpty()) {
            return List.of();
        }
        List<BarrierProgressResponse> result = new ArrayList<>(barriers.size());
        for (CampaignDifficulty b : barriers) {
            List<UUID> prereqs = ctx.prereqsByDifficulty.getOrDefault(b.getId(), List.of());
            CampaignPrerequisiteMode mode = b.getPrerequisiteMode() != null
                    ? b.getPrerequisiteMode()
                    : CampaignPrerequisiteMode.OR;
            boolean unlocked = agnostic || prereqsSatisfied(prereqs, mode, completedIds);
            List<UUID> affected = ctx.affectedByBarrier.getOrDefault(b.getId(), List.of());
            result.add(BarrierProgressResponse.builder()
                    .barrier(toBarrierResponse(b, prereqs, affected,
                            ctx.itemsByNode.getOrDefault(b.getId(), List.of())))
                    .currentValue(computeBarrierCurrentValue(b, affected, bestsByMapDifficulty, mapDifficultyByNode))
                    .satisfied(completedIds.contains(b.getId()))
                    .unlocked(unlocked)
                    .build());
        }
        return result;
    }

    private Map<UUID, UserMapDifficultyBests> loadProgressBests(Campaign campaign, UserCampaign uc,
            List<CampaignDifficulty> difficulties, Long resolvedUserId) {
        if (uc == null || difficulties.isEmpty()) {
            return Map.of();
        }
        List<UUID> mapDifficultyIds = difficulties.stream()
                .map(d -> d.getMapDifficulty().getId())
                .distinct()
                .toList();
        Instant since = !campaign.isLegacy() && uc.getStartedAt() != null
                ? uc.getStartedAt()
                : Instant.EPOCH;
        return scoreRepository.findBestsByUserAndMapDifficulties(resolvedUserId, mapDifficultyIds, since).stream()
                .collect(Collectors.toMap(UserMapDifficultyBests::mapDifficultyId, b -> b));
    }

    private static boolean prereqsSatisfied(List<UUID> prereqs, CampaignPrerequisiteMode mode,
            Set<UUID> completedIds) {
        if (prereqs.isEmpty()) {
            return true;
        }
        if (mode == CampaignPrerequisiteMode.AND) {
            return completedIds.containsAll(prereqs);
        }
        for (UUID p : prereqs) {
            if (completedIds.contains(p)) {
                return true;
            }
        }
        return false;
    }

    private static Map<UUID, Integer> countMap(List<Object[]> rows) {
        Map<UUID, Integer> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return map;
    }

    private record ProgressContext(
            Map<UUID, List<CampaignDifficulty>> difficultiesByCampaign,
            Map<UUID, List<CampaignDifficulty>> barriersByCampaign,
            Map<UUID, List<UUID>> prereqsByDifficulty,
            Map<UUID, Map<UUID, Score>> campaignScoreByDifficulty,
            Map<UUID, UserCampaign> userCampaignByCampaign,
            Map<UUID, Set<UUID>> completedByCampaign,
            Map<UUID, List<CampaignTagResponse>> tagsByCampaign,
            Map<UUID, List<UUID>> affectedByBarrier,
            Map<UUID, List<CampaignItemAwardResponse>> itemsByNode) {
    }

    public List<CampaignTagResponse> listTags() {
        return campaignTagRepository.findByActiveTrue().stream().map(CampaignService::toTagResponse).toList();
    }

    public List<CampaignTagResponse> listTagsByKind(CampaignTagKind kind) {
        return campaignTagRepository.findByKindAndActiveTrue(kind).stream()
                .map(CampaignService::toTagResponse)
                .toList();
    }

    @Transactional
    public CampaignTagResponse createTag(CreateCampaignTagRequest request, StaffUser actor) {
        boolean isCurator = actor != null
                && (actor.getRole() == StaffRole.CAMPAIGN_CURATOR || actor.getRole() == StaffRole.ADMIN);

        if (request.getKind() == CampaignTagKind.CATEGORY || request.getKind() == CampaignTagKind.DIFFICULTY) {
            throw new ValidationException("Category and difficulty tags are system-managed");
        }
        if (request.getKind() == CampaignTagKind.THEME && !isCurator) {
            throw new ValidationException("Only curators can mint theme tags");
        }

        if (campaignTagRepository
                .findByKindAndNameIgnoreCaseAndActiveTrue(request.getKind(), request.getName().trim())
                .isPresent()) {
            throw new ValidationException("A tag with that kind and name already exists");
        }

        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findByIdAndActiveTrue(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", request.getCategoryId()));
        }

        CampaignTag tag = CampaignTag.builder()
                .kind(request.getKind())
                .name(request.getName().trim())
                .category(category)
                .system(false)
                .build();
        return toTagResponse(campaignTagRepository.save(tag));
    }

    @Transactional
    public CampaignBarrierResponse addBarrierAsPlayer(Long playerId, UUID campaignId, AddCampaignBarrierRequest request) {
        editableDraftCampaign(playerId, campaignId);
        if (campaignDifficultyRepository.countByCampaign_IdAndBarrierTrueAndActiveTrue(campaignId)
                >= MAX_BARRIERS_PER_CAMPAIGN) {
            throw new ValidationException("Campaign has reached the maximum of "
                    + MAX_BARRIERS_PER_CAMPAIGN + " barriers");
        }
        return addBarrier(campaignId, request);
    }

    @Transactional
    public CampaignBarrierResponse addBarrier(UUID campaignId, AddCampaignBarrierRequest request) {
        Campaign campaign = loadActiveCampaign(campaignId);
        ensureEditable(campaign);
        if (campaign.isProgressionAgnostic()) {
            throw new ValidationException("Barriers cannot be added to a progression-agnostic campaign");
        }
        validateBarrierCondition(request.getConditionType(), request.getConditionValue());
        if (campaignDifficultyRepository.existsByCampaign_IdAndPositionXAndPositionYAndActiveTrue(
                campaignId, request.getPositionX(), request.getPositionY())) {
            throw new ValidationException("A node already occupies that grid position");
        }
        CampaignDifficulty barrier = CampaignDifficulty.builder()
                .campaign(campaign)
                .barrier(true)
                .barrierConditionType(request.getConditionType())
                .barrierConditionValue(request.getConditionValue())
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
                .xp(request.getXp() != null ? request.getXp() : BigDecimal.ZERO)
                .build();
        barrier = campaignDifficultyRepository.save(barrier);
        createPrerequisitePaths(barrier, request.getPrerequisiteCampaignDifficultyIds());
        replaceAffectedNodes(barrier, request.getAffectedCampaignDifficultyIds());
        return toBarrierResponse(barrier,
                safePrereqIds(request.getPrerequisiteCampaignDifficultyIds()),
                safePrereqIds(request.getAffectedCampaignDifficultyIds()),
                List.of());
    }

    @Transactional
    public CampaignBarrierResponse updateBarrierAsPlayer(Long playerId, UUID barrierId,
            UpdateCampaignBarrierRequest request) {
        Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(playerId);
        CampaignDifficulty barrier = loadActiveBarrier(barrierId);
        assertPlayerCanEditDraft(barrier.getCampaign(), resolvedUserId);
        return applyBarrierUpdate(barrier, request);
    }

    @Transactional
    public CampaignBarrierResponse updateBarrier(UUID barrierId, UpdateCampaignBarrierRequest request) {
        return applyBarrierUpdate(loadActiveBarrier(barrierId), request);
    }

    @Transactional
    public void removeBarrierAsPlayer(Long playerId, UUID campaignId, UUID barrierId) {
        editableDraftCampaign(playerId, campaignId);
        loadActiveBarrier(barrierId);
        removeDifficulty(campaignId, barrierId);
    }

    private CampaignBarrierResponse applyBarrierUpdate(CampaignDifficulty barrier,
            UpdateCampaignBarrierRequest request) {
        ensureEditable(barrier.getCampaign());

        boolean conditionChanged = false;
        if (request.getConditionType() != null && request.getConditionType() != barrier.getBarrierConditionType()) {
            barrier.setBarrierConditionType(request.getConditionType());
            conditionChanged = true;
        }
        if (request.getConditionValue() != null
                && (barrier.getBarrierConditionValue() == null
                        || barrier.getBarrierConditionValue().compareTo(request.getConditionValue()) != 0)) {
            barrier.setBarrierConditionValue(request.getConditionValue());
            conditionChanged = true;
        }
        validateBarrierCondition(barrier.getBarrierConditionType(), barrier.getBarrierConditionValue());
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
        if (request.getPositionX() != null || request.getPositionY() != null) {
            int newX = request.getPositionX() != null ? request.getPositionX() : barrier.getPositionX();
            int newY = request.getPositionY() != null ? request.getPositionY() : barrier.getPositionY();
            if ((newX != barrier.getPositionX() || newY != barrier.getPositionY())
                    && campaignDifficultyRepository.existsByCampaign_IdAndPositionXAndPositionYAndActiveTrue(
                            barrier.getCampaign().getId(), newX, newY)) {
                throw new ValidationException("A node already occupies that grid position");
            }
            barrier.setPositionX(newX);
            barrier.setPositionY(newY);
        }
        if (request.getXp() != null) {
            if (request.getXp().signum() < 0) {
                throw new ValidationException("xp", "must be non-negative");
            }
            barrier.setXp(request.getXp());
        }
        if (request.getPrerequisiteCampaignDifficultyIds() != null) {
            replacePrerequisitePaths(barrier, request.getPrerequisiteCampaignDifficultyIds());
        }
        if (request.getAffectedCampaignDifficultyIds() != null) {
            replaceAffectedNodes(barrier, request.getAffectedCampaignDifficultyIds());
        }

        barrier = campaignDifficultyRepository.save(barrier);
        if (conditionChanged && barrier.getCampaign().getStatus() != CampaignStatus.DRAFT) {
            campaignEvaluationService.recomputeAfterRequirementChange(barrier.getCampaign(),
                    Set.of(barrier.getId()));
        }
        List<UUID> prereqIds = campaignDifficultyPathRepository
                .findByCampaignDifficulty_IdAndActiveTrue(barrier.getId()).stream()
                .map(p -> p.getComesFromCampaignDifficulty().getId())
                .toList();
        return toBarrierResponse(barrier, prereqIds, loadBarrierAffectedIds(barrier.getId()),
                loadDifficultyItems(barrier.getId()));
    }

    private void validateBarrierCondition(BarrierConditionType type, BigDecimal value) {
        if (type == null) {
            throw new ValidationException("conditionType", "is required");
        }
        if (type != BarrierConditionType.FC && value == null) {
            throw new ValidationException("conditionValue", "is required for this condition");
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
        CampaignDifficulty difficulty = loadActiveDifficulty(id);
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
    public CampaignTextResponse addTextAsPlayer(Long playerId, UUID campaignId, CampaignTextRequest request) {
        editableDraftCampaign(playerId, campaignId);
        if (campaignTextRepository.countByCampaign_IdAndActiveTrue(campaignId) >= MAX_TEXTS_PER_CAMPAIGN) {
            throw new ValidationException("Campaign has reached the maximum of "
                    + MAX_TEXTS_PER_CAMPAIGN + " text elements");
        }
        return addText(campaignId, request);
    }

    @Transactional
    public CampaignTextResponse addText(UUID campaignId, CampaignTextRequest request) {
        Campaign campaign = loadActiveCampaign(campaignId);
        ensureEditable(campaign);
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
        return toTextResponse(campaignTextRepository.save(text));
    }

    @Transactional
    public CampaignTextResponse updateTextAsPlayer(Long playerId, UUID textId, CampaignTextRequest request) {
        Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(playerId);
        CampaignText text = loadActiveText(textId);
        assertPlayerCanEditDraft(text.getCampaign(), resolvedUserId);
        return applyTextUpdate(text, request);
    }

    @Transactional
    public CampaignTextResponse updateText(UUID textId, CampaignTextRequest request) {
        return applyTextUpdate(loadActiveText(textId), request);
    }

    @Transactional
    public void removeTextAsPlayer(Long playerId, UUID campaignId, UUID textId) {
        editableDraftCampaign(playerId, campaignId);
        removeText(campaignId, textId);
    }

    @Transactional
    public void removeText(UUID campaignId, UUID textId) {
        CampaignText text = loadActiveText(textId);
        if (!text.getCampaign().getId().equals(campaignId)) {
            throw new ResourceNotFoundException("CampaignText", textId);
        }
        ensureEditable(text.getCampaign());
        campaignTextRepository.delete(text);
    }

    private CampaignTextResponse applyTextUpdate(CampaignText text, CampaignTextRequest request) {
        ensureEditable(text.getCampaign());
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
        return toTextResponse(campaignTextRepository.save(text));
    }

    private CampaignText loadActiveText(UUID id) {
        return campaignTextRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("CampaignText", id));
    }

    private String sanitizeTextContent(String content) {
        return content == null ? "" : richTextSanitizer.sanitize(content, MAX_TEXT_CONTENT_LENGTH);
    }

    private List<CampaignTextResponse> loadTextResponses(UUID campaignId) {
        return campaignTextRepository.findByCampaign_IdAndActiveTrue(campaignId).stream()
                .map(CampaignService::toTextResponse)
                .toList();
    }

    private Campaign loadActiveCampaign(UUID campaignId) {
        return campaignRepository.findByIdAndActiveTrue(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));
    }

    private void ensureEditable(Campaign campaign) {
        if (campaign.getStatus() == CampaignStatus.CURATED) {
            throw new ValidationException("Curated campaigns are locked and cannot be edited");
        }
    }

    private String normalizeAndValidateSlug(String requested, String fallbackName, UUID excludeId) {
        String candidate = requested != null && !requested.isBlank()
                ? requested.trim().toLowerCase()
                : slugify(fallbackName);
        if (!SLUG_PATTERN.matcher(candidate).matches()) {
            throw new ValidationException("slug",
                    "must be lowercase alphanumeric with dash separators");
        }
        boolean taken = excludeId == null
                ? campaignRepository.existsBySlug(candidate)
                : campaignRepository.existsBySlugAndIdNot(candidate, excludeId);
        if (taken) {
            throw new ValidationException("slug", "is already in use");
        }
        return candidate;
    }

    private String slugify(String input) {
        if (input == null) {
            return "";
        }
        String lowered = input.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return lowered.isEmpty() ? "" : lowered;
    }

    private void validateGraphSingleSink(UUID campaignId) {
        List<CampaignDifficulty> difficulties = campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaignId);
        if (difficulties.stream().noneMatch(d -> !d.isBarrier())) {
            throw new ValidationException("Campaign must have at least one difficulty");
        }
        List<CampaignDifficultyPath> paths = campaignDifficultyPathRepository
                .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaignId);

        Set<UUID> barrierIds = difficulties.stream().filter(CampaignDifficulty::isBarrier)
                .map(CampaignDifficulty::getId).collect(Collectors.toSet());
        Set<UUID> nodesWithOutgoing = paths.stream()
                .map(p -> p.getComesFromCampaignDifficulty().getId())
                .collect(Collectors.toSet());
        Set<UUID> nodesWithIncoming = paths.stream()
                .map(p -> p.getCampaignDifficulty().getId())
                .collect(Collectors.toSet());

        boolean barrierIsPathDeadEnd = barrierIds.stream()
                .anyMatch(id -> nodesWithIncoming.contains(id) && !nodesWithOutgoing.contains(id));
        if (barrierIsPathDeadEnd) {
            throw new ValidationException("A barrier cannot be the terminal endpoint of a campaign");
        }

        Set<UUID> sinks = difficulties.stream()
                .filter(d -> !d.isBarrier())
                .map(CampaignDifficulty::getId)
                .collect(Collectors.toCollection(HashSet::new));
        sinks.removeAll(nodesWithOutgoing);
        if (sinks.size() != 1) {
            throw new ValidationException("Campaign graph must have exactly one terminal endpoint");
        }
    }

    private void createPrerequisitePaths(CampaignDifficulty difficulty, List<UUID> prerequisiteIds) {
        if (prerequisiteIds == null || prerequisiteIds.isEmpty()) {
            return;
        }
        for (UUID prereqId : prerequisiteIds) {
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
                    .build();
            campaignDifficultyPathRepository.save(path);
        }
    }

    private void replacePrerequisitePaths(CampaignDifficulty difficulty, List<UUID> prerequisiteIds) {
        campaignDifficultyPathRepository.deleteAllByCampaignDifficultyId(difficulty.getId());
        campaignDifficultyPathRepository.flush();
        createPrerequisitePaths(difficulty, prerequisiteIds);
    }

    private void replaceTagLinks(Campaign campaign, List<UUID> tagIds) {
        List<CampaignTagLink> existing = campaignTagLinkRepository.findByCampaign_Id(campaign.getId());
        campaignTagLinkRepository.deleteAll(existing);
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        Set<UUID> seen = new HashSet<>();
        for (UUID tagId : tagIds) {
            if (!seen.add(tagId)) {
                continue;
            }
            CampaignTag tag = campaignTagRepository.findByIdAndActiveTrue(tagId)
                    .orElseThrow(() -> new ResourceNotFoundException("CampaignTag", tagId));
            CampaignTagLink link = CampaignTagLink.builder()
                    .id(CampaignTagLink.CampaignTagLinkId.builder()
                            .campaignId(campaign.getId())
                            .campaignTagId(tag.getId())
                            .build())
                    .campaign(campaign)
                    .campaignTag(tag)
                    .build();
            campaignTagLinkRepository.save(link);
        }
    }

    private List<CampaignItemAwardResponse> loadDifficultyItems(UUID difficultyId) {
        return campaignDifficultyItemRepository.findByCampaignDifficulty_Id(difficultyId).stream()
                .map(CampaignService::toItemAward)
                .toList();
    }

    private List<CampaignItemAwardResponse> loadCompletionItems(UUID campaignId) {
        return campaignCompletionItemRepository.findByCampaign_Id(campaignId).stream()
                .map(CampaignService::toItemAward)
                .toList();
    }

    private CampaignDetailResponse buildDetailResponse(Campaign campaign, Long viewerId) {
        UUID campaignId = campaign.getId();
        List<CampaignDifficulty> difficulties = campaignDifficultyRepository
                .findActiveWithMapByCampaignId(campaignId);
        Map<UUID, List<UUID>> prereqsByDifficultyId = campaignDifficultyPathRepository
                .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaignId).stream()
                .collect(Collectors.groupingBy(
                        p -> p.getCampaignDifficulty().getId(),
                        Collectors.mapping(p -> p.getComesFromCampaignDifficulty().getId(), Collectors.toList())));
        Map<UUID, List<CampaignItemAwardResponse>> itemsByDifficultyId = loadDifficultyItemsBulk(
                difficulties.stream().map(CampaignDifficulty::getId).toList());

        List<CampaignDifficultyResponse> difficultyResponses = new ArrayList<>(difficulties.size());
        for (CampaignDifficulty d : difficulties) {
            difficultyResponses.add(toCampaignDifficultyResponse(d,
                    prereqsByDifficultyId.getOrDefault(d.getId(), List.of()),
                    itemsByDifficultyId.getOrDefault(d.getId(), List.of())));
        }

        List<CampaignDifficulty> barriers = campaignDifficultyRepository
                .findByCampaign_IdAndBarrierTrueAndActiveTrue(campaignId);
        List<CampaignBarrierResponse> barrierResponses = new ArrayList<>(barriers.size());
        if (!barriers.isEmpty()) {
            List<UUID> barrierIds = barriers.stream().map(CampaignDifficulty::getId).toList();
            Map<UUID, List<UUID>> affectedByBarrier = barrierAffectedRepository.findByBarrier_IdIn(barrierIds).stream()
                    .collect(Collectors.groupingBy(a -> a.getBarrier().getId(),
                            Collectors.mapping(a -> a.getAffectedDifficulty().getId(), Collectors.toList())));
            Map<UUID, List<CampaignItemAwardResponse>> barrierItems = loadDifficultyItemsBulk(barrierIds);
            for (CampaignDifficulty b : barriers) {
                barrierResponses.add(toBarrierResponse(b,
                        prereqsByDifficultyId.getOrDefault(b.getId(), List.of()),
                        affectedByBarrier.getOrDefault(b.getId(), List.of()),
                        barrierItems.getOrDefault(b.getId(), List.of())));
            }
        }

        return CampaignDetailResponse.builder()
                .id(campaignId)
                .creatorId(campaign.getCreator() != null ? campaign.getCreator().getId() : null)
                .creatorName(campaign.getCreator() != null ? campaign.getCreator().getName() : null)
                .creatorAlias(campaign.getCreatorAlias())
                .name(campaign.getName())
                .slug(campaign.getSlug())
                .summary(campaign.getSummary())
                .description(campaign.getDescription())
                .status(campaign.getStatus())
                .seekingCuration(campaign.isSeekingCuration())
                .official(campaign.isOfficial())
                .progressionAgnostic(campaign.isProgressionAgnostic())
                .completionMode(campaign.getCompletionMode())
                .legacy(campaign.isLegacy())
                .completionXp(campaign.getCompletionXp())
                .curatorNotes(campaign.getCuratorNotes())
                .playlistExportEnabled(campaign.isPlaylistExportEnabled())
                .backgroundUrl(campaign.getBackgroundUrl())
                .backgroundColor(campaign.getBackgroundColor())
                .iconUrl(campaign.getIconUrl())
                .totalUpvotes(campaign.getTotalUpvotes())
                .totalDownvotes(campaign.getTotalDownvotes())
                .voteScore(campaign.getVoteScore())
                .myVote(viewerVoteFor(campaignId, viewerId))
                .submittedAt(campaign.getSubmittedAt())
                .curatedAt(campaign.getCuratedAt())
                .createdAt(campaign.getCreatedAt())
                .tags(loadTagResponses(campaignId))
                .difficulties(difficultyResponses)
                .barriers(barrierResponses)
                .texts(loadTextResponses(campaignId))
                .completionItems(loadCompletionItems(campaignId))
                .build();
    }

    private List<CampaignTagResponse> loadTagResponses(UUID campaignId) {
        return campaignTagLinkRepository.findByCampaign_Id(campaignId).stream()
                .map(link -> toTagResponse(link.getCampaignTag()))
                .toList();
    }

    private Map<UUID, List<CampaignTagResponse>> loadTagsByCampaignIds(Collection<UUID> campaignIds) {
        if (campaignIds.isEmpty()) {
            return Map.of();
        }
        return campaignTagLinkRepository.findByCampaign_IdIn(campaignIds).stream()
                .collect(Collectors.groupingBy(
                        link -> link.getCampaign().getId(),
                        Collectors.mapping(link -> toTagResponse(link.getCampaignTag()), Collectors.toList())));
    }

    private Map<UUID, List<CampaignItemAwardResponse>> loadDifficultyItemsBulk(Collection<UUID> difficultyIds) {
        if (difficultyIds.isEmpty()) {
            return Map.of();
        }
        return campaignDifficultyItemRepository.findByCampaignDifficulty_IdIn(difficultyIds).stream()
                .collect(Collectors.groupingBy(
                        link -> link.getCampaignDifficulty().getId(),
                        Collectors.mapping(CampaignService::toItemAward, Collectors.toList())));
    }

    private CampaignResponse toCampaignResponse(Campaign campaign) {
        return toCampaignResponse(campaign,
                loadTagResponses(campaign.getId()),
                (int) campaignDifficultyRepository.countByCampaign_IdAndBarrierFalseAndActiveTrue(campaign.getId()));
    }

    private CampaignResponse toCampaignResponse(Campaign campaign, List<CampaignTagResponse> tags,
            int difficultyCount) {
        return toCampaignResponse(campaign, tags, difficultyCount, null);
    }

    private CampaignResponse toCampaignResponse(Campaign campaign, List<CampaignTagResponse> tags,
            int difficultyCount, CampaignVoteDirection myVote) {
        return CampaignResponse.builder()
                .id(campaign.getId())
                .creatorId(campaign.getCreator() != null ? campaign.getCreator().getId() : null)
                .creatorName(campaign.getCreator() != null ? campaign.getCreator().getName() : null)
                .creatorAlias(campaign.getCreatorAlias())
                .name(campaign.getName())
                .slug(campaign.getSlug())
                .summary(campaign.getSummary())
                .description(campaign.getDescription())
                .status(campaign.getStatus())
                .seekingCuration(campaign.isSeekingCuration())
                .official(campaign.isOfficial())
                .progressionAgnostic(campaign.isProgressionAgnostic())
                .completionMode(campaign.getCompletionMode())
                .legacy(campaign.isLegacy())
                .completionXp(campaign.getCompletionXp())
                .playlistExportEnabled(campaign.isPlaylistExportEnabled())
                .backgroundUrl(campaign.getBackgroundUrl())
                .backgroundColor(campaign.getBackgroundColor())
                .iconUrl(campaign.getIconUrl())
                .difficultyCount(difficultyCount)
                .totalUpvotes(campaign.getTotalUpvotes())
                .totalDownvotes(campaign.getTotalDownvotes())
                .voteScore(campaign.getVoteScore())
                .myVote(myVote)
                .tags(tags)
                .submittedAt(campaign.getSubmittedAt())
                .curatedAt(campaign.getCuratedAt())
                .createdAt(campaign.getCreatedAt())
                .build();
    }

    private CampaignDifficultyResponse toCampaignDifficultyResponse(CampaignDifficulty difficulty,
            List<UUID> prerequisiteIds,
            List<CampaignItemAwardResponse> items) {
        MapDifficulty md = difficulty.getMapDifficulty();
        return CampaignDifficultyResponse.builder()
                .id(difficulty.getId())
                .mapDifficultyId(md.getId())
                .songName(md.getMap().getSongName())
                .songAuthor(md.getMap().getSongAuthor())
                .mapAuthor(md.getMap().getMapAuthor())
                .coverUrl(md.getMap().getCoverUrl())
                .cdnCoverUrl(md.getMap().getCdnCoverUrl())
                .difficulty(md.getDifficulty().name())
                .characteristic(md.getCharacteristic())
                .requirementType(difficulty.getRequirementType())
                .requirementValue(difficulty.getRequirementValue())
                .prerequisiteMode(difficulty.getPrerequisiteMode())
                .description(difficulty.getDescription())
                .checkpointLabel(difficulty.getCheckpointLabel())
                .checkpointLabelPosition(difficulty.getCheckpointLabelPosition())
                .checkpointColor(difficulty.getCheckpointColor())
                .borderColor(difficulty.getBorderColor())
                .borderShape(difficulty.getBorderShape())
                .size(difficulty.getSize())
                .checkpointSize(difficulty.getCheckpointSize())
                .checkpointAvatarUrl(difficulty.getCheckpointAvatarUrl())
                .positionX(difficulty.getPositionX())
                .positionY(difficulty.getPositionY())
                .xp(difficulty.getXp())
                .prerequisiteCampaignDifficultyIds(prerequisiteIds)
                .items(items)
                .build();
    }

    private static CampaignBarrierResponse toBarrierResponse(CampaignDifficulty barrier, List<UUID> prereqIds,
            List<UUID> affectedIds, List<CampaignItemAwardResponse> items) {
        return CampaignBarrierResponse.builder()
                .id(barrier.getId())
                .conditionType(barrier.getBarrierConditionType())
                .conditionValue(barrier.getBarrierConditionValue())
                .description(barrier.getDescription())
                .checkpointLabel(barrier.getCheckpointLabel())
                .checkpointLabelPosition(barrier.getCheckpointLabelPosition())
                .checkpointAvatarUrl(barrier.getCheckpointAvatarUrl())
                .checkpointColor(barrier.getCheckpointColor())
                .borderColor(barrier.getBorderColor())
                .borderShape(barrier.getBorderShape())
                .size(barrier.getSize())
                .checkpointSize(barrier.getCheckpointSize())
                .positionX(barrier.getPositionX())
                .positionY(barrier.getPositionY())
                .xp(barrier.getXp())
                .prerequisiteCampaignDifficultyIds(prereqIds)
                .affectedCampaignDifficultyIds(affectedIds)
                .items(items)
                .build();
    }

    private static CampaignTextResponse toTextResponse(CampaignText text) {
        return CampaignTextResponse.builder()
                .id(text.getId())
                .content(text.getContent())
                .positionX(text.getPositionX())
                .positionY(text.getPositionY())
                .font(text.getFont())
                .scale(text.getScale())
                .color(text.getColor())
                .effects(text.getEffects())
                .build();
    }

    private BigDecimal computeBarrierCurrentValue(CampaignDifficulty barrier, List<UUID> affected,
            Map<UUID, UserMapDifficultyBests> bestsByMapDifficulty, Map<UUID, UUID> mapDifficultyByNode) {
        BarrierConditionType type = barrier.getBarrierConditionType();
        if (type == null || affected.isEmpty()) {
            return null;
        }
        List<BigDecimal> values = new ArrayList<>(affected.size());
        boolean allFullCombo = true;
        for (UUID nodeId : affected) {
            UserMapDifficultyBests bests = bestsByMapDifficulty.get(mapDifficultyByNode.get(nodeId));
            if (bests == null) {
                return null;
            }
            if (type == BarrierConditionType.FC) {
                allFullCombo = allFullCombo && bests.hasFullCombo();
                continue;
            }
            BigDecimal v = CampaignScoreMetrics.barrierMetric(bests, type);
            if (v == null) {
                return null;
            }
            values.add(v);
        }
        if (type == BarrierConditionType.FC) {
            return allFullCombo ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        if (values.isEmpty()) {
            return null;
        }
        return CampaignScoreMetrics.isMaxAggregate(type)
                ? CampaignScoreMetrics.max(values)
                : CampaignScoreMetrics.average(values);
    }

    private UserCampaignResponse toUserCampaignResponse(UserCampaign uc) {
        int completed = (int) userCampaignScoreRepository
                .countByUser_IdAndCampaign_IdAndActiveTrue(uc.getUser().getId(), uc.getCampaign().getId());
        return toUserCampaignResponse(uc, toCampaignResponse(uc.getCampaign()), completed);
    }

    private UserCampaignResponse toUserCampaignResponse(UserCampaign uc, CampaignResponse campaign,
            int completedDifficulties) {
        return UserCampaignResponse.builder()
                .id(uc.getId())
                .campaign(campaign)
                .progressStatus(uc.getStatus())
                .startedAt(uc.getStartedAt())
                .completedAt(uc.getCompletedAt())
                .completedDifficulties(completedDifficulties)
                .build();
    }

    private static CampaignTagResponse toTagResponse(CampaignTag tag) {
        return CampaignTagResponse.builder()
                .id(tag.getId())
                .kind(tag.getKind())
                .name(tag.getName())
                .categoryId(tag.getCategory() != null ? tag.getCategory().getId() : null)
                .system(tag.isSystem())
                .build();
    }

    private static CampaignItemAwardResponse toItemAward(CampaignDifficultyItem link) {
        return CampaignItemAwardResponse.builder()
                .itemId(link.getItem().getId())
                .itemName(link.getItem().getName())
                .quantity(link.getQuantity())
                .build();
    }

    private static CampaignItemAwardResponse toItemAward(CampaignCompletionItem link) {
        return CampaignItemAwardResponse.builder()
                .itemId(link.getItem().getId())
                .itemName(link.getItem().getName())
                .quantity(link.getQuantity())
                .build();
    }

    private static List<UUID> safePrereqIds(List<UUID> ids) {
        return ids == null ? List.of() : ids;
    }
}
