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

    @Operation(summary = "List the campaigns", description = "Published campaigns, filterable by status, tag, creator, whether they are official or loved, and a search term. Totals for XP and rewards come back on this list but not on a single campaign, since working them out costs an extra query that is not worth it for one.")
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

    @Operation(summary = "Get one campaign", description = "A campaign with its nodes, barriers and text, which is everything you need to draw the map. Reward totals are left off here on purpose, so use the list if you want those.")
    @GetMapping("/{campaignId}")
    public ResponseEntity<CampaignDetailResponse> getCampaign(
            @PathVariable UUID campaignId,
            Authentication authentication) {
        return ResponseEntity.ok(campaignService.findCampaignById(campaignId,
                StaffPrincipals.viewerIdOf(authentication),
                StaffPrincipals.canViewCampaignDrafts(authentication)));
    }

    @Operation(summary = "Get one campaign by slug", description = "The same as above but addressed by the readable slug rather than the id, which is nicer in a URL.")
    @GetMapping("/slug/{slug}")
    public ResponseEntity<CampaignDetailResponse> getCampaignBySlug(
            @PathVariable String slug,
            Authentication authentication) {
        return ResponseEntity.ok(campaignService.findCampaignBySlug(slug,
                StaffPrincipals.viewerIdOf(authentication),
                StaffPrincipals.canViewCampaignDrafts(authentication)));
    }

    @Operation(summary = "List the campaign tags", description = "Tags campaigns can be filed under. Pass kind to narrow to one sort of tag.")
    @GetMapping("/tags")
    public ResponseEntity<List<CampaignTagResponse>> listTags(
            @RequestParam(required = false) CampaignTagKind kind) {
        return ResponseEntity.ok(kind != null ? campaignService.listTagsByKind(kind) : campaignService.listTags());
    }

    @Operation(summary = "Start a campaign", description = "Signs you up to a campaign and unlocks its opening nodes. Scores only count toward a node once it is unlocked for you, so nothing you played beforehand will retroactively complete anything.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{campaignId}/start")
    public ResponseEntity<UserCampaignResponse> startCampaign(
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(campaignService.startCampaign(principal.getUserId(), campaignId));
    }

    @Operation(summary = "Abandon a campaign", description = "Drops you out of a campaign. Nodes you already completed stay completed, so starting again later does not put you back at the beginning.")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{campaignId}/start")
    public ResponseEntity<Void> abandonCampaign(
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        campaignService.abandonCampaign(principal.getUserId(), campaignId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Vote on a campaign", description = "Up or down vote a campaign. Sending a new vote replaces your old one rather than adding to it.")
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{campaignId}/vote")
    public ResponseEntity<CampaignVoteResponse> voteOnCampaign(
            @PathVariable UUID campaignId,
            @Valid @RequestBody CampaignVoteRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(
                campaignService.vote(principal.getUserId(), campaignId, request.getDirection()));
    }

    @Operation(summary = "Clear your vote", description = "Removes your vote from a campaign entirely, which is different from voting the other way.")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{campaignId}/vote")
    public ResponseEntity<CampaignVoteResponse> clearCampaignVote(
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(campaignService.clearVote(principal.getUserId(), campaignId));
    }

    @Operation(summary = "List your campaigns", description = "Campaigns you have started, with how far through each one you are. Filters and sorting work exactly as they do on the campaign list, and progressStatus narrows it to the ones you are still playing or have finished.")
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

    @Operation(summary = "Get your progress in a campaign", description = "Node by node progress for one campaign, including which nodes are unlocked and your best on each. Locked nodes deliberately show no best, since you are not meant to be able to see ahead.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{campaignId}/me/progress")
    public ResponseEntity<CampaignProgressResponse> getMyProgress(
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(campaignService.getUserProgress(principal.getUserId(), campaignId));
    }

    @Operation(summary = "Get a player's campaign progress by campaign slug",
            description = "Public read for integrations (e.g. the Discord bot). Includes per-node completion,"
                    + " scores, the legacy completed-path flag per node, and the furthest reached milestone.")
    @GetMapping("/slug/{slug}/users/{userId}/progress")
    public ResponseEntity<CampaignProgressResponse> getUserProgressBySlug(
            @PathVariable String slug,
            @PathVariable Long userId) {
        return ResponseEntity.ok(campaignService.getUserProgressBySlug(userId, slug));
    }

    @Operation(summary = "Get your progress in several campaigns", description = "Progress for a list of campaigns in one call, which saves hammering the single campaign route when you are drawing a list. Pass the ids you care about.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me/progress")
    public ResponseEntity<List<CampaignProgressResponse>> getMyProgressBulk(
            @RequestParam("ids") List<UUID> ids,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(campaignService.getUserProgressBulk(principal.getUserId(), ids));
    }

    @Operation(summary = "Create a campaign", description = "Starts a new campaign as a draft. Drafts are only visible to you and anyone you invite as a collaborator until you publish.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<CampaignResponse> createMyCampaign(
            @Valid @RequestBody CreateCampaignRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(campaignService.createCampaignAsEditor(CampaignEditor.player(principal.getUserId()), request));
    }

    @Operation(summary = "Update your campaign", description = "Changes the name, description, difficulty and other details. Once a campaign has been curated its structure locks, but the metadata here can still be edited.")
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

    @Operation(summary = "Publish your campaign", description = "Makes a draft visible to everyone and lets people start it. Before this goes through you need at least one node flagged as terminal. You can flag as many as you like. Publishing does not make it hand out XP, that only happens once a curator has looked at it.")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{campaignId}/publish")
    public ResponseEntity<CampaignResponse> publishMyCampaign(
            @PathVariable UUID campaignId,
            Authentication authentication,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(campaignService.publishAsEditor(editorFor(authentication, principal), campaignId));
    }

    @Operation(summary = "Unpublish your campaign", description = "Takes a published campaign back to draft so you can change it. Anyone partway through keeps their progress.")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{campaignId}/unpublish")
    public ResponseEntity<CampaignResponse> unpublishMyCampaign(
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(campaignService.unpublishAsEditor(CampaignEditor.player(principal.getUserId()), campaignId));
    }

    @Operation(summary = "Delete your campaign", description = "Deactivates a draft you own. The row stays behind rather than being properly deleted, so nothing referencing it breaks.")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{campaignId}")
    public ResponseEntity<Void> deactivateMyCampaign(
            @PathVariable UUID campaignId,
            Authentication authentication,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        campaignService.deactivateCampaignAsEditor(editorFor(authentication, principal), campaignId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Import a map for your campaign", description = "Brings in a map that is not ranked so you can use it in a campaign. Give it a BeatLeader leaderboard id, and a ScoreSaber one too if you have it. There is a limit of 100 imports per player, and importing something already known attaches to the existing entry rather than failing. Imports nothing is using any more are freed up automatically.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/maps/import")
    public ResponseEntity<PublicMapDifficultyResponse> importCampaignMap(
            @Valid @RequestBody ImportCampaignMapRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(MapService.toPublicDifficultyResponse(
                mapImportService.importCampaignMap(principal.getUserId(), request)));
    }

    @Operation(summary = "Add a node to your campaign", description = "Puts a map on the campaign map as a node, with its position, objective and reward.")
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

    @Operation(summary = "Update a node", description = "Changes a node, whether that is where it sits, what it asks for, or what it pays out. Changing the objective on a live campaign makes everyone's progress on that node get worked out again.")
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

    @Operation(summary = "Repoint a node at a different map", description = "Swaps which map a node refers to. The shared map entry is never edited, so other campaigns using the same map are left alone.")
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

    @Operation(summary = "Remove a node", description = "Takes a node off the campaign map.")
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

    @Operation(summary = "Set a node reward", description = "Attaches an item to a node, or changes the quantity if one is already there. Only curated campaigns actually hand these out.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/difficulties/{campaignDifficultyId}/items")
    public ResponseEntity<List<CampaignItemAwardResponse>> setDifficultyItemOnMyCampaign(
            @PathVariable UUID campaignDifficultyId,
            @Valid @RequestBody SetCampaignItemRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(campaignService.setDifficultyItemAsEditor(
                CampaignEditor.player(principal.getUserId()), campaignDifficultyId, request));
    }

    @Operation(summary = "Remove a node reward", description = "Takes an item reward back off a node.")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/difficulties/{campaignDifficultyId}/items/{itemId}")
    public ResponseEntity<List<CampaignItemAwardResponse>> removeDifficultyItemFromMyCampaign(
            @PathVariable UUID campaignDifficultyId,
            @PathVariable UUID itemId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(campaignService.removeDifficultyItemAsEditor(
                CampaignEditor.player(principal.getUserId()), campaignDifficultyId, itemId));
    }

    @Operation(summary = "Set a completion reward", description = "Attaches an item to the reward for finishing the whole campaign, rather than to a single node.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{campaignId}/completion-items")
    public ResponseEntity<List<CampaignItemAwardResponse>> setCompletionItemOnMyCampaign(
            @PathVariable UUID campaignId,
            @Valid @RequestBody SetCampaignItemRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(campaignService.setCompletionItemAsEditor(
                CampaignEditor.player(principal.getUserId()), campaignId, request));
    }

    @Operation(summary = "Remove a completion reward", description = "Takes an item back off the completion bonus.")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{campaignId}/completion-items/{itemId}")
    public ResponseEntity<List<CampaignItemAwardResponse>> removeCompletionItemFromMyCampaign(
            @PathVariable UUID campaignId,
            @PathVariable UUID itemId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(campaignService.removeCompletionItemAsEditor(
                CampaignEditor.player(principal.getUserId()), campaignId, itemId));
    }

    @Operation(summary = "Upload a campaign background", description = "Sets the image behind the campaign map. Use the URL you get back rather than assuming the extension, since what we store depends on what you sent.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/{campaignId}/background", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CampaignResponse> uploadMyCampaignBackground(
            @PathVariable UUID campaignId,
            @RequestPart("file") MultipartFile file,
            Authentication authentication,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        campaignService.assertCanUploadCampaignMedia(editorFor(authentication, principal), campaignId);
        String url = mediaProcessingService.storeImage(file, CAMPAIGN_BACKGROUND_SUBDIR, campaignId.toString(),
                MediaFormat.PNG);
        return ResponseEntity.ok(
                campaignService.setBackgroundUrlAsEditor(editorFor(authentication, principal), campaignId, url));
    }

    @Operation(summary = "Remove the campaign background", description = "Clears the background image.")
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

    @Operation(summary = "Upload a campaign icon", description = "Sets the campaign icon. Same note about using the returned URL.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/{campaignId}/icon", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CampaignResponse> uploadMyCampaignIcon(
            @PathVariable UUID campaignId,
            @RequestPart("file") MultipartFile file,
            Authentication authentication,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        campaignService.assertCanUploadCampaignMedia(editorFor(authentication, principal), campaignId);
        String url = mediaProcessingService.storeImage(file, CAMPAIGN_ICON_SUBDIR, campaignId.toString(),
                MediaFormat.PNG);
        return ResponseEntity.ok(
                campaignService.setIconUrlAsEditor(editorFor(authentication, principal), campaignId, url));
    }

    @Operation(summary = "Remove the campaign icon", description = "Clears the campaign icon.")
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

    @Operation(summary = "Upload a checkpoint avatar", description = "Sets the avatar shown on a checkpoint node.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/difficulties/{campaignDifficultyId}/checkpoint-avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CampaignDifficultyResponse> uploadMyNodeCheckpointAvatar(
            @PathVariable UUID campaignDifficultyId,
            @RequestPart("file") MultipartFile file,
            Authentication authentication,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        campaignService.assertCanUploadDifficultyMedia(editorFor(authentication, principal), campaignDifficultyId);
        String url = mediaProcessingService.storeImage(file, CAMPAIGN_CHECKPOINT_SUBDIR,
                campaignDifficultyId.toString(), MediaFormat.PNG);
        UpdateCampaignDifficultyRequest request = new UpdateCampaignDifficultyRequest();
        request.setCheckpointAvatarUrl(url);
        return ResponseEntity.ok(
                campaignService.updateDifficultyAsEditor(editorFor(authentication, principal), campaignDifficultyId, request));
    }

    @Operation(summary = "Remove a checkpoint avatar", description = "Clears the checkpoint avatar.")
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

    @Operation(summary = "Upload a node border", description = "Sets a border image around a node. Depending on the layer it either frames the cover or sits behind it as a backplate. Animated GIFs keep their animation here, unlike most of our uploads, so use the URL you get back.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/difficulties/{campaignDifficultyId}/node-border", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CampaignDifficultyResponse> uploadMyNodeBorder(
            @PathVariable UUID campaignDifficultyId,
            @RequestPart("file") MultipartFile file,
            Authentication authentication,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        campaignService.assertCanUploadDifficultyMedia(editorFor(authentication, principal), campaignDifficultyId);
        String url = mediaProcessingService.storeImage(file, CAMPAIGN_NODE_BORDER_SUBDIR,
                campaignDifficultyId.toString(), MediaFormat.PNG);
        UpdateCampaignDifficultyRequest request = new UpdateCampaignDifficultyRequest();
        request.setNodeBorderUrl(url);
        return ResponseEntity.ok(
                campaignService.updateDifficultyAsEditor(editorFor(authentication, principal), campaignDifficultyId, request));
    }

    @Operation(summary = "Remove a node border", description = "Clears the node border image.")
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

    @Operation(summary = "Add a barrier", description = "Barriers sit between nodes and hold players back until a condition across the nodes behind them is met. They pay out XP of their own when cleared.")
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

    @Operation(summary = "Update a barrier", description = "Changes a barrier, its condition, or which nodes it looks at.")
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

    @Operation(summary = "Remove a barrier", description = "Takes a barrier off the campaign map.")
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

    @Operation(summary = "Move elements", description = "Repositions several nodes, barriers and text elements at once. The whole move is validated as one layout, so shifting a block of nodes never collides with the block itself.")
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

    @Operation(summary = "Add a text element", description = "Places some text on the campaign map, for titles, notes or flavour. Formatting is cleaned up server side.")
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

    @Operation(summary = "Update a text element", description = "Changes the content or position of a piece of text.")
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

    @Operation(summary = "Remove a text element", description = "Takes a piece of text off the campaign map.")
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
