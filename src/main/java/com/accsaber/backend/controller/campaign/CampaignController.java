package com.accsaber.backend.controller.campaign;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.model.dto.request.campaign.AddCampaignBarrierRequest;
import com.accsaber.backend.model.dto.request.campaign.AddCampaignDifficultyRequest;
import com.accsaber.backend.model.dto.request.campaign.CampaignFilter;
import com.accsaber.backend.model.dto.request.campaign.CampaignTextRequest;
import com.accsaber.backend.model.dto.request.campaign.CampaignVoteRequest;
import com.accsaber.backend.model.dto.request.campaign.CreateCampaignRequest;
import com.accsaber.backend.model.dto.request.campaign.MoveCampaignElementsRequest;
import com.accsaber.backend.model.dto.request.campaign.SetCampaignItemRequest;
import com.accsaber.backend.model.dto.request.campaign.UpdateCampaignBarrierRequest;
import com.accsaber.backend.model.dto.request.campaign.UpdateCampaignDifficultyRequest;
import com.accsaber.backend.model.dto.request.campaign.UpdateCampaignRequest;
import com.accsaber.backend.model.dto.request.map.ImportCampaignMapRequest;
import com.accsaber.backend.model.dto.response.campaign.CampaignBarrierResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignDetailResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignDifficultyResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignItemAwardResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignProgressResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignTagResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignTextResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignVoteResponse;
import com.accsaber.backend.model.dto.response.campaign.UserCampaignResponse;
import com.accsaber.backend.model.dto.response.map.PublicMapDifficultyResponse;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.campaign.CampaignTagKind;
import com.accsaber.backend.model.entity.campaign.UserCampaignStatus;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.security.StaffPrincipals;
import com.accsaber.backend.service.campaign.CampaignEditor;
import com.accsaber.backend.service.campaign.CampaignService;
import com.accsaber.backend.service.map.MapImportService;
import com.accsaber.backend.service.map.MapService;
import com.accsaber.backend.service.media.MediaFormat;
import com.accsaber.backend.service.media.MediaProcessingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/campaigns")
@RequiredArgsConstructor
@Tag(name = "Campaigns")
public class CampaignController {

    private static final String CAMPAIGN_BACKGROUND_SUBDIR = "campaigns";
    private static final String CAMPAIGN_ICON_SUBDIR = "campaign-icons";
    private static final String CAMPAIGN_CHECKPOINT_SUBDIR = "campaign-checkpoints";
    private static final String CAMPAIGN_NODE_BORDER_SUBDIR = "campaign-node-borders";

    private final CampaignService campaignService;
    private final MapImportService mapImportService;
    private final MediaProcessingService mediaProcessingService;

    private static CampaignEditor editorFor(Authentication authentication, PlayerUserDetails principal) {
        if (StaffPrincipals.canViewCampaignDrafts(authentication)) {
            return CampaignEditor.staff(StaffPrincipals.viewerIdOf(authentication));
        }
        if (principal == null) {
            throw new UnauthorizedException("Player authentication required");
        }
        return CampaignEditor.player(principal.getUserId());
    }

    @Operation(summary = "List campaigns", description = "Filter by status, tag, creator, official, loved and "
            + "search. XP and reward totals only show up on this list.")
    @GetMapping
    public ResponseEntity<Page<CampaignResponse>> listCampaigns(
            @RequestParam(required = false) List<CampaignStatus> status,
            @RequestParam(required = false) List<UUID> tagIds,
            @RequestParam(required = false) Long creatorId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean official,
            @RequestParam(required = false) Boolean loved,
            Authentication authentication,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        CampaignFilter filter = CampaignFilter.builder()
                .status(status).tagIds(tagIds).creatorId(creatorId).search(search)
                .official(official).loved(loved).build();
        return ResponseEntity.ok(campaignService.findCampaigns(filter,
                StaffPrincipals.viewerIdOf(authentication),
                StaffPrincipals.canViewCampaignDrafts(authentication), pageable));
    }

