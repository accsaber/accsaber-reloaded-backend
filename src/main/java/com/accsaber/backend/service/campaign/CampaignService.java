package com.accsaber.backend.service.campaign;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.config.CdnProperties;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.projection.UserMapDifficultyBests;
import com.accsaber.backend.model.dto.request.campaign.AddCampaignBarrierRequest;
import com.accsaber.backend.model.dto.request.campaign.AddCampaignDifficultyRequest;
import com.accsaber.backend.model.dto.request.campaign.CampaignBound;
import com.accsaber.backend.model.dto.request.campaign.CampaignConnectionRequest;
import com.accsaber.backend.model.dto.request.campaign.CampaignFilter;
import com.accsaber.backend.model.dto.request.campaign.CampaignModifierRequirementRequest;
import com.accsaber.backend.model.dto.request.campaign.CampaignTargetRequest;
import com.accsaber.backend.model.dto.request.campaign.CampaignTextRequest;
import com.accsaber.backend.model.dto.request.campaign.CreateCampaignRequest;
import com.accsaber.backend.model.dto.request.campaign.CreateCampaignTagRequest;
import com.accsaber.backend.model.dto.request.campaign.MoveCampaignElementsRequest;
import com.accsaber.backend.model.dto.request.campaign.SetCampaignItemRequest;
import com.accsaber.backend.model.dto.request.campaign.UpdateCampaignBarrierRequest;
import com.accsaber.backend.model.dto.request.campaign.UpdateCampaignDifficultyRequest;
import com.accsaber.backend.model.dto.request.campaign.UpdateCampaignRequest;
import com.accsaber.backend.model.dto.response.campaign.BarrierProgressResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignBarrierResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignConnectionResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignDetailResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignDifficultyProgressResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignDifficultyResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignItemAwardResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignModifierRequirementResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignProgressResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignTagResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignTargetProgressResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignTargetResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignTextResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignVoteResponse;
import com.accsaber.backend.model.dto.response.campaign.CurrentMilestoneResponse;
import com.accsaber.backend.model.dto.response.campaign.UserCampaignResponse;
import com.accsaber.backend.model.dto.response.staff.PublicStaffUserResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.Modifier;
import com.accsaber.backend.model.entity.campaign.BarrierConditionType;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignBackgroundPlacement;
import com.accsaber.backend.model.entity.campaign.CampaignBarrierAffectedDifficulty;
import com.accsaber.backend.model.entity.campaign.CampaignCollaboratorStatus;
import com.accsaber.backend.model.entity.campaign.CampaignCompletionItem;
import com.accsaber.backend.model.entity.campaign.CampaignCompletionMode;
import com.accsaber.backend.model.entity.campaign.CampaignDifficulty;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyItem;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyModifier;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyModifier.CampaignDifficultyModifierId;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyPath;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyTarget;
import com.accsaber.backend.model.entity.campaign.CampaignModifierRequirement;
import com.accsaber.backend.model.entity.campaign.CampaignNodeBorderLayer;
import com.accsaber.backend.model.entity.campaign.CampaignPrerequisiteMode;
import com.accsaber.backend.model.entity.campaign.CampaignRequirementType;
import com.accsaber.backend.model.entity.campaign.CampaignRewardTotals;
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
import com.accsaber.backend.model.entity.map.MapDifficultyComplexity;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.staff.StaffRole;
import com.accsaber.backend.model.entity.staff.StaffUser;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.ModifierRepository;
import com.accsaber.backend.repository.campaign.CampaignBarrierAffectedDifficultyRepository;
import com.accsaber.backend.repository.campaign.CampaignCollaboratorRepository;
import com.accsaber.backend.repository.campaign.CampaignCompletionItemRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyItemRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyModifierRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyPathRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyTargetRepository;
import com.accsaber.backend.repository.campaign.CampaignRepository;
import com.accsaber.backend.repository.campaign.CampaignRewardTotalsRepository;
import com.accsaber.backend.repository.campaign.CampaignTagLinkRepository;
import com.accsaber.backend.repository.campaign.CampaignTagRepository;
import com.accsaber.backend.repository.campaign.CampaignTextRepository;
import com.accsaber.backend.repository.campaign.CampaignVoteRepository;
import com.accsaber.backend.repository.campaign.UserCampaignRepository;
import com.accsaber.backend.repository.campaign.UserCampaignScoreRepository;
import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.repository.map.MapDifficultyComplexityRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreModifierLinkRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.staff.StaffUserRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.infra.ModifierService;
import com.accsaber.backend.service.item.ItemService;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.player.RichTextSanitizer;
import com.accsaber.backend.service.playlist.PlaylistService;
import com.accsaber.backend.service.staff.StaffMapper;
import com.accsaber.backend.util.CampaignModifierRule;
import com.accsaber.backend.util.CampaignScoreMetrics;
import com.accsaber.backend.util.MapDifficultyMetrics;
import com.accsaber.backend.util.ScoreModifierIndex;
import com.accsaber.backend.util.Slugs;
import com.accsaber.backend.util.WilsonScore;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampaignService {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final int MAX_PROGRESS_BULK_IDS = 50;
    private static final int MAX_DIFFICULTIES_PER_CAMPAIGN = 200;
    private static final int MAX_BARRIERS_PER_CAMPAIGN = 100;
    private static final int MAX_TEXTS_PER_CAMPAIGN = 100;
    private static final int MAX_TEXT_CONTENT_LENGTH = 4000;
    private static final Double MAX_BACKGROUND_SCALE = (double) (1000);
    private static final Double MIN_BACKGROUND_SCALE = 1.0;
    private static final Double MAX_BACKGROUND_CELL = (double) (1000);
    private static final Double MIN_BACKGROUND_CELL = (double) (-1000);

    private final CampaignRepository campaignRepository;
    private final CampaignCollaboratorRepository campaignCollaboratorRepository;
    private final CampaignDifficultyRepository campaignDifficultyRepository;
    private final CampaignDifficultyPathRepository campaignDifficultyPathRepository;
    private final CampaignBarrierAffectedDifficultyRepository barrierAffectedRepository;
    private final CampaignTextRepository campaignTextRepository;
    private final RichTextSanitizer richTextSanitizer;
    private final CampaignDifficultyItemRepository campaignDifficultyItemRepository;
    private final CampaignDifficultyModifierRepository campaignDifficultyModifierRepository;
    private final CampaignDifficultyTargetRepository campaignDifficultyTargetRepository;
    private final CampaignRewardTotalsRepository campaignRewardTotalsRepository;
    private final CampaignCompletionItemRepository campaignCompletionItemRepository;
    private final CampaignTagRepository campaignTagRepository;
    private final CampaignTagLinkRepository campaignTagLinkRepository;
    private final UserCampaignRepository userCampaignRepository;
    private final UserCampaignScoreRepository userCampaignScoreRepository;
    private final CampaignVoteRepository campaignVoteRepository;
    private final UserRepository userRepository;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final MapDifficultyComplexityRepository mapDifficultyComplexityRepository;
    private final ScoreRepository scoreRepository;
    private final ScoreModifierLinkRepository scoreModifierLinkRepository;
    private final ModifierRepository modifierRepository;
    private final ItemRepository itemRepository;
    private final CategoryRepository categoryRepository;
    private final StaffUserRepository staffUserRepository;
    private final DuplicateUserService duplicateUserService;
    private final CampaignEvaluationService campaignEvaluationService;
    private final com.accsaber.backend.service.score.CampaignScoreGate campaignScoreGate;
    private final com.accsaber.backend.service.map.MapImportService mapImportService;
    private final PlaylistService playlistService;
    private final CdnProperties cdnProperties;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public Page<CampaignResponse> findCampaigns(CampaignFilter filter, Long viewerId, boolean privileged,
            Pageable pageable) {
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
        Long resolvedParticipantId = filter.participantId() != null
                ? duplicateUserService.resolvePrimaryUserId(filter.participantId())
                : null;
        return paginateAsResponses(
                campaignRepository.findFiltered(hasStatus, statusArg, filter.creatorId(), hasTags, tagArg,
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

    private static Pageable withSortExpressions(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            String expression = SORT_EXPRESSIONS.get(order.getProperty());
            if (expression == null) {
                continue;
            }
            JpaSort sort = JpaSort.unsafe(order.getDirection(), expression);
            Sort resolved = NULLABLE_SORTS.contains(order.getProperty())
                    ? nullsLast(sort).and(JpaSort.unsafe(Sort.Direction.ASC, "c.name"))
                    : sort;
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), resolved);
        }
        return pageable;
    }

    private static Sort nullsLast(Sort sort) {
        return Sort.by(sort.stream().map(order -> order.with(Sort.NullHandling.NULLS_LAST)).toList());
    }

    private Page<CampaignResponse> paginateAsResponses(Page<Campaign> page, Long resolvedViewerId) {
        if (!page.hasContent()) {
            return page.map(c -> toCampaignResponse(c, CampaignRow.empty()));
        }
        List<UUID> ids = page.getContent().stream().map(Campaign::getId).distinct().toList();
        Map<UUID, List<CampaignTagResponse>> tagsByCampaign = loadTagsByCampaignIds(ids);
        Map<UUID, Integer> diffCountByCampaign = countMap(campaignDifficultyRepository.countActiveByCampaignIds(ids));
        Map<UUID, CampaignVoteDirection> votesByCampaign = loadViewerVotes(resolvedViewerId, ids);
        Map<UUID, List<CampaignItemAwardResponse>> completionItemsByCampaign = loadCompletionItemsBulk(ids);
        Map<UUID, CampaignRewards> rewardsByCampaign = loadRewardsBulk(ids, completionItemsByCampaign);
        Map<UUID, PublicStaffUserResponse> staffRefs = loadStaffRefs(page.getContent());
        return page.map(c -> toCampaignResponse(c, new CampaignRow(
                tagsByCampaign.getOrDefault(c.getId(), List.of()),
                diffCountByCampaign.getOrDefault(c.getId(), 0),
                votesByCampaign.get(c.getId()),
                completionItemsByCampaign.getOrDefault(c.getId(), List.of()),
                rewardsByCampaign.getOrDefault(c.getId(), CampaignRewards.none()),
                staffRefs)));
    }

    private Map<UUID, CampaignRewards> loadRewardsBulk(Collection<UUID> campaignIds,
            Map<UUID, List<CampaignItemAwardResponse>> completionItemsByCampaign) {
        Map<UUID, CampaignRewardTotals> totalsByCampaign = campaignRewardTotalsRepository
                .findByCampaignIdIn(campaignIds).stream()
                .collect(Collectors.toMap(CampaignRewardTotals::getCampaignId, t -> t));
        Map<UUID, Map<UUID, CampaignItemAwardResponse>> merged = new HashMap<>();
        for (CampaignDifficultyItem link : campaignDifficultyItemRepository.findActiveByCampaignIds(campaignIds)) {
            accumulateReward(merged, link.getCampaignDifficulty().getCampaign().getId(), toItemAward(link));
        }
        completionItemsByCampaign.forEach((campaignId, awards) -> awards
                .forEach(award -> accumulateReward(merged, campaignId, award)));

        Map<UUID, CampaignRewards> rewards = new HashMap<>(campaignIds.size() * 2);
        for (UUID campaignId : campaignIds) {
            rewards.put(campaignId, new CampaignRewards(totalsByCampaign.get(campaignId),
                    List.copyOf(merged.getOrDefault(campaignId, Map.of()).values())));
        }
        return rewards;
    }

    private static void accumulateReward(Map<UUID, Map<UUID, CampaignItemAwardResponse>> merged,
            UUID campaignId, CampaignItemAwardResponse award) {
        merged.computeIfAbsent(campaignId, k -> new LinkedHashMap<>())
                .merge(award.getItemId(), award, (existing, added) -> CampaignItemAwardResponse.builder()
                        .itemId(existing.getItemId())
                        .itemName(existing.getItemName())
                        .quantity(existing.getQuantity() + added.getQuantity())
                        .build());
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

    public CampaignResponse getCampaignSummary(UUID campaignId) {
        return toCampaignResponse(loadActiveCampaign(campaignId));
    }

    public CampaignDifficultyResponse getCampaignNode(UUID campaignDifficultyId) {
        CampaignDifficulty difficulty = loadActiveDifficulty(campaignDifficultyId);
        List<CampaignConnectionResponse> connections = toConnections(campaignDifficultyPathRepository
                .findByCampaignDifficulty_IdAndActiveTrue(difficulty.getId()));
        return toCampaignDifficultyResponse(difficulty, new NodeAssets(connections,
                loadDifficultyItems(difficulty.getId()),
                loadDifficultyModifiers(difficulty.getId()),
                loadTargets(difficulty.getId()),
                loadComplexity(difficulty.getMapDifficulty().getId())));
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
                .background(resolveBackgroundPlacement(request.getBackground()))
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
        return toCampaignResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignResponse publish(UUID campaignId) {
        Campaign campaign = loadActiveCampaign(campaignId);
        if (campaign.getStatus() != CampaignStatus.DRAFT && campaign.getStatus() != CampaignStatus.EDITING) {
            throw new ValidationException("Only draft or editing campaigns can be published");
        }
        validateTerminalNodes(campaign);
        List<CampaignDifficulty> dirty = campaignDifficultyRepository
                .findByCampaign_IdAndActiveTrueAndRequirementDirtyTrue(campaign.getId());
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
    public CampaignResponse markCurated(UUID campaignId, UUID curatorStaffId) {
        StaffUser curator = requireCurator(curatorStaffId, "curate");
        Campaign campaign = loadActiveCampaign(campaignId);
        if (campaign.getStatus() != CampaignStatus.PUBLISHED && campaign.getStatus() != CampaignStatus.EDITING) {
            throw new ValidationException("Only published or editing campaigns can be curated");
        }
        validateTerminalNodes(campaign);
        campaign.setStatus(CampaignStatus.CURATED);
        campaign.setCuratedAt(Instant.now());
        campaign.setCuratedBy(curator);
        Campaign saved = campaignRepository.save(campaign);
        campaignEvaluationService.applyCuratedTransition(saved.getId());
        return toCampaignResponse(saved);
    }

    @Transactional
    public CampaignResponse setLoved(UUID campaignId, boolean loved, UUID curatorStaffId) {
        StaffUser curator = requireCurator(curatorStaffId, "change loved status");
        Campaign campaign = loadActiveCampaign(campaignId);
        if (loved && campaign.getStatus() == CampaignStatus.DRAFT) {
            throw new ValidationException("Draft campaigns cannot be loved");
        }
        campaign.setLoved(loved);
        campaign.setLovedAt(loved ? Instant.now() : null);
        campaign.setLovedBy(loved ? curator : null);
        return toCampaignResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignResponse uncurate(UUID campaignId, UUID curatorStaffId) {
        requireCurator(curatorStaffId, "uncurate");
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
        Campaign campaign = loadActiveCampaign(campaignId);
        campaign.setOfficial(official);
        return toCampaignResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignResponse setBackgroundUrl(UUID campaignId, String backgroundUrl) {
        return applyBackgroundUrl(loadActiveCampaign(campaignId), backgroundUrl);
    }

    @Transactional
    public CampaignResponse setBackgroundUrlAsEditor(CampaignEditor editor, UUID campaignId, String backgroundUrl) {
        return applyBackgroundUrl(editableDraftCampaign(editor, campaignId), backgroundUrl);
    }

    private CampaignResponse applyBackgroundUrl(Campaign campaign, String backgroundUrl) {
        campaign.setBackgroundUrl(backgroundUrl);
        if (backgroundUrl == null) {
            campaign.setBackground(null);
        }
        return toCampaignResponse(campaignRepository.save(campaign));
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

    private static CampaignBackgroundPlacement staticBackground(Campaign campaign) {
        CampaignBackgroundPlacement placement = campaign.getBackground();
        if (placement == null || placement.getSize() == null || placement.getX() == null
                || placement.getY() == null) {
            return null;
        }
        return placement;
    }

    @Transactional
    public CampaignResponse setIconUrl(UUID campaignId, String iconUrl) {
        Campaign campaign = loadActiveCampaign(campaignId);
        campaign.setIconUrl(iconUrl);
        return toCampaignResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignResponse setIconUrlAsEditor(CampaignEditor editor, UUID campaignId, String iconUrl) {
        Campaign campaign = editableDraftCampaign(editor, campaignId);
        campaign.setIconUrl(iconUrl);
        return toCampaignResponse(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignResponse createCampaignAsEditor(CampaignEditor editor, CreateCampaignRequest request) {
        assertImageryIsCdnHosted(editor, request.getBackgroundUrl(), request.getIconUrl());
        request.setCreatorId(resolveEditorId(editor));
        request.setCreatorAlias(null);
        return createCampaign(request);
    }

    @Transactional
    public CampaignResponse updateCampaignAsEditor(CampaignEditor editor, UUID campaignId,
            UpdateCampaignRequest request) {
        assertImageryIsCdnHosted(editor, request.getBackgroundUrl(), request.getIconUrl());
        assertCanEditDraft(loadActiveCampaign(campaignId), editor);
        return updateCampaign(campaignId, request);
    }

    @Transactional
    public CampaignResponse publishAsEditor(CampaignEditor editor, UUID campaignId) {
        assertOwnsDraft(loadActiveCampaign(campaignId), editor);
        return publish(campaignId);
    }

    @Transactional
    public CampaignResponse unpublishAsEditor(CampaignEditor editor, UUID campaignId) {
        Campaign campaign = loadActiveCampaign(campaignId);
        Long resolvedUserId = resolveEditorId(editor);
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
    public void deactivateCampaignAsEditor(CampaignEditor editor, UUID campaignId) {
        Campaign campaign = ownedDraftCampaign(editor, campaignId);
        campaign.setActive(false);
        campaignRepository.save(campaign);
    }

    @Transactional
    public CampaignDifficultyResponse addDifficultyAsEditor(CampaignEditor editor, UUID campaignId,
            AddCampaignDifficultyRequest request) {
        editableDraftCampaign(editor, campaignId);
        assertUnderCap(editor,
                () -> campaignDifficultyRepository.countByCampaign_IdAndBarrierFalseAndActiveTrue(campaignId),
                MAX_DIFFICULTIES_PER_CAMPAIGN, "difficulties");
        return addDifficulty(campaignId, request);
    }

    @Transactional
    public CampaignDifficultyResponse updateDifficultyAsEditor(CampaignEditor editor, UUID campaignDifficultyId,
            UpdateCampaignDifficultyRequest request) {
        CampaignDifficulty difficulty = loadActiveDifficulty(campaignDifficultyId);
        assertCanEditDraft(difficulty.getCampaign(), editor);
        return applyDifficultyUpdate(difficulty, request);
    }

    @Transactional
    public void removeDifficultyAsEditor(CampaignEditor editor, UUID campaignId, UUID campaignDifficultyId) {
        editableDraftCampaign(editor, campaignId);
        removeDifficulty(campaignId, campaignDifficultyId);
    }

    private Campaign ownedDraftCampaign(CampaignEditor editor, UUID campaignId) {
        Campaign campaign = loadActiveCampaign(campaignId);
        assertOwnsDraft(campaign, editor);
        return campaign;
    }

    public void assertCanUploadCampaignMedia(CampaignEditor editor, UUID campaignId) {
        editableDraftCampaign(editor, campaignId);
    }

    public void assertCanUploadDifficultyMedia(CampaignEditor editor, UUID campaignDifficultyId) {
        editableDraftDifficulty(editor, campaignDifficultyId);
    }

    private Campaign editableDraftCampaign(CampaignEditor editor, UUID campaignId) {
        Campaign campaign = loadActiveCampaign(campaignId);
        assertCanEditDraft(campaign, editor);
        return campaign;
    }

    private CampaignDifficulty editableDraftDifficulty(CampaignEditor editor, UUID campaignDifficultyId) {
        CampaignDifficulty difficulty = loadActiveDifficulty(campaignDifficultyId);
        assertCanEditDraft(difficulty.getCampaign(), editor);
        return difficulty;
    }

    private CampaignDifficulty loadActiveDifficulty(UUID campaignDifficultyId) {
        return campaignDifficultyRepository.findByIdAndActiveTrue(campaignDifficultyId)
                .orElseThrow(() -> new ResourceNotFoundException("CampaignDifficulty", campaignDifficultyId));
    }

    private void assertOwnsDraft(Campaign campaign, CampaignEditor editor) {
        if (editor.privileged()) {
            return;
        }
        Long playerId = resolveEditorId(editor);
        if (campaign.getCreator() == null || !playerId.equals(campaign.getCreator().getId())) {
            throw new ValidationException("Only the campaign creator can perform this action");
        }
        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new ValidationException("Players can only edit campaigns in draft status; unpublish first");
        }
    }

    private void assertCanEditDraft(Campaign campaign, CampaignEditor editor) {
        if (editor.privileged()) {
            return;
        }
        Long playerId = resolveEditorId(editor);
        boolean isOwner = campaign.getCreator() != null && playerId.equals(campaign.getCreator().getId());
        if (!isOwner && !isAcceptedCollaborator(campaign.getId(), playerId)) {
            throw new ValidationException("Only the campaign owner or a collaborator can perform this action");
        }
        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new ValidationException("Players can only edit campaigns in draft status; unpublish first");
        }
    }

    private Long resolveEditorId(CampaignEditor editor) {
        if (editor.userId() == null) {
            throw new UnauthorizedException("Player authentication required");
        }
        return duplicateUserService.resolvePrimaryUserId(editor.userId());
    }

    private boolean isAcceptedCollaborator(UUID campaignId, Long playerId) {
        return campaignCollaboratorRepository.existsByCampaign_IdAndUser_IdAndStatusAndActiveTrue(
                campaignId, playerId, CampaignCollaboratorStatus.ACCEPTED);
    }

    private void assertImageryIsCdnHosted(CampaignEditor editor, String... urls) {
        if (editor.privileged()) {
            return;
        }
        for (String url : urls) {
            if (url != null && !url.isBlank() && !isCdnHosted(url)) {
                throw new ValidationException(
                        "Campaign imagery must be uploaded through the campaign image endpoints");
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
        List<CampaignTargetRequest> requestedTargets = effectiveTargets(request.getTargets(),
                request.getRequirementType(), request.getRequirementValue(), request.getRequirementValueMax());

        if (campaignDifficultyRepository.existsByCampaign_IdAndPositionXAndPositionYAndActiveTrue(
                campaignId, request.getPositionX(), request.getPositionY())) {
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

        return toCampaignDifficultyResponse(difficulty, new NodeAssets(
                echoConnections(request.getPrerequisites()),
                List.of(),
                loadDifficultyModifiers(difficulty.getId()),
                toTargetResponses(targets),
                loadComplexity(mapDifficulty.getId())));
    }

    @Transactional
    public CampaignDifficultyResponse updateDifficulty(UUID campaignDifficultyId,
            UpdateCampaignDifficultyRequest request) {
        return applyDifficultyUpdate(loadActiveDifficulty(campaignDifficultyId), request);
    }

    @Transactional
    public CampaignDifficultyResponse updateDifficultyMapAsEditor(CampaignEditor editor, UUID campaignDifficultyId,
            com.accsaber.backend.model.dto.request.map.ImportCampaignMapRequest request) {
        CampaignDifficulty difficulty = loadActiveDifficulty(campaignDifficultyId);
        assertCanEditDraft(difficulty.getCampaign(), editor);
        return applyDifficultyMapUpdate(difficulty, request,
                editor.privileged() ? null : resolveEditorId(editor));
    }

    @Transactional
    public CampaignDifficultyResponse updateDifficultyMap(UUID campaignDifficultyId,
            com.accsaber.backend.model.dto.request.map.ImportCampaignMapRequest request) {
        return applyDifficultyMapUpdate(loadActiveDifficulty(campaignDifficultyId), request, null);
    }

    private CampaignDifficultyResponse applyDifficultyMapUpdate(CampaignDifficulty difficulty,
            com.accsaber.backend.model.dto.request.map.ImportCampaignMapRequest request, Long playerId) {
        if (difficulty.isBarrier()) {
            throw new ValidationException("Barriers do not reference a map difficulty");
        }
        Campaign campaign = difficulty.getCampaign();
        ensureEditable(campaign);

        MapDifficulty current = difficulty.getMapDifficulty();
        MapDifficulty target = mapImportService.resolveCampaignMap(playerId, request,
                current != null ? current.getId() : null);
        List<CampaignConnectionResponse> connections = toConnections(campaignDifficultyPathRepository
                .findByCampaignDifficulty_IdAndActiveTrue(difficulty.getId()));
        List<CampaignItemAwardResponse> items = loadDifficultyItems(difficulty.getId());
        List<CampaignModifierRequirementResponse> modifiers = loadDifficultyModifiers(difficulty.getId());
        List<CampaignTargetResponse> targetResponses = loadTargets(difficulty.getId());
        if (current != null && target.getId().equals(current.getId())) {
            return toCampaignDifficultyResponse(difficulty,
                    new NodeAssets(connections, items, modifiers, targetResponses, loadComplexity(target.getId())));
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

        return toCampaignDifficultyResponse(difficulty,
                new NodeAssets(connections, items, modifiers, targetResponses, loadComplexity(target.getId())));
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
        ensureEditable(difficulty.getCampaign());

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
        if (request.getPositionX() != null || request.getPositionY() != null) {
            Double newX = request.getPositionX() != null ? request.getPositionX() : difficulty.getPositionX();
            Double newY = request.getPositionY() != null ? request.getPositionY() : difficulty.getPositionY();
            if ((boundChanged(difficulty.getPositionX(), newX) || boundChanged(difficulty.getPositionY(), newY))
                    && campaignDifficultyRepository.existsByCampaign_IdAndPositionXAndPositionYAndActiveTrue(
                            difficulty.getCampaign().getId(), newX, newY)) {
                throw new ValidationException("A difficulty already occupies that grid position");
            }
            difficulty.setPositionX(newX);
            difficulty.setPositionY(newY);
        }
        if (request.getXp() != null) {
            if (Math.signum(request.getXp()) < 0) {
                throw new ValidationException("xp", "must be non-negative");
            }
            difficulty.setXp(request.getXp());
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
        List<CampaignConnectionResponse> currentConnections = toConnections(campaignDifficultyPathRepository
                .findByCampaignDifficulty_IdAndActiveTrue(difficulty.getId()));
        return toCampaignDifficultyResponse(difficulty, new NodeAssets(currentConnections,
                loadDifficultyItems(difficulty.getId()),
                loadDifficultyModifiers(difficulty.getId()),
                loadTargets(difficulty.getId()),
                loadComplexity(difficulty.getMapDifficulty().getId())));
    }

    @Transactional
    public void removeDifficulty(UUID campaignId, UUID campaignDifficultyId) {
        CampaignDifficulty difficulty = loadActiveDifficulty(campaignDifficultyId);
        if (!difficulty.getCampaign().getId().equals(campaignId)) {
            throw new ResourceNotFoundException("CampaignDifficulty", campaignDifficultyId);
        }
        ensureEditable(difficulty.getCampaign());
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
    public List<CampaignItemAwardResponse> setDifficultyItemAsEditor(CampaignEditor editor, UUID campaignDifficultyId,
            SetCampaignItemRequest request) {
        CampaignDifficulty difficulty = editableDraftDifficulty(editor, campaignDifficultyId);
        return setDifficultyItem(difficulty, request);
    }

    @Transactional
    public List<CampaignItemAwardResponse> removeDifficultyItemAsEditor(CampaignEditor editor,
            UUID campaignDifficultyId,
            UUID itemId) {
        CampaignDifficulty difficulty = editableDraftDifficulty(editor, campaignDifficultyId);
        campaignDifficultyItemRepository.deleteByCampaignDifficulty_IdAndItem_Id(difficulty.getId(), itemId);
        return loadDifficultyItems(difficulty.getId());
    }

    @Transactional
    public List<CampaignItemAwardResponse> setCompletionItemAsEditor(CampaignEditor editor, UUID campaignId,
            SetCampaignItemRequest request) {
        return setCompletionItem(editableDraftCampaign(editor, campaignId), request);
    }

    @Transactional
    public List<CampaignItemAwardResponse> removeCompletionItemAsEditor(CampaignEditor editor, UUID campaignId,
            UUID itemId) {
        Campaign campaign = editableDraftCampaign(editor, campaignId);
        campaignCompletionItemRepository.deleteByCampaign_IdAndItem_Id(campaign.getId(), itemId);
        return loadCompletionItems(campaign.getId());
    }

    private void assertRewardItemAllowed(Item item, Campaign campaign) {
        if (!item.isTradeable() && !campaign.isOfficial() && !ItemService.isActiveCrateSentinel(item)) {
            throw new ValidationException(
                    "Only official campaigns can reward untradeable items");
        }
        if (!item.isObtainableAt(Instant.now())) {
            throw new ValidationException(
                    "'" + item.getName() + "' can no longer be handed out as a reward");
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
                publishLegacyBackfill(campaign, resolvedUserId, campaignId);
                campaignScoreGate.refresh();
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
        publishLegacyBackfill(campaign, resolvedUserId, campaignId);
        campaignScoreGate.refresh();
        return toUserCampaignResponse(saved);
    }

    private void publishLegacyBackfill(Campaign campaign, Long userId, UUID campaignId) {
        if (campaign.isLegacy()) {
            eventPublisher.publishEvent(
                    new com.accsaber.backend.model.event.LegacyCampaignBackfillEvent(userId, campaignId));
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

    public Page<UserCampaignResponse> listUserCampaigns(CampaignFilter filter, Long viewerId, boolean privileged,
            Pageable pageable) {
        Long participantId = duplicateUserService.resolvePrimaryUserId(filter.participantId());
        Page<CampaignResponse> campaigns = findCampaigns(filter, viewerId, privileged, pageable);
        List<UUID> campaignIds = campaigns.getContent().stream().map(CampaignResponse::getId).toList();
        Map<UUID, UserCampaign> byCampaign = campaignIds.isEmpty()
                ? Map.of()
                : userCampaignRepository.findByUser_IdAndCampaign_IdInAndActiveTrue(participantId, campaignIds)
                        .stream()
                        .collect(Collectors.toMap(uc -> uc.getCampaign().getId(), uc -> uc));
        Map<UUID, Integer> completedByCampaign = countMap(campaignIds.isEmpty()
                ? List.<Object[]>of()
                : userCampaignScoreRepository.countActiveByUserAndCampaignIds(participantId, campaignIds));
        return campaigns.map(campaign -> toUserCampaignResponse(byCampaign.get(campaign.getId()), campaign,
                completedByCampaign.getOrDefault(campaign.getId(), 0)));
    }

    public CampaignProgressResponse getUserProgress(Long userId, UUID campaignId) {
        return userProgress(userId, loadActiveCampaign(campaignId));
    }

    public CampaignProgressResponse getUserProgressBySlug(Long userId, String slug) {
        return userProgress(userId, campaignRepository.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", slug)));
    }

    private CampaignProgressResponse userProgress(Long userId, Campaign campaign) {
        Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(userId);
        if (isDraftHiddenFrom(campaign, resolvedUserId, false)) {
            throw new ResourceNotFoundException("Campaign", campaign.getId());
        }
        ProgressContext ctx = loadProgressContext(resolvedUserId, List.of(campaign.getId()));
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
        Map<UUID, List<CampaignConnectionResponse>> prereqsByDifficulty = paths.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getCampaignDifficulty().getId(),
                        Collectors.mapping(CampaignService::toConnection, Collectors.toList())));

        Map<UUID, UserCampaign> userCampaignByCampaign = userCampaignRepository
                .findByUser_IdAndCampaign_IdInAndActiveTrue(resolvedUserId, campaignIds).stream()
                .collect(Collectors.toMap(uc -> uc.getCampaign().getId(), uc -> uc));

        List<UserCampaignScore> campaignScores = userCampaignScoreRepository
                .findWithScoreByUser_IdAndCampaign_IdInAndActiveTrue(resolvedUserId, campaignIds);
        Map<UUID, Set<UUID>> completedByCampaign = campaignScores.stream()
                .collect(Collectors.groupingBy(
                        ucs -> ucs.getCampaign().getId(),
                        Collectors.mapping(ucs -> ucs.getCampaignDifficulty().getId(), Collectors.toSet())));
        Map<UUID, Set<UUID>> rewardsPaidByCampaign = campaignScores.stream()
                .filter(UserCampaignScore::isRewardsPaid)
                .collect(Collectors.groupingBy(
                        ucs -> ucs.getCampaign().getId(),
                        Collectors.mapping(ucs -> ucs.getCampaignDifficulty().getId(), Collectors.toSet())));
        Map<UUID, Map<UUID, Score>> campaignScoreByDifficulty = new HashMap<>();
        Map<UUID, Map<UUID, Instant>> completionTimesByCampaign = new HashMap<>();
        for (UserCampaignScore ucs : campaignScores) {
            Instant completionTime = ucs.getScore() != null
                    ? CampaignScoreMetrics.effectiveTime(ucs.getScore())
                    : ucs.getSubmittedAt();
            completionTimesByCampaign
                    .computeIfAbsent(ucs.getCampaign().getId(), k -> new HashMap<>())
                    .put(ucs.getCampaignDifficulty().getId(), completionTime);
            if (ucs.getScore() == null) {
                continue;
            }
            campaignScoreByDifficulty
                    .computeIfAbsent(ucs.getCampaign().getId(), k -> new HashMap<>())
                    .put(ucs.getCampaignDifficulty().getId(), ucs.getScore());
        }

        Map<UUID, List<CampaignTagResponse>> tagsByCampaign = loadTagsByCampaignIds(campaignIds);
        Map<UUID, List<CampaignItemAwardResponse>> completionItemsByCampaign = loadCompletionItemsBulk(campaignIds);

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
        Map<UUID, List<CampaignDifficultyTarget>> targetsByNode = loadTargetsBulk(nodeIds);
        List<CampaignDifficultyModifier> modifierLinks = loadDifficultyModifierLinks(nodeIds);
        Map<UUID, List<CampaignModifierRequirementResponse>> modifiersByNode = groupModifierResponses(modifierLinks);
        Map<UUID, CampaignModifierRule> modifierRulesByNode = CampaignModifierRule.byNode(modifierLinks);
        Map<UUID, Double> complexityByMapDifficulty = loadComplexitiesBulk(difficulties);

        return new ProgressContext(difficultiesByCampaign, barriersByCampaign, prereqsByDifficulty,
                campaignScoreByDifficulty, userCampaignByCampaign, completedByCampaign, rewardsPaidByCampaign,
                completionTimesByCampaign, tagsByCampaign, completionItemsByCampaign, affectedByBarrier, itemsByNode,
                modifiersByNode, modifierRulesByNode, targetsByNode, complexityByMapDifficulty);
    }

    private CampaignProgressResponse buildProgress(Campaign campaign, Long resolvedUserId, ProgressContext ctx) {
        UUID campaignId = campaign.getId();
        List<CampaignDifficulty> difficulties = ctx.difficultiesByCampaign.getOrDefault(campaignId, List.of());
        Set<UUID> completedIds = ctx.completedByCampaign.getOrDefault(campaignId, Set.of());
        Set<UUID> rewardsPaidIds = ctx.rewardsPaidByCampaign.getOrDefault(campaignId, Set.of());
        Set<UUID> pathCompletedIds = campaignEvaluationService.fullyReachedNodeIds(campaignId, resolvedUserId);
        Map<UUID, Score> campaignScores = ctx.campaignScoreByDifficulty.getOrDefault(campaignId, Map.of());
        UserCampaign uc = ctx.userCampaignByCampaign.get(campaignId);
        boolean agnostic = campaign.isProgressionAgnostic();

        Map<UUID, Instant> completionTimes = ctx.completionTimesByCampaign.getOrDefault(campaignId, Map.of());
        Instant since = progressSince(campaign, uc);
        Map<UUID, List<Score>> rowsByMap = loadProgressRows(uc, difficulties, resolvedUserId, since);
        ScoreModifierIndex scoreModifiers = ScoreModifierIndex.load(
                rowsByMap.values().stream().flatMap(List::stream).toList(),
                scoreModifierLinkRepository::findModifierRows);

        Map<UUID, UserMapDifficultyBests> bestsByNode = new HashMap<>();
        List<CampaignDifficultyProgressResponse> progress = new ArrayList<>(difficulties.size());
        for (CampaignDifficulty d : difficulties) {
            List<CampaignConnectionResponse> prereqs = ctx.prereqsByDifficulty.getOrDefault(d.getId(), List.of());
            CampaignPrerequisiteMode mode = d.getPrerequisiteMode() != null
                    ? d.getPrerequisiteMode()
                    : CampaignPrerequisiteMode.OR;
            NodeWindow window = displayWindow(prereqs, mode, completionTimes, since, agnostic);
            boolean unlocked = window != null;
            UserMapDifficultyBests bests = null;
            Score reference = null;
            if (window != null) {
                CampaignModifierRule rule = ctx.modifierRulesByNode.get(d.getId());
                List<Score> rows = rowsByMap.getOrDefault(d.getMapDifficulty().getId(), List.of()).stream()
                        .filter(r -> withinWindow(CampaignScoreMetrics.effectiveTime(r), window)
                                && scoreModifiers.satisfies(r.getId(), rule))
                        .toList();
                bests = CampaignScoreMetrics.reduceBests(d.getMapDifficulty().getId(),
                        d.getMapDifficulty().getMaxScore(), rows, scoreModifiers);
                reference = CampaignScoreMetrics.bestMatchingRow(rows,
                        CampaignScoreMetrics.effectiveTargets(d, ctx.targetsByNode.getOrDefault(d.getId(), List.of())),
                        CampaignScoreMetrics.requiresAllTargets(d), scoreModifiers);
            }
            if (bests != null) {
                bestsByNode.put(d.getId(), bests);
            }
            Double userValue = reference != null && d.getRequirementType() != null
                    ? CampaignScoreMetrics.rowValue(reference, d.getRequirementType(), scoreModifiers)
                    : null;
            List<CampaignTargetProgressResponse> targetProgress = toTargetProgress(
                    ctx.targetsByNode.getOrDefault(d.getId(), List.of()), reference, scoreModifiers);
            Score score = campaignScores.get(d.getId());
            Integer userScore = score != null ? score.getScore() : null;
            progress.add(CampaignDifficultyProgressResponse.builder()
                    .node(toCampaignDifficultyResponse(d, new NodeAssets(prereqs,
                            ctx.itemsByNode.getOrDefault(d.getId(), List.of()),
                            ctx.modifiersByNode.getOrDefault(d.getId(), List.of()),
                            toTargetResponses(ctx.targetsByNode.getOrDefault(d.getId(), List.of())),
                            ctx.complexityByMapDifficulty.get(d.getMapDifficulty().getId()))))
                    .userValue(userValue)
                    .targets(targetProgress)
                    .userScore(userScore)
                    .completed(completedIds.contains(d.getId()))
                    .unlocked(unlocked)
                    .pathCompleted(pathCompletedIds.contains(d.getId()))
                    .rewardsEarned(rewardsPaidIds.contains(d.getId()))
                    .build());
        }

        List<BarrierProgressResponse> barrierProgress = buildBarrierProgress(campaignId, ctx, completedIds, agnostic,
                bestsByNode);
        Set<UUID> mapNodeIds = difficulties.stream().map(CampaignDifficulty::getId).collect(Collectors.toSet());
        int completedMapNodes = (int) completedIds.stream().filter(mapNodeIds::contains).count();

        CampaignResponse campaignResponse = toCampaignResponse(campaign,
                ctx.tagsByCampaign.getOrDefault(campaignId, List.of()), difficulties.size(),
                ctx.completionItemsByCampaign.getOrDefault(campaignId, List.of()));

        return CampaignProgressResponse.builder()
                .id(uc != null ? uc.getId() : null)
                .campaign(campaignResponse)
                .progressStatus(uc != null ? uc.getStatus() : null)
                .startedAt(uc != null ? uc.getStartedAt() : null)
                .completedAt(uc != null ? uc.getCompletedAt() : null)
                .completedDifficulties(completedMapNodes)
                .currentMilestone(furthestReachedMilestone(difficulties, ctx.prereqsByDifficulty, pathCompletedIds))
                .difficulties(progress)
                .barriers(barrierProgress)
                .build();
    }

    private static CurrentMilestoneResponse furthestReachedMilestone(List<CampaignDifficulty> difficulties,
            Map<UUID, List<CampaignConnectionResponse>> prereqsByDifficulty, Set<UUID> pathCompletedIds) {
        Map<UUID, List<UUID>> prereqs = new HashMap<>();
        prereqsByDifficulty.forEach((nodeId, connections) -> prereqs.put(nodeId, connections.stream()
                .map(CampaignConnectionResponse::getComesFromCampaignDifficultyId).toList()));
        Map<UUID, Integer> depths = new HashMap<>();
        CampaignDifficulty furthest = null;
        int bestDepth = -1;
        for (CampaignDifficulty d : difficulties) {
            String label = d.getCheckpointLabel();
            if (label == null || label.isBlank() || !pathCompletedIds.contains(d.getId())) {
                continue;
            }
            int depth = nodeDepth(d.getId(), prereqs, depths);
            if (depth > bestDepth) {
                bestDepth = depth;
                furthest = d;
            }
        }
        return furthest == null ? null
                : CurrentMilestoneResponse.builder()
                        .nodeId(furthest.getId())
                        .label(furthest.getCheckpointLabel())
                        .depth(bestDepth)
                        .build();
    }

    private static int nodeDepth(UUID id, Map<UUID, List<UUID>> prereqs, Map<UUID, Integer> memo) {
        Integer cached = memo.get(id);
        if (cached != null) {
            return cached;
        }
        memo.put(id, 0);
        int max = 0;
        for (UUID p : prereqs.getOrDefault(id, List.of())) {
            max = Math.max(max, 1 + nodeDepth(p, prereqs, memo));
        }
        memo.put(id, max);
        return max;
    }

    private List<BarrierProgressResponse> buildBarrierProgress(UUID campaignId, ProgressContext ctx,
            Set<UUID> completedIds, boolean agnostic,
            Map<UUID, UserMapDifficultyBests> bestsByNode) {
        List<CampaignDifficulty> barriers = ctx.barriersByCampaign.getOrDefault(campaignId, List.of());
        if (barriers.isEmpty()) {
            return List.of();
        }
        List<BarrierProgressResponse> result = new ArrayList<>(barriers.size());
        for (CampaignDifficulty b : barriers) {
            List<CampaignConnectionResponse> prereqs = ctx.prereqsByDifficulty.getOrDefault(b.getId(), List.of());
            CampaignPrerequisiteMode mode = b.getPrerequisiteMode() != null
                    ? b.getPrerequisiteMode()
                    : CampaignPrerequisiteMode.OR;
            boolean unlocked = agnostic || prereqsSatisfied(prereqs, mode, completedIds);
            List<UUID> affected = ctx.affectedByBarrier.getOrDefault(b.getId(), List.of());
            result.add(BarrierProgressResponse.builder()
                    .barrier(toBarrierResponse(b, prereqs, affected,
                            ctx.itemsByNode.getOrDefault(b.getId(), List.of())))
                    .currentValue(computeBarrierCurrentValue(b, affected, completedIds, bestsByNode))
                    .satisfied(completedIds.contains(b.getId()))
                    .unlocked(unlocked)
                    .build());
        }
        return result;
    }

    private static Instant progressSince(Campaign campaign, UserCampaign uc) {
        if (campaign.isLegacy()) {
            return Instant.EPOCH;
        }
        if (uc == null) {
            return Instant.EPOCH;
        }
        if (uc.getStartedAt() != null) {
            return uc.getStartedAt();
        }
        return uc.getCreatedAt() != null ? uc.getCreatedAt() : Instant.EPOCH;
    }

    private Map<UUID, List<Score>> loadProgressRows(UserCampaign uc, List<CampaignDifficulty> difficulties,
            Long resolvedUserId, Instant since) {
        if (uc == null || difficulties.isEmpty()) {
            return Map.of();
        }
        List<UUID> mapDifficultyIds = difficulties.stream()
                .map(d -> d.getMapDifficulty().getId())
                .distinct()
                .toList();
        return scoreRepository.findEligibleCampaignRows(resolvedUserId, mapDifficultyIds, since).stream()
                .collect(Collectors.groupingBy(s -> s.getMapDifficulty().getId()));
    }

    private static boolean prereqsSatisfied(List<CampaignConnectionResponse> prereqs, CampaignPrerequisiteMode mode,
            Set<UUID> completedIds) {
        if (prereqs.isEmpty()) {
            return true;
        }
        if (mode == CampaignPrerequisiteMode.AND) {
            for (CampaignConnectionResponse p : prereqs) {
                if (!completedIds.contains(p.getComesFromCampaignDifficultyId())) {
                    return false;
                }
            }
            return true;
        }
        for (CampaignConnectionResponse p : prereqs) {
            if (completedIds.contains(p.getComesFromCampaignDifficultyId())) {
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
            Map<UUID, List<CampaignConnectionResponse>> prereqsByDifficulty,
            Map<UUID, Map<UUID, Score>> campaignScoreByDifficulty,
            Map<UUID, UserCampaign> userCampaignByCampaign,
            Map<UUID, Set<UUID>> completedByCampaign,
            Map<UUID, Set<UUID>> rewardsPaidByCampaign,
            Map<UUID, Map<UUID, Instant>> completionTimesByCampaign,
            Map<UUID, List<CampaignTagResponse>> tagsByCampaign,
            Map<UUID, List<CampaignItemAwardResponse>> completionItemsByCampaign,
            Map<UUID, List<UUID>> affectedByBarrier,
            Map<UUID, List<CampaignItemAwardResponse>> itemsByNode,
            Map<UUID, List<CampaignModifierRequirementResponse>> modifiersByNode,
            Map<UUID, CampaignModifierRule> modifierRulesByNode,
            Map<UUID, List<CampaignDifficultyTarget>> targetsByNode,
            Map<UUID, Double> complexityByMapDifficulty) {
    }

    private record NodeWindow(Instant at, boolean exclusive) {
    }

    private static NodeWindow displayWindow(List<CampaignConnectionResponse> prereqs,
            CampaignPrerequisiteMode mode, Map<UUID, Instant> completionTimes, Instant since, boolean agnostic) {
        if (agnostic || prereqs.isEmpty()) {
            return new NodeWindow(since, false);
        }
        if (mode == CampaignPrerequisiteMode.AND) {
            Instant latest = null;
            for (CampaignConnectionResponse p : prereqs) {
                Instant t = completionTimes.get(p.getComesFromCampaignDifficultyId());
                if (t == null) {
                    return null;
                }
                if (latest == null || t.isAfter(latest)) {
                    latest = t;
                }
            }
            return new NodeWindow(latest, true);
        }
        Instant earliest = null;
        for (CampaignConnectionResponse p : prereqs) {
            Instant t = completionTimes.get(p.getComesFromCampaignDifficultyId());
            if (t != null && (earliest == null || t.isBefore(earliest))) {
                earliest = t;
            }
        }
        return earliest != null ? new NodeWindow(earliest, true) : null;
    }

    private static boolean withinWindow(Instant scoreTime, NodeWindow window) {
        if (window == null || scoreTime == null) {
            return false;
        }
        return window.exclusive() ? scoreTime.isAfter(window.at()) : !scoreTime.isBefore(window.at());
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
    public CampaignBarrierResponse addBarrierAsEditor(CampaignEditor editor, UUID campaignId,
            AddCampaignBarrierRequest request) {
        editableDraftCampaign(editor, campaignId);
        assertUnderCap(editor,
                () -> campaignDifficultyRepository.countByCampaign_IdAndBarrierTrueAndActiveTrue(campaignId),
                MAX_BARRIERS_PER_CAMPAIGN, "barriers");
        return addBarrier(campaignId, request);
    }

    @Transactional
    public CampaignBarrierResponse addBarrier(UUID campaignId, AddCampaignBarrierRequest request) {
        Campaign campaign = loadActiveCampaign(campaignId);
        ensureEditable(campaign);
        if (campaign.isProgressionAgnostic()) {
            throw new ValidationException("Barriers cannot be added to a progression-agnostic campaign");
        }
        boolean valueless = isValuelessCondition(request.getConditionType());
        Double conditionValue = valueless ? null : request.getConditionValue();
        Double conditionValueMax = valueless ? null : request.getConditionValueMax();
        validateBarrierCondition(request.getConditionType(), conditionValue, conditionValueMax);
        validateCompletionTarget(request.getConditionType(), conditionValue,
                (int) safePrereqIds(request.getAffectedCampaignDifficultyIds()).stream().distinct().count());
        if (campaignDifficultyRepository.existsByCampaign_IdAndPositionXAndPositionYAndActiveTrue(
                campaignId, request.getPositionX(), request.getPositionY())) {
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
        return toBarrierResponse(barrier,
                echoConnections(request.getPrerequisites()),
                safePrereqIds(request.getAffectedCampaignDifficultyIds()),
                List.of());
    }

    @Transactional
    public CampaignBarrierResponse updateBarrierAsEditor(CampaignEditor editor, UUID barrierId,
            UpdateCampaignBarrierRequest request) {
        CampaignDifficulty barrier = loadActiveBarrier(barrierId);
        assertCanEditDraft(barrier.getCampaign(), editor);
        return applyBarrierUpdate(barrier, request);
    }

    @Transactional
    public CampaignBarrierResponse updateBarrier(UUID barrierId, UpdateCampaignBarrierRequest request) {
        return applyBarrierUpdate(loadActiveBarrier(barrierId), request);
    }

    @Transactional
    public void removeBarrierAsEditor(CampaignEditor editor, UUID campaignId, UUID barrierId) {
        editableDraftCampaign(editor, campaignId);
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
        if (request.getPositionX() != null || request.getPositionY() != null) {
            Double newX = request.getPositionX() != null ? request.getPositionX() : barrier.getPositionX();
            Double newY = request.getPositionY() != null ? request.getPositionY() : barrier.getPositionY();
            if ((boundChanged(barrier.getPositionX(), newX) || boundChanged(barrier.getPositionY(), newY))
                    && campaignDifficultyRepository.existsByCampaign_IdAndPositionXAndPositionYAndActiveTrue(
                            barrier.getCampaign().getId(), newX, newY)) {
                throw new ValidationException("A node already occupies that grid position");
            }
            barrier.setPositionX(newX);
            barrier.setPositionY(newY);
        }
        if (request.getXp() != null) {
            if (Math.signum(request.getXp()) < 0) {
                throw new ValidationException("xp", "must be non-negative");
            }
            barrier.setXp(request.getXp());
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
        List<CampaignConnectionResponse> prereqConnections = toConnections(campaignDifficultyPathRepository
                .findByCampaignDifficulty_IdAndActiveTrue(barrier.getId()));
        return toBarrierResponse(barrier, prereqConnections, affectedIds, loadDifficultyItems(barrier.getId()));
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

    private Map<UUID, List<CampaignDifficultyTarget>> loadTargetsBulk(Collection<UUID> difficultyIds) {
        if (difficultyIds.isEmpty()) {
            return Map.of();
        }
        return campaignDifficultyTargetRepository.findByCampaignDifficultyIds(difficultyIds).stream()
                .collect(Collectors.groupingBy(target -> target.getCampaignDifficulty().getId()));
    }

    private List<CampaignTargetResponse> loadTargets(UUID campaignDifficultyId) {
        return toTargetResponses(
                campaignDifficultyTargetRepository.findByCampaignDifficulty_IdOrderByOrdinalAsc(campaignDifficultyId));
    }

    private static List<CampaignTargetProgressResponse> toTargetProgress(List<CampaignDifficultyTarget> targets,
            Score reference, ScoreModifierIndex modifiers) {
        return targets.stream().map(target -> {
            Double userValue = reference != null
                    ? CampaignScoreMetrics.rowValue(reference, target.getRequirementType(), modifiers)
                    : null;
            return CampaignTargetProgressResponse.builder()
                    .target(toTargetResponse(target))
                    .userValue(userValue)
                    .met(CampaignScoreMetrics.satisfies(target, userValue))
                    .build();
        }).toList();
    }

    private static List<CampaignTargetResponse> toTargetResponses(List<CampaignDifficultyTarget> targets) {
        return targets.stream().map(CampaignService::toTargetResponse).toList();
    }

    private static CampaignTargetResponse toTargetResponse(CampaignDifficultyTarget target) {
        return CampaignTargetResponse.builder()
                .id(target.getId())
                .requirementType(target.getRequirementType())
                .requirementValue(target.getRequirementValue())
                .requirementValueMax(target.getRequirementValueMax())
                .build();
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

    private static final Set<CampaignRequirementType> RANKED_ONLY_REQUIREMENTS = Set.of(
            CampaignRequirementType.AP, CampaignRequirementType.RANK);

    private static final Set<BarrierConditionType> RANKED_ONLY_BARRIER_CONDITIONS = Set.of(
            BarrierConditionType.AVERAGE_AP, BarrierConditionType.AP_MAX,
            BarrierConditionType.AVERAGE_RANK, BarrierConditionType.MAX_RANK);

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
    public CampaignTextResponse addTextAsEditor(CampaignEditor editor, UUID campaignId, CampaignTextRequest request) {
        editableDraftCampaign(editor, campaignId);
        assertUnderCap(editor, () -> campaignTextRepository.countByCampaign_IdAndActiveTrue(campaignId),
                MAX_TEXTS_PER_CAMPAIGN, "text elements");
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
    public CampaignTextResponse updateTextAsEditor(CampaignEditor editor, UUID textId, CampaignTextRequest request) {
        CampaignText text = loadActiveText(textId);
        assertCanEditDraft(text.getCampaign(), editor);
        return applyTextUpdate(text, request);
    }

    @Transactional
    public CampaignTextResponse updateText(UUID textId, CampaignTextRequest request) {
        return applyTextUpdate(loadActiveText(textId), request);
    }

    @Transactional
    public void removeTextAsEditor(CampaignEditor editor, UUID campaignId, UUID textId) {
        editableDraftCampaign(editor, campaignId);
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

    @Transactional
    public void moveElementsAsEditor(CampaignEditor editor, UUID campaignId,
            MoveCampaignElementsRequest request) {
        ensureEditable(editableDraftCampaign(editor, campaignId));

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
        Map<UUID, List<CampaignConnectionResponse>> prereqsByDifficultyId = campaignDifficultyPathRepository
                .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaignId).stream()
                .collect(Collectors.groupingBy(
                        p -> p.getCampaignDifficulty().getId(),
                        Collectors.mapping(CampaignService::toConnection, Collectors.toList())));
        List<UUID> difficultyIds = difficulties.stream().map(CampaignDifficulty::getId).toList();
        Map<UUID, List<CampaignItemAwardResponse>> itemsByDifficultyId = loadDifficultyItemsBulk(difficultyIds);
        Map<UUID, PublicStaffUserResponse> staffRefs = loadStaffRefs(List.of(campaign));
        Map<UUID, List<CampaignDifficultyTarget>> targetsByDifficultyId = loadTargetsBulk(difficultyIds);
        Map<UUID, List<CampaignModifierRequirementResponse>> modifiersByDifficultyId = groupModifierResponses(
                loadDifficultyModifierLinks(difficultyIds));
        Map<UUID, Double> complexityByMapDifficulty = loadComplexitiesBulk(difficulties);

        List<CampaignDifficultyResponse> difficultyResponses = new ArrayList<>(difficulties.size());
        for (CampaignDifficulty d : difficulties) {
            difficultyResponses.add(toCampaignDifficultyResponse(d, new NodeAssets(
                    prereqsByDifficultyId.getOrDefault(d.getId(), List.of()),
                    itemsByDifficultyId.getOrDefault(d.getId(), List.of()),
                    modifiersByDifficultyId.getOrDefault(d.getId(), List.of()),
                    toTargetResponses(targetsByDifficultyId.getOrDefault(d.getId(), List.of())),
                    complexityByMapDifficulty.get(d.getMapDifficulty().getId()))));
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
                .creatorId(campaign.getCreator() != null ? String.valueOf(campaign.getCreator().getId()) : null)
                .creatorName(campaign.getCreator() != null ? campaign.getCreator().getName() : null)
                .creatorAlias(campaign.getCreatorAlias())
                .name(campaign.getName())
                .slug(campaign.getSlug())
                .summary(campaign.getSummary())
                .description(campaign.getDescription())
                .status(campaign.getStatus())
                .official(campaign.isOfficial())
                .progressionAgnostic(campaign.isProgressionAgnostic())
                .completionMode(campaign.getCompletionMode())
                .legacy(campaign.isLegacy())
                .completionXp(campaign.getCompletionXp())
                .curatorNotes(campaign.getCuratorNotes())
                .playlistExportEnabled(campaign.isPlaylistExportEnabled())
                .backgroundUrl(campaign.getBackgroundUrl())
                .backgroundColor(campaign.getBackgroundColor())
                .background(staticBackground(campaign))
                .iconUrl(campaign.getIconUrl())
                .totalUpvotes(campaign.getTotalUpvotes())
                .totalDownvotes(campaign.getTotalDownvotes())
                .voteScore(campaign.getVoteScore())
                .myVote(viewerVoteFor(campaignId, viewerId))
                .curatedAt(campaign.getCuratedAt())
                .curatedBy(staffRef(campaign.getCuratedBy(), staffRefs))
                .loved(campaign.isLoved())
                .lovedAt(campaign.getLovedAt())
                .lovedBy(staffRef(campaign.getLovedBy(), staffRefs))
                .publishedAt(campaign.getPublishedAt())
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

    private Map<UUID, List<CampaignItemAwardResponse>> loadCompletionItemsBulk(Collection<UUID> campaignIds) {
        if (campaignIds.isEmpty()) {
            return Map.of();
        }
        return campaignCompletionItemRepository.findByCampaign_IdIn(campaignIds).stream()
                .collect(Collectors.groupingBy(
                        link -> link.getCampaign().getId(),
                        Collectors.mapping(CampaignService::toItemAward, Collectors.toList())));
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

    private List<CampaignModifierRequirementResponse> loadDifficultyModifiers(UUID difficultyId) {
        return campaignDifficultyModifierRepository.findByCampaignDifficulty_Id(difficultyId).stream()
                .map(CampaignService::toModifierRequirement)
                .toList();
    }

    private List<CampaignDifficultyModifier> loadDifficultyModifierLinks(Collection<UUID> difficultyIds) {
        if (difficultyIds.isEmpty()) {
            return List.of();
        }
        return campaignDifficultyModifierRepository.findByCampaignDifficulty_IdIn(difficultyIds);
    }

    private static Map<UUID, List<CampaignModifierRequirementResponse>> groupModifierResponses(
            Collection<CampaignDifficultyModifier> links) {
        return links.stream().collect(Collectors.groupingBy(
                link -> link.getId().getCampaignDifficultyId(),
                Collectors.mapping(CampaignService::toModifierRequirement, Collectors.toList())));
    }

    private static CampaignModifierRequirementResponse toModifierRequirement(CampaignDifficultyModifier link) {
        return CampaignModifierRequirementResponse.builder()
                .modifier(ModifierService.toResponse(link.getModifier()))
                .requirement(link.getRequirement())
                .build();
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
            if (target.put(entry.getModifierId(), entry.getRequirement()) != null) {
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

    private Map<UUID, Double> loadComplexitiesBulk(Collection<CampaignDifficulty> difficulties) {
        List<UUID> mapDifficultyIds = difficulties.stream()
                .filter(d -> !d.isBarrier())
                .map(d -> d.getMapDifficulty().getId())
                .distinct()
                .toList();
        if (mapDifficultyIds.isEmpty()) {
            return Map.of();
        }
        return mapDifficultyComplexityRepository.findActiveByMapDifficultyIdIn(mapDifficultyIds).stream()
                .collect(Collectors.toMap(
                        c -> c.getMapDifficulty().getId(),
                        MapDifficultyComplexity::getComplexity,
                        (a, b) -> a));
    }

    private Double loadComplexity(UUID mapDifficultyId) {
        return mapDifficultyComplexityRepository.findByMapDifficultyIdAndActiveTrue(mapDifficultyId)
                .map(MapDifficultyComplexity::getComplexity)
                .orElse(null);
    }

    private CampaignResponse toCampaignResponse(Campaign campaign) {
        return toCampaignResponse(campaign,
                loadTagResponses(campaign.getId()),
                (int) campaignDifficultyRepository.countByCampaign_IdAndBarrierFalseAndActiveTrue(campaign.getId()),
                loadCompletionItems(campaign.getId()));
    }

    private CampaignResponse toCampaignResponse(Campaign campaign, List<CampaignTagResponse> tags,
            int difficultyCount, List<CampaignItemAwardResponse> completionItems) {
        return toCampaignResponse(campaign, new CampaignRow(tags, difficultyCount, null, completionItems,
                CampaignRewards.none(), loadStaffRefs(List.of(campaign))));
    }

    private record CampaignRow(
            List<CampaignTagResponse> tags,
            int difficultyCount,
            CampaignVoteDirection myVote,
            List<CampaignItemAwardResponse> completionItems,
            CampaignRewards rewards,
            Map<UUID, PublicStaffUserResponse> staffRefs) {

        static CampaignRow empty() {
            return new CampaignRow(List.of(), 0, null, List.of(), CampaignRewards.none(), Map.of());
        }

        PublicStaffUserResponse staff(StaffUser staff) {
            return staff != null ? staffRefs.get(staff.getId()) : null;
        }
    }

    private Map<UUID, PublicStaffUserResponse> loadStaffRefs(Collection<Campaign> campaigns) {
        List<UUID> staffIds = campaigns.stream()
                .flatMap(c -> Stream.of(c.getCuratedBy(), c.getLovedBy()))
                .filter(Objects::nonNull)
                .map(StaffUser::getId)
                .distinct()
                .toList();
        if (staffIds.isEmpty()) {
            return Map.of();
        }
        return staffUserRepository.findAllByIdWithUser(staffIds).stream()
                .collect(Collectors.toMap(StaffUser::getId, StaffMapper::toPublicResponse));
    }

    private PublicStaffUserResponse staffRef(StaffUser staff, Map<UUID, PublicStaffUserResponse> refs) {
        return staff != null ? refs.get(staff.getId()) : null;
    }

    private record CampaignRewards(CampaignRewardTotals totals, List<CampaignItemAwardResponse> items) {

        private static final CampaignRewards NONE = new CampaignRewards(null, List.of());

        static CampaignRewards none() {
            return NONE;
        }

        Double totalXp() {
            return totals != null ? totals.getTotalXp() : null;
        }

        Integer totalRewardCount() {
            return totals != null ? totals.getTotalRewards() : null;
        }
    }

    private CampaignResponse toCampaignResponse(Campaign campaign, CampaignRow row) {
        return CampaignResponse.builder()
                .id(campaign.getId())
                .creatorId(campaign.getCreator() != null ? String.valueOf(campaign.getCreator().getId()) : null)
                .creatorName(campaign.getCreator() != null ? campaign.getCreator().getName() : null)
                .creatorAlias(campaign.getCreatorAlias())
                .name(campaign.getName())
                .slug(campaign.getSlug())
                .summary(campaign.getSummary())
                .description(campaign.getDescription())
                .status(campaign.getStatus())
                .official(campaign.isOfficial())
                .progressionAgnostic(campaign.isProgressionAgnostic())
                .completionMode(campaign.getCompletionMode())
                .legacy(campaign.isLegacy())
                .completionXp(campaign.getCompletionXp())
                .playlistExportEnabled(campaign.isPlaylistExportEnabled())
                .backgroundUrl(campaign.getBackgroundUrl())
                .backgroundColor(campaign.getBackgroundColor())
                .background(staticBackground(campaign))
                .iconUrl(campaign.getIconUrl())
                .difficultyCount(row.difficultyCount())
                .totalUpvotes(campaign.getTotalUpvotes())
                .totalDownvotes(campaign.getTotalDownvotes())
                .voteScore(campaign.getVoteScore())
                .myVote(row.myVote())
                .tags(row.tags())
                .completionItems(row.completionItems())
                .totalXp(row.rewards().totalXp())
                .totalRewardCount(row.rewards().totalRewardCount())
                .rewards(row.rewards().items())
                .curatedAt(campaign.getCuratedAt())
                .curatedBy(row.staff(campaign.getCuratedBy()))
                .loved(campaign.isLoved())
                .lovedAt(campaign.getLovedAt())
                .lovedBy(row.staff(campaign.getLovedBy()))
                .publishedAt(campaign.getPublishedAt())
                .createdAt(campaign.getCreatedAt())
                .build();
    }

    private static CampaignConnectionResponse toConnection(CampaignDifficultyPath path) {
        return CampaignConnectionResponse.builder()
                .comesFromCampaignDifficultyId(path.getComesFromCampaignDifficulty().getId())
                .color(path.getColor())
                .build();
    }

    private static List<CampaignConnectionResponse> toConnections(List<CampaignDifficultyPath> paths) {
        return paths.stream().map(CampaignService::toConnection).toList();
    }

    private static List<CampaignConnectionResponse> echoConnections(List<CampaignConnectionRequest> prerequisites) {
        if (prerequisites == null) {
            return List.of();
        }
        return prerequisites.stream()
                .map(c -> CampaignConnectionResponse.builder()
                        .comesFromCampaignDifficultyId(c.getComesFromCampaignDifficultyId())
                        .color(c.getColor())
                        .build())
                .toList();
    }

    private record NodeAssets(
            List<CampaignConnectionResponse> prerequisites,
            List<CampaignItemAwardResponse> items,
            List<CampaignModifierRequirementResponse> modifiers,
            List<CampaignTargetResponse> targets,
            Double complexity) {
    }

    private CampaignDifficultyResponse toCampaignDifficultyResponse(CampaignDifficulty difficulty,
            NodeAssets assets) {
        MapDifficulty md = difficulty.getMapDifficulty();
        return CampaignDifficultyResponse.builder()
                .id(difficulty.getId())
                .mapDifficultyId(md.getId())
                .mapId(md.getMap().getId())
                .categoryId(md.getCategory() != null ? md.getCategory().getId() : null)
                .complexity(assets.complexity())
                .beatsaverCode(md.getMap().getBeatsaverCode())
                .maxScore(md.getMaxScore())
                .metadata(md.getMetadata())
                .nps(MapDifficultyMetrics.nps(md.getMetadata()))
                .maxCombo(MapDifficultyMetrics.maxCombo(md.getMetadata()))
                .songName(md.getMap().getSongName())
                .songSubName(md.getMap().getSongSubName())
                .songAuthor(md.getMap().getSongAuthor())
                .mapAuthor(md.getMap().getMapAuthor())
                .coverUrl(md.getMap().getCoverUrl())
                .cdnCoverUrl(md.getMap().getCdnCoverUrl())
                .difficulty(md.getDifficulty())
                .characteristic(md.getCharacteristic())
                .mapDifficultyStatus(md.getStatus())
                .requirementType(difficulty.getRequirementType())
                .requirementValue(difficulty.getRequirementValue())
                .requirementValueMax(difficulty.getRequirementValueMax())
                .targetMode(difficulty.getTargetMode())
                .targets(assets.targets())
                .prerequisiteMode(difficulty.getPrerequisiteMode())
                .terminal(difficulty.isTerminal())
                .description(difficulty.getDescription())
                .checkpointLabel(difficulty.getCheckpointLabel())
                .checkpointLabelPosition(difficulty.getCheckpointLabelPosition())
                .checkpointColor(difficulty.getCheckpointColor())
                .borderColor(difficulty.getBorderColor())
                .borderShape(difficulty.getBorderShape())
                .nodeBorderUrl(difficulty.getNodeBorderUrl())
                .nodeBorderLayer(difficulty.getNodeBorderLayer())
                .size(difficulty.getSize())
                .checkpointSize(difficulty.getCheckpointSize())
                .checkpointAvatarUrl(difficulty.getCheckpointAvatarUrl())
                .positionX(difficulty.getPositionX())
                .positionY(difficulty.getPositionY())
                .xp(difficulty.getXp())
                .prerequisites(assets.prerequisites())
                .items(assets.items())
                .modifiers(assets.modifiers())
                .build();
    }

    private static CampaignBarrierResponse toBarrierResponse(CampaignDifficulty barrier,
            List<CampaignConnectionResponse> prerequisites,
            List<UUID> affectedIds, List<CampaignItemAwardResponse> items) {
        return CampaignBarrierResponse.builder()
                .id(barrier.getId())
                .conditionType(barrier.getBarrierConditionType())
                .conditionValue(barrier.getBarrierConditionValue())
                .conditionValueMax(barrier.getBarrierConditionValueMax())
                .prerequisiteMode(barrier.getPrerequisiteMode())
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
                .prerequisites(prerequisites)
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

    private Double computeBarrierCurrentValue(CampaignDifficulty barrier, List<UUID> affected,
            Set<UUID> completedIds, Map<UUID, UserMapDifficultyBests> bestsByNode) {
        BarrierConditionType type = barrier.getBarrierConditionType();
        if (type == null || affected.isEmpty()) {
            return null;
        }
        if (type == BarrierConditionType.COMPLETION_COUNT) {
            return (double) (affected.stream().filter(completedIds::contains).count());
        }
        if (type == BarrierConditionType.FC) {
            return (double) (affected.stream()
                    .map(bestsByNode::get)
                    .filter(bests -> bests != null && bests.hasFullCombo())
                    .count());
        }
        if (type == BarrierConditionType.PASS) {
            return (double) (affected.stream()
                    .map(bestsByNode::get)
                    .filter(bests -> bests != null && bests.hasNoNfPass())
                    .count());
        }
        List<Double> values = new ArrayList<>(affected.size());
        for (UUID nodeId : affected) {
            UserMapDifficultyBests bests = bestsByNode.get(nodeId);
            if (bests == null) {
                return null;
            }
            Double v = CampaignScoreMetrics.barrierMetric(bests, type);
            if (v == null) {
                return null;
            }
            values.add(v);
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
                .countActiveByUserAndCampaign(uc.getUser().getId(), uc.getCampaign().getId());
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
