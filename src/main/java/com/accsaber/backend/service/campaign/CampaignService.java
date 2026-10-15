package com.accsaber.backend.service.campaign;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.accsaber.backend.config.CdnProperties;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.campaign.CampaignFilter;
import com.accsaber.backend.model.dto.request.campaign.CreateCampaignRequest;
import com.accsaber.backend.model.dto.request.campaign.CreateCampaignTagRequest;
import com.accsaber.backend.model.dto.request.campaign.SetCampaignItemRequest;
import com.accsaber.backend.model.dto.request.campaign.UpdateCampaignRequest;
import com.accsaber.backend.model.dto.response.campaign.CampaignDetailResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignDifficultyResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignItemAwardResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignTagResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignVoteResponse;
import com.accsaber.backend.model.dto.response.campaign.UserCampaignResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignBackgroundPlacement;
import com.accsaber.backend.model.entity.campaign.CampaignCollaboratorStatus;
import com.accsaber.backend.model.entity.campaign.CampaignCompletionItem;
import com.accsaber.backend.model.entity.campaign.CampaignCompletionMode;
import com.accsaber.backend.model.entity.campaign.CampaignDifficulty;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyItem;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.campaign.CampaignTag;
import com.accsaber.backend.model.entity.campaign.CampaignTagKind;
import com.accsaber.backend.model.entity.campaign.CampaignTagLink;
import com.accsaber.backend.model.entity.campaign.CampaignVote;
import com.accsaber.backend.model.entity.campaign.CampaignVoteDirection;
import com.accsaber.backend.model.entity.campaign.UserCampaign;
import com.accsaber.backend.model.entity.campaign.UserCampaignStatus;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.staff.StaffRole;
import com.accsaber.backend.model.entity.staff.StaffUser;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.event.CampaignDistinctionEvent;
import com.accsaber.backend.model.event.LegacyCampaignBackfillEvent;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.SortAliases;
import com.accsaber.backend.repository.campaign.CampaignCompletionItemRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyItemRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyRepository;
import com.accsaber.backend.repository.campaign.CampaignRepository;
import com.accsaber.backend.repository.campaign.CampaignTagLinkRepository;
import com.accsaber.backend.repository.campaign.CampaignTagRepository;
import com.accsaber.backend.repository.campaign.CampaignVoteRepository;
import com.accsaber.backend.repository.campaign.UserCampaignRepository;
import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.repository.staff.StaffUserRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.item.ItemAwardService;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.playlist.PlaylistService;
import com.accsaber.backend.service.score.CampaignScoreGate;
import com.accsaber.backend.util.Slugs;
import com.accsaber.backend.util.WilsonScore;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampaignService {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final Double MAX_BACKGROUND_SCALE = (double) (1000);
    private static final Double MIN_BACKGROUND_SCALE = 1.0;
    private static final Double MAX_BACKGROUND_CELL = (double) (1000);
    private static final Double MIN_BACKGROUND_CELL = (double) (-1000);

    private final CampaignRepository campaignRepository;
    private final CampaignDifficultyRepository campaignDifficultyRepository;
    private final CampaignDifficultyItemRepository campaignDifficultyItemRepository;
    private final CampaignCompletionItemRepository campaignCompletionItemRepository;
    private final CampaignTagRepository campaignTagRepository;
    private final CampaignTagLinkRepository campaignTagLinkRepository;
    private final UserCampaignRepository userCampaignRepository;
    private final CampaignVoteRepository campaignVoteRepository;
    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final CategoryRepository categoryRepository;
    private final StaffUserRepository staffUserRepository;
    private final DuplicateUserService duplicateUserService;
    private final CampaignEvaluationService campaignEvaluationService;
    private final CampaignScoreGate campaignScoreGate;
    private final PlaylistService playlistService;
    private final CdnProperties cdnProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final CampaignAccess access;
    private final CampaignMapper mapper;

    public Page<CampaignResponse> findCampaigns(CampaignFilter filter, Long viewerId, boolean privileged,
            Pageable pageable) {
        return findParticipantCampaigns(null, filter, viewerId, privileged, pageable);
    }

    public Page<CampaignResponse> findParticipantCampaigns(Long resolvedParticipantId, CampaignFilter filter,
            Long viewerId, boolean privileged, Pageable pageable) {
        boolean hasStatus = filter.status() != null && !filter.status().isEmpty();
        boolean hasTags = filter.tagIds() != null && !filter.tagIds().isEmpty();
        Collection<CampaignStatus> statusArg = hasStatus ? filter.status()
                : List.of(CampaignStatus.PUBLISHED, CampaignStatus.EDITING, CampaignStatus.CURATED);
        Collection<UUID> tagArg = hasTags ? filter.tagIds() : List.of(new UUID(0L, 0L));
        String searchArg = filter.search() != null && !filter.search().isBlank() ? filter.search().trim() : null;
        Collection<UserCampaignStatus> progressArg = filter.progressStatus() != null
                && !filter.progressStatus().isEmpty()
                        ? filter.progressStatus()
                        : UserCampaignStatus.PARTICIPATING;
        Long resolvedViewerId = viewerId != null ? duplicateUserService.resolvePrimaryUserId(viewerId) : null;
        Long resolvedMemberId = filter.memberId() != null
                ? duplicateUserService.resolvePrimaryUserId(filter.memberId())
                : null;
        return mapper.toResponsePage(
                campaignRepository.findFiltered(hasStatus, statusArg, filter.creatorId(), resolvedMemberId,
                        hasTags, tagArg,
                        CampaignStatus.DRAFT, resolvedViewerId, privileged,
                        CampaignCollaboratorStatus.ACCEPTED, searchArg, filter.official(), filter.loved(),
                        resolvedParticipantId, progressArg,
                        withSortExpressions(pageable)),
                resolvedViewerId);
    }

    private static final Map<String, String> SORT_EXPRESSIONS = Map.of(
            "publishedAt", "COALESCE(c.publishedAt, c.createdAt)",
            "totalXp", "rt.totalXp",
            "totalRewardCount", "rt.totalRewards",
            "lovedAt", "c.lovedAt");

    private static final Set<String> NULLABLE_SORTS = Set.of("lovedAt");

    static final Sort PROGRESS_SORT = SortAliases.nullsLast(JpaSort.unsafe(Sort.Direction.DESC, "puc.completedAt")
            .andUnsafe(Sort.Direction.DESC, "(SELECT COUNT(ucs) FROM UserCampaignScore ucs JOIN ucs.campaignDifficulty pcd"
                    + " WHERE ucs.campaign = c AND ucs.user = puc.user AND ucs.active = true"
                    + " AND pcd.active = true AND pcd.barrier = false) * 1.0"
                    + " / NULLIF((SELECT COUNT(td) FROM CampaignDifficulty td"
                    + " WHERE td.campaign = c AND td.active = true AND td.barrier = false), 0)"))
            .and(JpaSort.unsafe(Sort.Direction.ASC, "c.name"));

    private static Pageable withSortExpressions(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (order.getProperty().equals("progress")) {
                return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), PROGRESS_SORT);
            }
            String expression = SORT_EXPRESSIONS.get(order.getProperty());
            if (expression == null) {
                continue;
            }
            JpaSort sort = JpaSort.unsafe(order.getDirection(), expression);
            Sort resolved = NULLABLE_SORTS.contains(order.getProperty())
                    ? SortAliases.nullsLast(sort).and(JpaSort.unsafe(Sort.Direction.ASC, "c.name"))
                    : sort;
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), resolved);
        }
        return pageable;
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
        Campaign campaign = access.loadActiveCampaign(campaignId);
        if (access.isDraftHiddenFrom(campaign, viewerId, privileged)) {
            throw new ResourceNotFoundException("Campaign", campaignId);
        }
        return mapper.toDetailResponse(campaign, viewerVoteFor(campaignId, viewerId));
    }

    public CampaignDetailResponse findCampaignBySlug(String slug, Long viewerId, boolean privileged) {
        Campaign campaign = campaignRepository.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", slug));
        if (access.isDraftHiddenFrom(campaign, viewerId, privileged)) {
            throw new ResourceNotFoundException("Campaign", slug);
        }
        return mapper.toDetailResponse(campaign, viewerVoteFor(campaign.getId(), viewerId));
    }

    public CampaignResponse getCampaignSummary(UUID campaignId) {
        return mapper.toCampaignResponse(access.loadActiveCampaign(campaignId));
    }

    public CampaignDifficultyResponse getCampaignNode(UUID campaignDifficultyId) {
        return mapper.toNodeResponse(access.loadActiveDifficulty(campaignDifficultyId));
    }

    @Transactional
    public CampaignResponse createCampaign(CampaignEditor editor, CreateCampaignRequest request) {
        if (!editor.privileged()) {
            assertImageryIsCdnHosted(request.getBackgroundUrl(), request.getIconUrl());
            request.setCreatorId(access.resolveEditorId(editor));
            request.setCreatorAlias(null);
        }
        User creator = null;
        if (request.getCreatorId() != null) {
            creator = userRepository.requireActive(request.getCreatorId());
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
                .background(resolveBackgroundPlacement(request.getBackground()))
                .iconUrl(request.getIconUrl())
                .build();

        campaign = campaignRepository.save(campaign);

        if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
            replaceTagLinks(campaign, request.getTagIds());
        }

        return mapper.toCampaignResponse(campaign);
    }

    @Transactional
    public CampaignResponse updateCampaign(CampaignEditor editor, UUID campaignId, UpdateCampaignRequest request) {
        if (!editor.privileged()) {
            assertImageryIsCdnHosted(request.getBackgroundUrl(), request.getIconUrl());
        }
        Campaign campaign = access.editableDraftCampaign(editor, campaignId);

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
        if (request.getCompletionMode() != null
                && request.getCompletionMode() != campaign.getCompletionMode()) {
            campaign.setCompletionMode(request.getCompletionMode());
            if (campaign.getStatus() != CampaignStatus.DRAFT) {
                validateTerminalNodes(campaign);
                campaignEvaluationService.recomputeCampaignCompletion(campaign.getId());
            }
        }
        if (request.getPlaylistExportEnabled() != null) {
            campaign.setPlaylistExportEnabled(request.getPlaylistExportEnabled());
        }
        if (request.getBackgroundUrl() != null) {
            campaign.setBackgroundUrl(request.getBackgroundUrl());
        }
        if (request.getBackground() != null) {
            campaign.setBackground(resolveBackgroundPlacement(request.getBackground()));
        }
        if (request.getBackgroundColor() != null) {
            campaign.setBackgroundColor(request.getBackgroundColor());
        }
        if (request.getIconUrl() != null) {
            campaign.setIconUrl(request.getIconUrl());
        }
        if (request.getCompletionXp() != null) {
            if (Math.signum(request.getCompletionXp()) < 0) {
                throw new ValidationException("completionXp", "must be non-negative");
            }
            campaign.setCompletionXp(request.getCompletionXp());
        }
        if (request.getCreatorAlias() != null) {
            campaign.setCreatorAlias(request.getCreatorAlias());
        }
        if (request.getTagIds() != null) {
            replaceTagLinks(campaign, request.getTagIds());
        }

        playlistService.evictCampaignPlaylist(campaign.getId());
        return mapper.toCampaignResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignResponse publish(CampaignEditor editor, UUID campaignId) {
        Campaign campaign = access.ownedDraftCampaign(editor, campaignId);
        if (campaign.getStatus() != CampaignStatus.DRAFT && campaign.getStatus() != CampaignStatus.EDITING) {
            throw new ValidationException("Only draft or editing campaigns can be published");
        }
        validateTerminalNodes(campaign);
        List<CampaignDifficulty> dirty = campaignDifficultyRepository.findDirtyByCampaign(campaign.getId());
        if (!dirty.isEmpty()) {
            Set<UUID> changed = dirty.stream().map(CampaignDifficulty::getId).collect(Collectors.toSet());
            campaignEvaluationService.recomputeAfterRequirementChange(campaign, changed);
            dirty.forEach(d -> d.setRequirementDirty(false));
            campaignDifficultyRepository.saveAll(dirty);
        }
        campaign.setStatus(CampaignStatus.PUBLISHED);
        if (campaign.getPublishedAt() == null) {
            campaign.setPublishedAt(Instant.now());
        }
        return mapper.toCampaignResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignResponse startEditing(UUID campaignId) {
        Campaign campaign = access.loadActiveCampaign(campaignId);
        if (campaign.getStatus() != CampaignStatus.PUBLISHED) {
            throw new ValidationException("Only published campaigns can enter editing mode");
        }
        campaign.setStatus(CampaignStatus.EDITING);
        return mapper.toCampaignResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignResponse markCurated(UUID campaignId, UUID curatorStaffId) {
        StaffUser curator = requireCurator(curatorStaffId, "curate");
        Campaign campaign = access.loadActiveCampaign(campaignId);
        if (campaign.getStatus() != CampaignStatus.PUBLISHED && campaign.getStatus() != CampaignStatus.EDITING) {
            throw new ValidationException("Only published or editing campaigns can be curated");
        }
        validateTerminalNodes(campaign);
        campaign.setStatus(CampaignStatus.CURATED);
        campaign.setCuratedAt(Instant.now());
        campaign.setCuratedBy(curator);
        Campaign saved = campaignRepository.save(campaign);
        campaignEvaluationService.applyCuratedTransition(saved.getId());
        eventPublisher.publishEvent(new CampaignDistinctionEvent(saved.getId()));
        return mapper.toCampaignResponse(saved);
    }

    @Transactional
    public CampaignResponse setLoved(UUID campaignId, boolean loved, UUID curatorStaffId) {
        StaffUser curator = requireCurator(curatorStaffId, "change loved status");
        Campaign campaign = access.loadActiveCampaign(campaignId);
        if (loved && campaign.getStatus() == CampaignStatus.DRAFT) {
            throw new ValidationException("Draft campaigns cannot be loved");
        }
        campaign.setLoved(loved);
        campaign.setLovedAt(loved ? Instant.now() : null);
        campaign.setLovedBy(loved ? curator : null);
        Campaign saved = campaignRepository.save(campaign);
        if (loved) {
            eventPublisher.publishEvent(new CampaignDistinctionEvent(saved.getId()));
        }
        return mapper.toCampaignResponse(saved);
    }

    @Transactional
    public CampaignResponse uncurate(UUID campaignId, UUID curatorStaffId) {
        requireCurator(curatorStaffId, "uncurate");
        Campaign campaign = access.loadActiveCampaign(campaignId);
        if (campaign.getStatus() != CampaignStatus.CURATED) {
            throw new ValidationException("Only curated campaigns can be uncurated");
        }
        campaign.setStatus(CampaignStatus.PUBLISHED);
        campaign.setCuratedAt(null);
        campaign.setCuratedBy(null);
        return mapper.toCampaignResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignResponse unpublish(CampaignEditor editor, UUID campaignId) {
        Campaign campaign = access.loadActiveCampaign(campaignId);
        Long resolvedUserId = access.resolveEditorId(editor);
        if (campaign.getCreator() == null || !resolvedUserId.equals(campaign.getCreator().getId())) {
            throw new ValidationException("Only the campaign creator can perform this action");
        }
        if (campaign.getStatus() != CampaignStatus.PUBLISHED && campaign.getStatus() != CampaignStatus.EDITING) {
            throw new ValidationException("Only a published campaign can be unpublished");
        }
        campaign.setStatus(CampaignStatus.DRAFT);
        return mapper.toCampaignResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public void deactivateCampaign(CampaignEditor editor, UUID campaignId) {
        Campaign campaign = access.ownedDraftCampaign(editor, campaignId);
        campaign.setActive(false);
        campaignRepository.save(campaign);
    }

    private StaffUser requireCurator(UUID staffId, String action) {
        StaffUser curator = staffId != null
                ? staffUserRepository.findByIdAndActiveTrue(staffId).orElse(null)
                : null;
        if (curator == null || !isCurator(curator)) {
            throw new ValidationException("Only campaign curators or admins can " + action);
        }
        return curator;
    }

    private static boolean isCurator(StaffUser staff) {
        return staff != null
                && (staff.getRole() == StaffRole.CAMPAIGN_CURATOR || staff.getRole() == StaffRole.ADMIN);
    }

    @Transactional
    public CampaignResponse setOfficial(UUID campaignId, boolean official) {
        Campaign campaign = access.loadActiveCampaign(campaignId);
        campaign.setOfficial(official);
        return mapper.toCampaignResponse(campaignRepository.save(campaign));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CampaignResponse replaceCampaignMedia(CampaignEditor editor, UUID campaignId, CampaignMediaKind kind,
            MultipartFile file) {
        if (kind.node()) {
            throw new IllegalArgumentException(kind + " is not campaign media");
        }
        String url = file != null ? access.storeMedia(editor, campaignId, kind, file) : null;
        CampaignResponse response = transactionTemplate.execute(status -> {
            Campaign campaign = access.editableDraftCampaign(editor, campaignId);
            if (kind == CampaignMediaKind.ICON) {
                campaign.setIconUrl(url);
            } else {
                campaign.setBackgroundUrl(url);
                if (url == null) {
                    campaign.setBackground(null);
                }
            }
            return mapper.toCampaignResponse(campaignRepository.save(campaign));
        });
        if (file == null) {
            access.deleteMedia(kind, campaignId);
        }
        return response;
    }

    private CampaignBackgroundPlacement resolveBackgroundPlacement(CampaignBackgroundPlacement requested) {
        if (requested == null
                || (requested.getSize() == null && requested.getX() == null && requested.getY() == null)) {
            return null;
        }
        if (requested.getSize() == null || requested.getX() == null || requested.getY() == null) {
            throw new ValidationException("background",
                    "size, x and y must be supplied together; send an empty object to clear");
        }
        assertBackgroundRange("background.size", requested.getSize(),
                MIN_BACKGROUND_SCALE, MAX_BACKGROUND_SCALE);
        assertBackgroundRange("background.x", requested.getX(),
                MIN_BACKGROUND_CELL, MAX_BACKGROUND_CELL);
        assertBackgroundRange("background.y", requested.getY(),
                MIN_BACKGROUND_CELL, MAX_BACKGROUND_CELL);
        return CampaignBackgroundPlacement.builder()
                .size(requested.getSize())
                .x(requested.getX())
                .y(requested.getY())
                .build();
    }

    private static void assertBackgroundRange(String field, Double value,
            Double min, Double max) {
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new ValidationException(field,
                    "must be between " + min + " and " + max);
        }
    }

    private void assertImageryIsCdnHosted(String... urls) {
        for (String url : urls) {
            if (url != null && !url.isBlank() && !cdnProperties.isCdnUrl(url)) {
                throw new ValidationException(
                        "Campaign imagery must be uploaded through the campaign image endpoints");
            }
        }
    }

    @Transactional
    public List<CampaignItemAwardResponse> setDifficultyItem(CampaignEditor editor, UUID campaignDifficultyId,
            SetCampaignItemRequest request) {
        CampaignDifficulty difficulty = access.editableDraftDifficulty(editor, campaignDifficultyId);
        CampaignAccess.ensureEditable(difficulty.getCampaign());
        Item item = loadRewardItem(request, difficulty.getCampaign());
        CampaignDifficultyItem.CampaignDifficultyItemId key = new CampaignDifficultyItem.CampaignDifficultyItemId(
                difficulty.getId(), item.getId());
        CampaignDifficultyItem link = campaignDifficultyItemRepository.findById(key)
                .orElseGet(() -> CampaignDifficultyItem.builder()
                        .id(key)
                        .campaignDifficulty(difficulty)
                        .item(item)
                        .build());
        link.setQuantity(request.getQuantity() != null ? request.getQuantity() : 1);
        campaignDifficultyItemRepository.save(link);
        return mapper.loadDifficultyItems(difficulty.getId());
    }

    @Transactional
    public List<CampaignItemAwardResponse> removeDifficultyItem(CampaignEditor editor, UUID campaignDifficultyId,
            UUID itemId) {
        CampaignDifficulty difficulty = access.editableDraftDifficulty(editor, campaignDifficultyId);
        campaignDifficultyItemRepository.deleteByCampaignDifficulty_IdAndItem_Id(difficulty.getId(), itemId);
        return mapper.loadDifficultyItems(difficulty.getId());
    }

    @Transactional
    public List<CampaignItemAwardResponse> setCompletionItem(CampaignEditor editor, UUID campaignId,
            SetCampaignItemRequest request) {
        Campaign campaign = access.editableDraftCampaign(editor, campaignId);
        CampaignAccess.ensureEditable(campaign);
        Item item = loadRewardItem(request, campaign);
        CampaignCompletionItem.CampaignCompletionItemId key = new CampaignCompletionItem.CampaignCompletionItemId(
                campaign.getId(), item.getId());
        CampaignCompletionItem link = campaignCompletionItemRepository.findById(key)
                .orElseGet(() -> CampaignCompletionItem.builder()
                        .id(key)
                        .campaign(campaign)
                        .item(item)
                        .build());
        link.setQuantity(request.getQuantity() != null ? request.getQuantity() : 1);
        campaignCompletionItemRepository.save(link);
        return mapper.loadCompletionItems(campaign.getId());
    }

    @Transactional
    public List<CampaignItemAwardResponse> removeCompletionItem(CampaignEditor editor, UUID campaignId,
            UUID itemId) {
        Campaign campaign = access.editableDraftCampaign(editor, campaignId);
        campaignCompletionItemRepository.deleteByCampaign_IdAndItem_Id(campaign.getId(), itemId);
        return mapper.loadCompletionItems(campaign.getId());
    }

    private Item loadRewardItem(SetCampaignItemRequest request, Campaign campaign) {
        Item item = itemRepository.findByIdAndActiveTrue(request.getItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Item", request.getItemId()));
        if (!item.isTradeable() && !campaign.isOfficial() && !ItemAwardService.isActiveCrateSentinel(item)) {
            throw new ValidationException(
                    "Only official campaigns can reward untradeable items");
        }
        if (!item.isObtainableAt(Instant.now())) {
            throw new ValidationException(
                    "'" + item.getName() + "' can no longer be handed out as a reward");
        }
        return item;
    }

    @Transactional
    public UserCampaignResponse startCampaign(Long userId, UUID campaignId) {
        Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(userId);
        Campaign campaign = access.loadActiveCampaign(campaignId);
        if (campaign.getStatus() == CampaignStatus.DRAFT) {
            throw new ValidationException("Cannot start a draft campaign");
        }
        User user = userRepository.requireActive(resolvedUserId);

        UserCampaign existing = userCampaignRepository
                .findByUser_IdAndCampaign_IdAndActiveTrue(resolvedUserId, campaignId)
                .orElse(null);
        if (existing != null) {
            if (existing.getStatus() == UserCampaignStatus.ABANDONED) {
                existing.setStatus(UserCampaignStatus.IN_PROGRESS);
                existing.setStartedAt(Instant.now());
                existing.setCompletedAt(null);
                UserCampaign revived = userCampaignRepository.save(existing);
                publishLegacyBackfill(campaign, resolvedUserId, campaignId);
                campaignScoreGate.refresh();
                return mapper.toUserCampaignResponse(revived);
            }
            return mapper.toUserCampaignResponse(existing);
        }

        UserCampaign userCampaign = UserCampaign.builder()
                .user(user)
                .campaign(campaign)
                .status(UserCampaignStatus.IN_PROGRESS)
                .startedAt(Instant.now())
                .build();
        UserCampaign saved = userCampaignRepository.save(userCampaign);
        publishLegacyBackfill(campaign, resolvedUserId, campaignId);
        campaignScoreGate.refresh();
        return mapper.toUserCampaignResponse(saved);
    }

    private void publishLegacyBackfill(Campaign campaign, Long userId, UUID campaignId) {
        if (campaign.isLegacy()) {
            eventPublisher.publishEvent(new LegacyCampaignBackfillEvent(userId, campaignId));
        }
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
        campaignScoreGate.refresh();
    }

    @Transactional
    public CampaignVoteResponse vote(Long userId, UUID campaignId, CampaignVoteDirection direction) {
        Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(userId);
        Campaign campaign = campaignRepository.findByIdAndActiveTrueForUpdate(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));
        if (campaign.getStatus() == CampaignStatus.DRAFT) {
            throw new ValidationException("Cannot vote on a draft campaign");
        }
        User user = userRepository.requireActive(resolvedUserId);
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

    public List<CampaignTagResponse> listTags() {
        return campaignTagRepository.findByActiveTrue().stream().map(CampaignMapper::toTagResponse).toList();
    }

    public List<CampaignTagResponse> listTagsByKind(CampaignTagKind kind) {
        return campaignTagRepository.findByKindAndActiveTrue(kind).stream()
                .map(CampaignMapper::toTagResponse)
                .toList();
    }

    @Transactional
    public CampaignTagResponse createTag(CreateCampaignTagRequest request, UUID actorStaffId) {
        boolean isCurator = actorStaffId != null
                && staffUserRepository.findByIdAndActiveTrue(actorStaffId)
                        .filter(CampaignService::isCurator)
                        .isPresent();

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
            category = categoryRepository.requireActive(request.getCategoryId());
        }

        CampaignTag tag = CampaignTag.builder()
                .kind(request.getKind())
                .name(request.getName().trim())
                .category(category)
                .system(false)
                .build();
        return CampaignMapper.toTagResponse(campaignTagRepository.save(tag));
    }

    private String normalizeAndValidateSlug(String requested, String fallbackName, UUID excludeId) {
        String candidate = requested != null && !requested.isBlank()
                ? requested.trim().toLowerCase()
                : Slugs.slugify(fallbackName);
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

    private void validateTerminalNodes(Campaign campaign) {
        List<CampaignDifficulty> difficulties = campaignDifficultyRepository
                .findByCampaign_IdAndActiveTrue(campaign.getId());
        if (difficulties.stream().noneMatch(d -> !d.isBarrier())) {
            throw new ValidationException("Campaign must have at least one difficulty");
        }
        if (campaign.getCompletionMode() == CampaignCompletionMode.ALL) {
            return;
        }
        if (difficulties.stream().noneMatch(d -> d.isTerminal() && !d.isBarrier())) {
            throw new ValidationException("At least one node must be flagged as terminal");
        }
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
}