    @Operation(summary = "Get a campaign", description = "Nodes, barriers and text, everything to draw the map. No "
            + "reward totals here.")
    @GetMapping("/{campaignId}")
    public ResponseEntity<CampaignDetailResponse> getCampaign(
            @PathVariable UUID campaignId,
            Authentication authentication) {
        return ResponseEntity.ok(campaignService.findCampaignById(campaignId,
                StaffPrincipals.viewerIdOf(authentication),
                StaffPrincipals.canViewCampaignDrafts(authentication)));
    }

    @Operation(summary = "Campaign by slug")
    @GetMapping("/slug/{slug}")
    public ResponseEntity<CampaignDetailResponse> getCampaignBySlug(
            @PathVariable String slug,
            Authentication authentication) {
        return ResponseEntity.ok(campaignService.findCampaignBySlug(slug,
                StaffPrincipals.viewerIdOf(authentication),
                StaffPrincipals.canViewCampaignDrafts(authentication)));
    }

    @Operation(summary = "Campaign tags", description = "Pass kind to filter to one kind of tag.")
    @GetMapping("/tags")
    public ResponseEntity<List<CampaignTagResponse>> listTags(
            @RequestParam(required = false) CampaignTagKind kind) {
        return ResponseEntity.ok(kind != null ? campaignService.listTagsByKind(kind) : campaignService.listTags());
    }

    @Operation(summary = "Start a campaign", description = "Unlocks the opening nodes. Scores set before a node "
            + "unlocks never count for it.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{campaignId}/start")
    public ResponseEntity<UserCampaignResponse> startCampaign(
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(campaignService.startCampaign(principal.getUserId(), campaignId));
    }

