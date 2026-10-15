package com.accsaber.backend.service.campaign;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.accsaber.backend.model.dto.request.campaign.CampaignConnectionRequest;
import com.accsaber.backend.model.dto.response.campaign.CampaignBarrierResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignConnectionResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignDetailResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignDifficultyResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignItemAwardResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignModifierRequirementResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignTagResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignTargetResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignTextResponse;
import com.accsaber.backend.model.dto.response.campaign.UserCampaignResponse;
import com.accsaber.backend.model.dto.response.staff.PublicStaffUserResponse;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignBackgroundPlacement;
import com.accsaber.backend.model.entity.campaign.CampaignCompletionItem;
import com.accsaber.backend.model.entity.campaign.CampaignDifficulty;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyItem;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyModifier;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyPath;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyTarget;
import com.accsaber.backend.model.entity.campaign.CampaignRewardTotals;
import com.accsaber.backend.model.entity.campaign.CampaignTag;
import com.accsaber.backend.model.entity.campaign.CampaignText;
import com.accsaber.backend.model.entity.campaign.CampaignVote;
import com.accsaber.backend.model.entity.campaign.CampaignVoteDirection;
import com.accsaber.backend.model.entity.campaign.UserCampaign;
import com.accsaber.backend.model.entity.campaign.UserCampaignStatus;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyComplexity;
import com.accsaber.backend.model.entity.staff.StaffUser;
import com.accsaber.backend.repository.campaign.CampaignBarrierAffectedDifficultyRepository;
import com.accsaber.backend.repository.campaign.CampaignCompletionItemRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyItemRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyModifierRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyPathRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyTargetRepository;
import com.accsaber.backend.repository.campaign.CampaignRewardTotalsRepository;
import com.accsaber.backend.repository.campaign.CampaignTagLinkRepository;
import com.accsaber.backend.repository.campaign.CampaignTextRepository;
import com.accsaber.backend.repository.campaign.CampaignVoteRepository;
import com.accsaber.backend.repository.campaign.UserCampaignRepository;
import com.accsaber.backend.repository.campaign.UserCampaignScoreRepository;
import com.accsaber.backend.repository.map.MapDifficultyComplexityRepository;
import com.accsaber.backend.repository.staff.StaffUserRepository;
import com.accsaber.backend.service.infra.ModifierService;
import com.accsaber.backend.service.item.ItemMapper;
import com.accsaber.backend.service.staff.StaffMapper;
import com.accsaber.backend.util.MapDifficultyMetrics;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class CampaignMapper {

    private final CampaignDifficultyRepository campaignDifficultyRepository;
    private final CampaignDifficultyPathRepository campaignDifficultyPathRepository;
    private final CampaignBarrierAffectedDifficultyRepository barrierAffectedRepository;
    private final CampaignTextRepository campaignTextRepository;
    private final CampaignDifficultyItemRepository campaignDifficultyItemRepository;
    private final CampaignDifficultyModifierRepository campaignDifficultyModifierRepository;
    private final CampaignDifficultyTargetRepository campaignDifficultyTargetRepository;
    private final CampaignRewardTotalsRepository campaignRewardTotalsRepository;
    private final CampaignCompletionItemRepository campaignCompletionItemRepository;
    private final CampaignTagLinkRepository campaignTagLinkRepository;
    private final CampaignVoteRepository campaignVoteRepository;
    private final UserCampaignRepository userCampaignRepository;
    private final UserCampaignScoreRepository userCampaignScoreRepository;
    private final MapDifficultyComplexityRepository mapDifficultyComplexityRepository;
    private final StaffUserRepository staffUserRepository;

    record NodeAssets(
            List<CampaignConnectionResponse> prerequisites,
            List<CampaignItemAwardResponse> items,
            List<CampaignModifierRequirementResponse> modifiers,
            List<CampaignTargetResponse> targets,
            Double complexity) {
    }

    private record CampaignRow(
            List<CampaignTagResponse> tags,
            int difficultyCount,
            CampaignVoteDirection myVote,
            List<CampaignItemAwardResponse> completionItems,
            CampaignRewards rewards,
            Map<UUID, PublicStaffUserResponse> staffRefs,
            UserCampaignStatus myProgressStatus,
            Integer myCompletedDifficulties) {

        static CampaignRow empty() {
            return new CampaignRow(List.of(), 0, null, List.of(), CampaignRewards.none(), Map.of(), null, null);
        }

        PublicStaffUserResponse staff(StaffUser staff) {
            return staff != null ? staffRefs.get(staff.getId()) : null;
        }
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

    Page<CampaignResponse> toResponsePage(Page<Campaign> page, Long resolvedViewerId) {
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
        Map<UUID, UserCampaignStatus> viewerStatus = resolvedViewerId == null ? Map.of()
                : userCampaignRepository.findByUser_IdAndCampaign_IdInAndActiveTrue(resolvedViewerId, ids).stream()
                        .collect(Collectors.toMap(uc -> uc.getCampaign().getId(), UserCampaign::getStatus));
        Map<UUID, Integer> viewerCompleted = viewerStatus.isEmpty() ? Map.of()
                : countMap(userCampaignScoreRepository.countActiveByUserAndCampaignIds(resolvedViewerId, ids));
        return page.map(c -> toCampaignResponse(c, new CampaignRow(
                tagsByCampaign.getOrDefault(c.getId(), List.of()),
                diffCountByCampaign.getOrDefault(c.getId(), 0),
                votesByCampaign.get(c.getId()),
                completionItemsByCampaign.getOrDefault(c.getId(), List.of()),
                rewardsByCampaign.getOrDefault(c.getId(), CampaignRewards.none()),
                staffRefs,
                viewerStatus.get(c.getId()),
                viewerStatus.containsKey(c.getId()) ? viewerCompleted.getOrDefault(c.getId(), 0) : null)));
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
                        .item(existing.getItem())
                        .build());
    }

    private Map<UUID, CampaignVoteDirection> loadViewerVotes(Long resolvedViewerId, Collection<UUID> campaignIds) {
        if (resolvedViewerId == null || campaignIds.isEmpty()) {
            return Map.of();
        }
        return campaignVoteRepository.findByUser_IdAndCampaign_IdIn(resolvedViewerId, campaignIds).stream()
                .collect(Collectors.toMap(v -> v.getCampaign().getId(), CampaignVote::getVote));
    }

    static Map<UUID, Integer> countMap(List<Object[]> rows) {
        Map<UUID, Integer> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return map;
    }

    CampaignDetailResponse toDetailResponse(Campaign campaign, CampaignVoteDirection myVote) {
        UUID campaignId = campaign.getId();
        List<CampaignDifficulty> difficulties = campaignDifficultyRepository
                .findActiveWithMapByCampaignId(campaignId);
        Map<UUID, List<CampaignConnectionResponse>> prereqsByDifficultyId = campaignDifficultyPathRepository
                .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaignId).stream()
                .collect(Collectors.groupingBy(
                        p -> p.getCampaignDifficulty().getId(),
                        Collectors.mapping(CampaignMapper::toConnection, Collectors.toList())));
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
                .campaign(toCampaignResponse(campaign, new CampaignRow(loadTagResponses(campaignId), 0, myVote,
                        loadCompletionItems(campaignId), new CampaignRewards(null, null), staffRefs, null, null)))
                .curatorNotes(campaign.getCuratorNotes())
                .difficulties(difficultyResponses)
                .barriers(barrierResponses)
                .texts(loadTextResponses(campaignId))
                .build();
    }

    private List<CampaignTextResponse> loadTextResponses(UUID campaignId) {
        return campaignTextRepository.findByCampaign_IdAndActiveTrue(campaignId).stream()
                .map(CampaignMapper::toTextResponse)
                .toList();
    }

    private List<CampaignTagResponse> loadTagResponses(UUID campaignId) {
        return campaignTagLinkRepository.findByCampaign_Id(campaignId).stream()
                .map(link -> toTagResponse(link.getCampaignTag()))
                .toList();
    }

    Map<UUID, List<CampaignTagResponse>> loadTagsByCampaignIds(Collection<UUID> campaignIds) {
        if (campaignIds.isEmpty()) {
            return Map.of();
        }
        return campaignTagLinkRepository.findByCampaign_IdIn(campaignIds).stream()
                .collect(Collectors.groupingBy(
                        link -> link.getCampaign().getId(),
                        Collectors.mapping(link -> toTagResponse(link.getCampaignTag()), Collectors.toList())));
    }

    Map<UUID, List<CampaignItemAwardResponse>> loadCompletionItemsBulk(Collection<UUID> campaignIds) {
        if (campaignIds.isEmpty()) {
            return Map.of();
        }
        return campaignCompletionItemRepository.findByCampaign_IdIn(campaignIds).stream()
                .collect(Collectors.groupingBy(
                        link -> link.getCampaign().getId(),
                        Collectors.mapping(CampaignMapper::toItemAward, Collectors.toList())));
    }

    Map<UUID, List<CampaignItemAwardResponse>> loadDifficultyItemsBulk(Collection<UUID> difficultyIds) {
        if (difficultyIds.isEmpty()) {
            return Map.of();
        }
        return campaignDifficultyItemRepository.findByCampaignDifficulty_IdIn(difficultyIds).stream()
                .collect(Collectors.groupingBy(
                        link -> link.getCampaignDifficulty().getId(),
                        Collectors.mapping(CampaignMapper::toItemAward, Collectors.toList())));
    }

    List<CampaignItemAwardResponse> loadDifficultyItems(UUID difficultyId) {
        return campaignDifficultyItemRepository.findByCampaignDifficulty_Id(difficultyId).stream()
                .map(CampaignMapper::toItemAward)
                .toList();
    }

    List<CampaignItemAwardResponse> loadCompletionItems(UUID campaignId) {
        return campaignCompletionItemRepository.findByCampaign_Id(campaignId).stream()
                .map(CampaignMapper::toItemAward)
                .toList();
    }

    private List<CampaignModifierRequirementResponse> loadDifficultyModifiers(UUID difficultyId) {
        return campaignDifficultyModifierRepository.findByCampaignDifficulty_Id(difficultyId).stream()
                .map(CampaignMapper::toModifierRequirement)
                .toList();
    }

    List<CampaignDifficultyModifier> loadDifficultyModifierLinks(Collection<UUID> difficultyIds) {
        if (difficultyIds.isEmpty()) {
            return List.of();
        }
        return campaignDifficultyModifierRepository.findByCampaignDifficulty_IdIn(difficultyIds);
    }

    static Map<UUID, List<CampaignModifierRequirementResponse>> groupModifierResponses(
            Collection<CampaignDifficultyModifier> links) {
        return links.stream().collect(Collectors.groupingBy(
                link -> link.getId().getCampaignDifficultyId(),
                Collectors.mapping(CampaignMapper::toModifierRequirement, Collectors.toList())));
    }

    private static CampaignModifierRequirementResponse toModifierRequirement(CampaignDifficultyModifier link) {
        return CampaignModifierRequirementResponse.builder()
                .modifier(ModifierService.toResponse(link.getModifier()))
                .requirement(link.getRequirement())
                .build();
    }

    Map<UUID, List<CampaignDifficultyTarget>> loadTargetsBulk(Collection<UUID> difficultyIds) {
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

    static List<CampaignTargetResponse> toTargetResponses(List<CampaignDifficultyTarget> targets) {
        return targets.stream().map(CampaignMapper::toTargetResponse).toList();
    }

    static CampaignTargetResponse toTargetResponse(CampaignDifficultyTarget target) {
        return CampaignTargetResponse.builder()
                .id(target.getId())
                .requirementType(target.getRequirementType())
                .requirementValue(target.getRequirementValue())
                .requirementValueMax(target.getRequirementValueMax())
                .build();
    }

    Map<UUID, Double> loadComplexitiesBulk(Collection<CampaignDifficulty> difficulties) {
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

    CampaignResponse toCampaignResponse(Campaign campaign) {
        return toCampaignResponse(campaign,
                loadTagResponses(campaign.getId()),
                (int) campaignDifficultyRepository.countByCampaign_IdAndBarrierFalseAndActiveTrue(campaign.getId()),
                loadCompletionItems(campaign.getId()));
    }

    CampaignResponse toCampaignResponse(Campaign campaign, List<CampaignTagResponse> tags,
            int difficultyCount, List<CampaignItemAwardResponse> completionItems) {
        return toCampaignResponse(campaign, new CampaignRow(tags, difficultyCount, null, completionItems,
                CampaignRewards.none(), loadStaffRefs(List.of(campaign)), null, null));
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

    private static CampaignBackgroundPlacement staticBackground(Campaign campaign) {
        CampaignBackgroundPlacement placement = campaign.getBackground();
        if (placement == null || placement.getSize() == null || placement.getX() == null
                || placement.getY() == null) {
            return null;
        }
        return placement;
    }

    private static CampaignResponse toCampaignResponse(Campaign campaign, CampaignRow row) {
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
                .myProgressStatus(row.myProgressStatus())
                .myCompletedDifficulties(row.myCompletedDifficulties())
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

    static CampaignConnectionResponse toConnection(CampaignDifficultyPath path) {
        return CampaignConnectionResponse.builder()
                .comesFromCampaignDifficultyId(path.getComesFromCampaignDifficulty().getId())
                .color(path.getColor())
                .build();
    }

    List<CampaignConnectionResponse> loadConnections(UUID campaignDifficultyId) {
        return campaignDifficultyPathRepository.findByCampaignDifficulty_IdAndActiveTrue(campaignDifficultyId)
                .stream().map(CampaignMapper::toConnection).toList();
    }

    static List<CampaignConnectionResponse> echoConnections(List<CampaignConnectionRequest> prerequisites) {
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

    CampaignDifficultyResponse toNodeResponse(CampaignDifficulty difficulty) {
        return toCampaignDifficultyResponse(difficulty, new NodeAssets(loadConnections(difficulty.getId()),
                loadDifficultyItems(difficulty.getId()),
                loadDifficultyModifiers(difficulty.getId()),
                loadTargets(difficulty.getId()),
                loadComplexity(difficulty.getMapDifficulty().getId())));
    }

    CampaignDifficultyResponse toNewNodeResponse(CampaignDifficulty difficulty,
            List<CampaignConnectionRequest> prerequisites, List<CampaignDifficultyTarget> targets) {
        return toCampaignDifficultyResponse(difficulty, new NodeAssets(
                echoConnections(prerequisites),
                List.of(),
                loadDifficultyModifiers(difficulty.getId()),
                toTargetResponses(targets),
                loadComplexity(difficulty.getMapDifficulty().getId())));
    }

    static CampaignDifficultyResponse toCampaignDifficultyResponse(CampaignDifficulty difficulty,
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

    static CampaignBarrierResponse toBarrierResponse(CampaignDifficulty barrier,
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

    static CampaignTextResponse toTextResponse(CampaignText text) {
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

    UserCampaignResponse toUserCampaignResponse(UserCampaign uc) {
        int completed = (int) userCampaignScoreRepository
                .countActiveByUserAndCampaign(uc.getUser().getId(), uc.getCampaign().getId());
        return toUserCampaignResponse(uc, toCampaignResponse(uc.getCampaign()), completed);
    }

    static UserCampaignResponse toUserCampaignResponse(UserCampaign uc, CampaignResponse campaign,
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

    static CampaignTagResponse toTagResponse(CampaignTag tag) {
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
                .item(ItemMapper.toItemResponse(link.getItem()))
                .build();
    }

    private static CampaignItemAwardResponse toItemAward(CampaignCompletionItem link) {
        return CampaignItemAwardResponse.builder()
                .itemId(link.getItem().getId())
                .itemName(link.getItem().getName())
                .quantity(link.getQuantity())
                .item(ItemMapper.toItemResponse(link.getItem()))
                .build();
    }
}