    @Operation(summary = "Abandon a campaign", description = "Completed nodes stay completed if you start again.")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{campaignId}/start")
    public ResponseEntity<Void> abandonCampaign(
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        campaignService.abandonCampaign(principal.getUserId(), campaignId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Vote on a campaign", description = "Up or down. A new vote replaces your old one.")
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{campaignId}/vote")
    public ResponseEntity<CampaignVoteResponse> voteOnCampaign(
            @PathVariable UUID campaignId,
            @Valid @RequestBody CampaignVoteRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(
                campaignService.vote(principal.getUserId(), campaignId, request.getDirection()));
    }

    @Operation(summary = "Clear your vote")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{campaignId}/vote")
    public ResponseEntity<CampaignVoteResponse> clearCampaignVote(
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(campaignService.clearVote(principal.getUserId(), campaignId));
    }

    @Operation(summary = "Your campaigns", description = "Campaigns you started, with progress. Same filters as "
            + "the list. progressStatus picks in progress or finished.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<Page<UserCampaignResponse>> listMyCampaigns(
            @RequestParam(required = false) List<CampaignStatus> status,
            @RequestParam(required = false) List<UUID> tagIds,
            @RequestParam(required = false) Long creatorId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean official,
            @RequestParam(required = false) Boolean loved,
            @RequestParam(required = false) List<UserCampaignStatus> progressStatus,
            @AuthenticationPrincipal PlayerUserDetails principal,
            Authentication authentication,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        CampaignFilter filter = CampaignFilter.builder()
                .status(status).tagIds(tagIds).creatorId(creatorId).search(search)
                .official(official).loved(loved)
                .participantId(principal.getUserId()).progressStatus(progressStatus).build();
        return ResponseEntity.ok(campaignService.listUserCampaigns(filter,
                StaffPrincipals.viewerIdOf(authentication),
                StaffPrincipals.canViewCampaignDrafts(authentication), pageable));
    }

    @Operation(summary = "Your progress in a campaign", description = "Per node unlocks and your best. Locked "
            + "nodes show no best.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{campaignId}/me/progress")
    public ResponseEntity<CampaignProgressResponse> getMyProgress(
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(campaignService.getUserProgress(principal.getUserId(), campaignId));
    }

    @Operation(summary = "Player progress by slug",
            description = "Public, for integrations like the Discord bot. Has per node completion, scores, the "
                    + "legacy completed path flag and the furthest milestone.")
    @GetMapping("/slug/{slug}/users/{userId}/progress")
    public ResponseEntity<CampaignProgressResponse> getUserProgressBySlug(
            @PathVariable String slug,
            @PathVariable Long userId) {
        return ResponseEntity.ok(campaignService.getUserProgressBySlug(userId, slug));
    }

    @Operation(summary = "Progress in several campaigns", description = "Pass the campaign ids you want.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me/progress")
    public ResponseEntity<List<CampaignProgressResponse>> getMyProgressBulk(
            @RequestParam("ids") List<UUID> ids,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(campaignService.getUserProgressBulk(principal.getUserId(), ids));
    }

    @Operation(summary = "Create a campaign", description = "Starts as a draft. Only you and your collaborators see "
            + "it until you publish.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<CampaignResponse> createMyCampaign(
            @Valid @RequestBody CreateCampaignRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(campaignService.createCampaignAsEditor(CampaignEditor.player(principal.getUserId()), request));
    }

    @Operation(summary = "Edit your campaign", description = "Name, description, difficulty and other details. "
            + "Still editable after curation.")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{campaignId}")
    public ResponseEntity<CampaignResponse> updateMyCampaign(
            @PathVariable UUID campaignId,
            @Valid @RequestBody UpdateCampaignRequest request,
            Authentication authentication,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(
                campaignService.updateCampaignAsEditor(editorFor(authentication, principal), campaignId, request));
    }

    @Operation(summary = "Publish your campaign", description = "Needs at least one terminal node. It gives no XP "
            + "until a curator looks at it.")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{campaignId}/publish")
    public ResponseEntity<CampaignResponse> publishMyCampaign(
            @PathVariable UUID campaignId,
            Authentication authentication,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(campaignService.publishAsEditor(editorFor(authentication, principal), campaignId));
    }

    @Operation(summary = "Unpublish your campaign", description = "Back to draft. Players keep their progress.")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{campaignId}/unpublish")
    public ResponseEntity<CampaignResponse> unpublishMyCampaign(
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(campaignService.unpublishAsEditor(CampaignEditor.player(principal.getUserId()), campaignId));
    }

    @Operation(summary = "Delete your campaign", description = "Deactivates a draft you own.")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{campaignId}")
    public ResponseEntity<Void> deactivateMyCampaign(
            @PathVariable UUID campaignId,
            Authentication authentication,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        campaignService.deactivateCampaignAsEditor(editorFor(authentication, principal), campaignId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Import a map for campaigns", description = "Unranked maps only, needs a BeatLeader "
            + "leaderboard ID and optionally a ScoreSaber one. Limit 100 per player. Known maps attach instead of "
            + "failing.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/maps/import")
    public ResponseEntity<PublicMapDifficultyResponse> importCampaignMap(
            @Valid @RequestBody ImportCampaignMapRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(MapService.toPublicDifficultyResponse(
                mapImportService.importCampaignMap(principal.getUserId(), request)));
    }

    @Operation(summary = "Add a node")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{campaignId}/difficulties")
    public ResponseEntity<CampaignDifficultyResponse> addDifficultyToMyCampaign(
            @PathVariable UUID campaignId,
            @Valid @RequestBody AddCampaignDifficultyRequest request,
            Authentication authentication,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(campaignService.addDifficultyAsEditor(editorFor(authentication, principal), campaignId, request));
    }

    @Operation(summary = "Update a node", description = "Changing the objective on a live campaign recounts "
            + "everyone's progress on that node.")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/difficulties/{campaignDifficultyId}")
    public ResponseEntity<CampaignDifficultyResponse> updateDifficultyOnMyCampaign(
            @PathVariable UUID campaignDifficultyId,
            @Valid @RequestBody UpdateCampaignDifficultyRequest request,
            Authentication authentication,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(
                campaignService.updateDifficultyAsEditor(editorFor(authentication, principal), campaignDifficultyId, request));
    }

    @Operation(summary = "Swap a node's map", description = "Other campaigns using the same map are "
            + "not affected.")
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/difficulties/{campaignDifficultyId}/map")
    public ResponseEntity<CampaignDifficultyResponse> updateDifficultyMapOnMyCampaign(
            @PathVariable UUID campaignDifficultyId,
            @Valid @RequestBody ImportCampaignMapRequest request,
            Authentication authentication,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(
                campaignService.updateDifficultyMapAsEditor(editorFor(authentication, principal), campaignDifficultyId, request));
    }

    @Operation(summary = "Remove a node")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{campaignId}/difficulties/{campaignDifficultyId}")
    public ResponseEntity<Void> removeDifficultyFromMyCampaign(
            @PathVariable UUID campaignId,
            @PathVariable UUID campaignDifficultyId,
            Authentication authentication,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        campaignService.removeDifficultyAsEditor(editorFor(authentication, principal), campaignId, campaignDifficultyId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Set a node reward", description = "Adds an item or changes its quantity. Only curated "
            + "campaigns give these out.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/difficulties/{campaignDifficultyId}/items")
    public ResponseEntity<List<CampaignItemAwardResponse>> setDifficultyItemOnMyCampaign(
            @PathVariable UUID campaignDifficultyId,
            @Valid @RequestBody SetCampaignItemRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(campaignService.setDifficultyItemAsEditor(
                CampaignEditor.player(principal.getUserId()), campaignDifficultyId, request));
    }

    @Operation(summary = "Remove a node reward")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/difficulties/{campaignDifficultyId}/items/{itemId}")
    public ResponseEntity<List<CampaignItemAwardResponse>> removeDifficultyItemFromMyCampaign(
            @PathVariable UUID campaignDifficultyId,
            @PathVariable UUID itemId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(campaignService.removeDifficultyItemAsEditor(
                CampaignEditor.player(principal.getUserId()), campaignDifficultyId, itemId));
    }

    @Operation(summary = "Set a completion reward", description = "Item given for finishing the whole campaign.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{campaignId}/completion-items")
    public ResponseEntity<List<CampaignItemAwardResponse>> setCompletionItemOnMyCampaign(
            @PathVariable UUID campaignId,
            @Valid @RequestBody SetCampaignItemRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(campaignService.setCompletionItemAsEditor(
                CampaignEditor.player(principal.getUserId()), campaignId, request));
    }

    @Operation(summary = "Remove a completion reward")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{campaignId}/completion-items/{itemId}")
    public ResponseEntity<List<CampaignItemAwardResponse>> removeCompletionItemFromMyCampaign(
            @PathVariable UUID campaignId,
            @PathVariable UUID itemId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(campaignService.removeCompletionItemAsEditor(
                CampaignEditor.player(principal.getUserId()), campaignId, itemId));
    }

    @Operation(summary = "Upload a background", description = "Use the returned URL. The extension depends "
            + "on what you sent.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/{campaignId}/background", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CampaignResponse> uploadMyCampaignBackground(
            @PathVariable UUID campaignId,
            @RequestPart("file") MultipartFile file,
            Authentication authentication,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        campaignService.assertCanUploadCampaignMedia(editorFor(authentication, principal), campaignId);
        String url = mediaProcessingService.storeImage(file, CAMPAIGN_BACKGROUND_SUBDIR, campaignId.toString(),
                MediaFormat.GIF);
        return ResponseEntity.ok(
                campaignService.setBackgroundUrlAsEditor(editorFor(authentication, principal), campaignId, url));
    }

    @Operation(summary = "Remove the background")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{campaignId}/background")
    public ResponseEntity<CampaignResponse> deleteMyCampaignBackground(
            @PathVariable UUID campaignId,
            Authentication authentication,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        CampaignResponse result = campaignService.setBackgroundUrlAsEditor(editorFor(authentication, principal), campaignId, null);
        mediaProcessingService.deleteIfExists(CAMPAIGN_BACKGROUND_SUBDIR, campaignId.toString());
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Upload a campaign icon", description = "Use the returned URL.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/{campaignId}/icon", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CampaignResponse> uploadMyCampaignIcon(
            @PathVariable UUID campaignId,
            @RequestPart("file") MultipartFile file,
            Authentication authentication,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        campaignService.assertCanUploadCampaignMedia(editorFor(authentication, principal), campaignId);
        String url = mediaProcessingService.storeImage(file, CAMPAIGN_ICON_SUBDIR, campaignId.toString(),
                MediaFormat.GIF);
        return ResponseEntity.ok(
                campaignService.setIconUrlAsEditor(editorFor(authentication, principal), campaignId, url));
    }

    @Operation(summary = "Remove the campaign icon")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{campaignId}/icon")
    public ResponseEntity<CampaignResponse> deleteMyCampaignIcon(
            @PathVariable UUID campaignId,
            Authentication authentication,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        CampaignResponse result = campaignService.setIconUrlAsEditor(editorFor(authentication, principal), campaignId, null);
        mediaProcessingService.deleteIfExists(CAMPAIGN_ICON_SUBDIR, campaignId.toString());
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Upload a checkpoint avatar")
    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/difficulties/{campaignDifficultyId}/checkpoint-avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CampaignDifficultyResponse> uploadMyNodeCheckpointAvatar(
            @PathVariable UUID campaignDifficultyId,
            @RequestPart("file") MultipartFile file,
            Authentication authentication,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        campaignService.assertCanUploadDifficultyMedia(editorFor(authentication, principal), campaignDifficultyId);
        String url = mediaProcessingService.storeImage(file, CAMPAIGN_CHECKPOINT_SUBDIR,
                campaignDifficultyId.toString(), MediaFormat.GIF);
        UpdateCampaignDifficultyRequest request = new UpdateCampaignDifficultyRequest();
        request.setCheckpointAvatarUrl(url);
        return ResponseEntity.ok(
                campaignService.updateDifficultyAsEditor(editorFor(authentication, principal), campaignDifficultyId, request));
    }

    @Operation(summary = "Remove a checkpoint avatar")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/difficulties/{campaignDifficultyId}/checkpoint-avatar")
    public ResponseEntity<CampaignDifficultyResponse> deleteMyNodeCheckpointAvatar(
            @PathVariable UUID campaignDifficultyId,
            Authentication authentication,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        UpdateCampaignDifficultyRequest request = new UpdateCampaignDifficultyRequest();
        request.setCheckpointAvatarUrl("");
        CampaignDifficultyResponse result = campaignService.updateDifficultyAsEditor(
                editorFor(authentication, principal), campaignDifficultyId, request);
        mediaProcessingService.deleteIfExists(CAMPAIGN_CHECKPOINT_SUBDIR, campaignDifficultyId.toString());
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Upload a node border", description = "The layer picks a frame over the cover or a "
            + "backplate behind it. Animated GIFs stay animated. Use the returned URL.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/difficulties/{campaignDifficultyId}/node-border", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CampaignDifficultyResponse> uploadMyNodeBorder(
            @PathVariable UUID campaignDifficultyId,
            @RequestPart("file") MultipartFile file,
            Authentication authentication,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        campaignService.assertCanUploadDifficultyMedia(editorFor(authentication, principal), campaignDifficultyId);
        String url = mediaProcessingService.storeImage(file, CAMPAIGN_NODE_BORDER_SUBDIR,
                campaignDifficultyId.toString(), MediaFormat.GIF);
        UpdateCampaignDifficultyRequest request = new UpdateCampaignDifficultyRequest();
        request.setNodeBorderUrl(url);
        return ResponseEntity.ok(
                campaignService.updateDifficultyAsEditor(editorFor(authentication, principal), campaignDifficultyId, request));
    }

    @Operation(summary = "Remove a node border")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/difficulties/{campaignDifficultyId}/node-border")
    public ResponseEntity<CampaignDifficultyResponse> deleteMyNodeBorder(
            @PathVariable UUID campaignDifficultyId,
            Authentication authentication,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        UpdateCampaignDifficultyRequest request = new UpdateCampaignDifficultyRequest();
        request.setNodeBorderUrl("");
        CampaignDifficultyResponse result = campaignService.updateDifficultyAsEditor(
                editorFor(authentication, principal), campaignDifficultyId, request);
        mediaProcessingService.deleteIfExists(CAMPAIGN_NODE_BORDER_SUBDIR, campaignDifficultyId.toString());
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Add a barrier", description = "Blocks players until a condition on the nodes behind it is "
            + "met. Gives its own XP when cleared.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{campaignId}/barriers")
    public ResponseEntity<CampaignBarrierResponse> addBarrierToMyCampaign(
            @PathVariable UUID campaignId,
            @Valid @RequestBody AddCampaignBarrierRequest request,
            Authentication authentication,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(campaignService.addBarrierAsEditor(editorFor(authentication, principal), campaignId, request));
    }

    @Operation(summary = "Update a barrier")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/barriers/{barrierId}")
    public ResponseEntity<CampaignBarrierResponse> updateBarrierOnMyCampaign(
            @PathVariable UUID barrierId,
            @Valid @RequestBody UpdateCampaignBarrierRequest request,
            Authentication authentication,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(
                campaignService.updateBarrierAsEditor(editorFor(authentication, principal), barrierId, request));
    }

    @Operation(summary = "Remove a barrier")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{campaignId}/barriers/{barrierId}")
    public ResponseEntity<Void> removeBarrierFromMyCampaign(
            @PathVariable UUID campaignId,
            @PathVariable UUID barrierId,
            Authentication authentication,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        campaignService.removeBarrierAsEditor(editorFor(authentication, principal), campaignId, barrierId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Bulk move elements", description = "Moves nodes, barriers and text in one go. Checked as one "
            + "layout. A moved block never collides with itself.")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{campaignId}/positions")
    public ResponseEntity<Void> moveElementsOnMyCampaign(
            @PathVariable UUID campaignId,
            @Valid @RequestBody MoveCampaignElementsRequest request,
            Authentication authentication,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        campaignService.moveElementsAsEditor(editorFor(authentication, principal), campaignId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Add text", description = "Formatting gets cleaned up on the server.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{campaignId}/texts")
    public ResponseEntity<CampaignTextResponse> addTextToMyCampaign(
            @PathVariable UUID campaignId,
            @Valid @RequestBody CampaignTextRequest request,
            Authentication authentication,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(campaignService.addTextAsEditor(editorFor(authentication, principal), campaignId, request));
    }

    @Operation(summary = "Edit text")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/texts/{textId}")
    public ResponseEntity<CampaignTextResponse> updateTextOnMyCampaign(
            @PathVariable UUID textId,
            @Valid @RequestBody CampaignTextRequest request,
            Authentication authentication,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(
                campaignService.updateTextAsEditor(editorFor(authentication, principal), textId, request));
    }

    @Operation(summary = "Remove text")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{campaignId}/texts/{textId}")
    public ResponseEntity<Void> removeTextFromMyCampaign(
            @PathVariable UUID campaignId,
            @PathVariable UUID textId,
            Authentication authentication,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        campaignService.removeTextAsEditor(editorFor(authentication, principal), campaignId, textId);
        return ResponseEntity.noContent().build();
    }
}
