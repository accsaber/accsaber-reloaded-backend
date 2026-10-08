package com.accsaber.backend.controller.playlist;

import java.util.Map;
import java.util.Optional;

import java.util.UUID;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.map.Batch;
import com.accsaber.backend.model.entity.score.SnipeSort;
import com.accsaber.backend.model.entity.score.SnipeUnplayed;
import com.accsaber.backend.repository.campaign.CampaignRepository;
import com.accsaber.backend.repository.map.BatchRepository;
import com.accsaber.backend.service.clan.war.ClanWarPoolService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import com.accsaber.backend.service.infra.CategoryService;
import com.accsaber.backend.service.playlist.PlaylistService;
import com.accsaber.backend.service.snipe.SnipeQuery;
import com.accsaber.backend.service.snipe.SnipeSelection;
import com.accsaber.backend.service.snipe.SnipeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/playlists")
@RequiredArgsConstructor
@Tag(name = "Playlists")
public class PlaylistController {

        private final PlaylistService playlistService;
        private final CategoryService categoryService;
        private final BatchRepository batchRepository;
        private final CampaignRepository campaignRepository;
        private final SnipeService snipeService;
        private final ClanWarPoolService clanWarPoolService;

        @Operation(summary = "Category playlist", description = "Keeps itself up to date through "
                + "its syncURL.")
        @GetMapping(value = "/{category}", produces = "application/json")
        public ResponseEntity<Map<String, Object>> getPlaylistByPath(
                        @Parameter(description = "Category code, like true_acc") @PathVariable String category) {
                return buildPlaylistResponse(category);
        }

        @Operation(summary = "Unplayed ranked maps", description = "Pass overall for every category.")
        @GetMapping(value = "/missing/{userId}/{category}", produces = "application/json")
        public ResponseEntity<Map<String, Object>> getMissingPlaylistByPath(
                        @Parameter(description = "Player user ID") @PathVariable Long userId,
                        @Parameter(description = "Category code or overall") @PathVariable String category) {
                return buildMissingPlaylistResponse(category, userId);
        }

        @Operation(summary = "Queue and qualified maps")
        @GetMapping(value = "/unranked/{category}", produces = "application/json")
        public ResponseEntity<Map<String, Object>> getUnrankedPlaylistByPath(
                        @Parameter(description = "Category code, like true_acc") @PathVariable String category) {
                return buildUnrankedPlaylistResponse(category);
        }

        @Operation(summary = "Snipe playlist", description = "Maps where the target beats you, closest "
                + "gap first. Covers every category. Cover image is the target's avatar.")
        @GetMapping(value = "/snipe/{sniperId}/{targetId}", produces = "application/json")
        public ResponseEntity<Map<String, Object>> getSnipePlaylist(
                        @Parameter(description = "Your user ID") @PathVariable Long sniperId,
                        @Parameter(description = "Target's user ID") @PathVariable Long targetId,
                        @Parameter(description = "GAP, AP_GAP, TARGET_AP, YOUR_AP or RANK_GAP") @RequestParam(defaultValue = "GAP") SnipeSort sort,
                        @Parameter(description = "ASC or DESC, each sort has a default") @RequestParam(required = false) Sort.Direction direction,
                        @Parameter(description = "EXCLUDE = played only, INCLUDE = add unplayed, ONLY = unplayed only") @RequestParam(required = false) SnipeUnplayed unplayed) {
                return buildSnipePlaylistResponse(new SnipeQuery(sniperId, targetId, null, sort, direction, unplayed), 0);
        }

        @Operation(summary = "Capped snipe playlist", description = "Size 0 means no cap.")
        @GetMapping(value = "/snipe/{sniperId}/{targetId}/{size}", produces = "application/json")
        public ResponseEntity<Map<String, Object>> getSnipePlaylistBySize(
                        @Parameter(description = "Your user ID") @PathVariable Long sniperId,
                        @Parameter(description = "Target's user ID") @PathVariable Long targetId,
                        @Parameter(description = "Max maps, 0 = no cap") @PathVariable int size,
                        @Parameter(description = "GAP, AP_GAP, TARGET_AP, YOUR_AP or RANK_GAP") @RequestParam(defaultValue = "GAP") SnipeSort sort,
                        @Parameter(description = "ASC or DESC, each sort has a default") @RequestParam(required = false) Sort.Direction direction,
                        @Parameter(description = "EXCLUDE = played only, INCLUDE = add unplayed, ONLY = unplayed only") @RequestParam(required = false) SnipeUnplayed unplayed) {
                return buildSnipePlaylistResponse(new SnipeQuery(sniperId, targetId, null, sort, direction, unplayed), size);
        }

        @Operation(summary = "Snipe one category", description = "Size 0 means no cap. "
                + "Category overall means every category.")
        @GetMapping(value = "/snipe/{sniperId}/{targetId}/{size}/{category}", produces = "application/json")
        public ResponseEntity<Map<String, Object>> getSnipePlaylistBySizeAndCategory(
                        @Parameter(description = "Your user ID") @PathVariable Long sniperId,
                        @Parameter(description = "Target's user ID") @PathVariable Long targetId,
                        @Parameter(description = "Max maps, 0 = no cap") @PathVariable int size,
                        @Parameter(description = "Category code") @PathVariable String category,
                        @Parameter(description = "GAP, AP_GAP, TARGET_AP, YOUR_AP or RANK_GAP") @RequestParam(defaultValue = "GAP") SnipeSort sort,
                        @Parameter(description = "ASC or DESC, each sort has a default") @RequestParam(required = false) Sort.Direction direction,
                        @Parameter(description = "EXCLUDE = played only, INCLUDE = add unplayed, ONLY = unplayed only") @RequestParam(required = false) SnipeUnplayed unplayed) {
                return buildSnipePlaylistResponse(new SnipeQuery(sniperId, targetId, category, sort, direction, unplayed),
                                size);
        }

        @Operation(summary = "Player scores as a playlist", description = "Same filters, sort and "
                + "paging as the scores route. You get whatever that page shows.")
        @GetMapping(value = "/scores/{userId}", produces = "application/json")
        public ResponseEntity<Map<String, Object>> getUserScoresPlaylist(
                        @Parameter(description = "Player user ID") @PathVariable Long userId,
                        @Parameter(description = "Category UUID or code") @RequestParam(required = false) String categoryId,
                        @Parameter(description = "Song name search") @RequestParam(required = false) String search,
                        @PageableDefault(size = 25, sort = "ap", direction = Sort.Direction.DESC) Pageable pageable) {
                return buildUserScoresPlaylistResponse(userId, categoryId, search, pageable);
        }

        @Operation(summary = "Batch playlist")
        @GetMapping(value = "/batch/{batchId}", produces = "application/json")
        public ResponseEntity<Map<String, Object>> getBatchPlaylist(
                        @Parameter(description = "Batch ID") @PathVariable UUID batchId) {
                return buildBatchPlaylistResponse(batchId);
        }

        @Operation(summary = "Clan war pool playlist", description = "Live once the pool locks.")
        @GetMapping(value = "/clan-war/{warId}", produces = "application/json")
        public ResponseEntity<Map<String, Object>> getClanWarPlaylist(
                        @Parameter(description = "War ID") @PathVariable UUID warId) {
                ClanWarPoolService.PlaylistSource source = clanWarPoolService.playlistSource(warId);
                String syncUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                                .path("/v1/playlists/clan-war/{warId}")
                                .buildAndExpand(warId)
                                .toUriString();
                return ResponseEntity.ok(playlistService.generateClanWarPlaylist(source, syncUrl));
        }

        @Operation(summary = "Campaign playlist", description = "The creator has to turn on playlist "
                + "export. You get a 422 if they did not.")
        @GetMapping(value = "/campaign/{campaignId}", produces = "application/json")
        public ResponseEntity<Map<String, Object>> getCampaignPlaylist(
                        @Parameter(description = "Campaign ID") @PathVariable UUID campaignId) {
                return buildCampaignPlaylistResponse(campaignId);
        }

        private ResponseEntity<Map<String, Object>> buildCampaignPlaylistResponse(UUID campaignId) {
                Campaign campaign = campaignRepository.findByIdAndActiveTrue(campaignId)
                                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));
                if (!campaign.isPlaylistExportEnabled()) {
                        throw new ValidationException("Playlist export is not enabled for this campaign");
                }
                String syncUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                                .path("/v1/playlists/campaign/{campaignId}")
                                .buildAndExpand(campaignId)
                                .toUriString();

                Map<String, Object> playlist = playlistService.generateCampaignPlaylist(campaign, syncUrl);

                String filename = "accsaber-campaign-" + campaign.getSlug() + ".bplist";

                return ResponseEntity.ok()
                                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                                .header("Cache-Control", "no-store")
                                .body(playlist);
        }

        private ResponseEntity<Map<String, Object>> buildUserScoresPlaylistResponse(Long userId, String categoryId,
                        String search, Pageable pageable) {
                String syncUrl = ServletUriComponentsBuilder.fromCurrentRequest().toUriString();

                Map<String, Object> playlist = playlistService.generateUserScoresPlaylist(
                                userId, categoryService.resolveId(categoryId), search, pageable, syncUrl);

                String filename = "accsaber-scores-" + userId + ".bplist";

                return ResponseEntity.ok()
                                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                                .header("Cache-Control", "no-store")
                                .body(playlist);
        }

        private ResponseEntity<Map<String, Object>> buildBatchPlaylistResponse(UUID batchId) {
                Batch batch = batchRepository.findById(batchId)
                                .orElseThrow(() -> new ResourceNotFoundException("Batch", batchId));
                String syncUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                                .path("/v1/playlists/batch/{batchId}")
                                .buildAndExpand(batchId)
                                .toUriString();

                Map<String, Object> playlist = playlistService.generateBatchPlaylist(batch, syncUrl);

                String filename = "accsaber-reloaded-"
                                + batch.getName().toLowerCase().replace(" ", "-").replace("_", "-")
                                + ".bplist";

                return ResponseEntity.ok()
                                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                                .body(playlist);
        }

        private ResponseEntity<Map<String, Object>> buildSnipePlaylistResponse(SnipeQuery query, int size) {
                Optional<String> categoryParam = Optional.ofNullable(query.categoryCode()).filter(c -> !c.isBlank());
                UriComponentsBuilder syncBuilder = ServletUriComponentsBuilder.fromCurrentContextPath()
                                .path(categoryParam.isPresent()
                                                ? "/v1/playlists/snipe/{sniperId}/{targetId}/{size}/{category}"
                                                : "/v1/playlists/snipe/{sniperId}/{targetId}/{size}");
                if (!query.isDefaultOrder()) {
                        syncBuilder.queryParam("sort", query.sort()).queryParam("direction", query.direction());
                }
                if (!query.unplayed().isDefault()) {
                        syncBuilder.queryParam("unplayed", query.unplayed());
                }
                String syncUrl = categoryParam
                                .map(c -> syncBuilder.buildAndExpand(query.sniperId(), query.targetId(), size, c))
                                .orElseGet(() -> syncBuilder.buildAndExpand(query.sniperId(), query.targetId(), size))
                                .toUriString();
                SnipeSelection selection = snipeService.findSnipeDifficulties(query, size);
                Map<String, Object> playlist = playlistService.generateSnipePlaylist(selection, query, syncUrl);

                String filenameSuffix = categoryParam.map(c -> "-" + c.replace("_", "-")).orElse("")
                                + (query.isDefaultOrder() ? "" : "-" + query.orderSlug())
                                + (query.unplayed().isDefault() ? "" : "-" + query.unplayed().getSlug());
                String filename = "accsaber-snipe-" + query.sniperId() + "-" + query.targetId() + filenameSuffix
                                + ".bplist";

                return ResponseEntity.ok()
                                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                                .header("Cache-Control", "no-store")
                                .body(playlist);
        }

        private ResponseEntity<Map<String, Object>> buildMissingPlaylistResponse(String category, Long userId) {
                String syncUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                                .path("/v1/playlists/missing/{userId}/{category}")
                                .buildAndExpand(userId, category)
                                .toUriString();
                Map<String, Object> playlist = playlistService.generateMissingPlaylist(userId, category, syncUrl);

                String filename = "accsaber-reloaded-missing-" + userId + "-"
                                + category.replace("_", "-") + ".bplist";

                return ResponseEntity.ok()
                                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                                .header("Cache-Control", "public, max-age=300")
                                .body(playlist);
        }

        private ResponseEntity<Map<String, Object>> buildPlaylistResponse(String category) {
                String syncUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                                .path("/v1/playlists/{category}")
                                .buildAndExpand(category)
                                .toUriString();
                Map<String, Object> playlist = playlistService.generatePlaylist(category, syncUrl);

                String filename = "accsaber-reloaded-" + category.replace("_", "-") + ".bplist";

                return ResponseEntity.ok()
                                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                                .body(playlist);
        }

        private ResponseEntity<Map<String, Object>> buildUnrankedPlaylistResponse(String category) {
                String syncUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                                .path("/v1/playlists/unranked/{category}")
                                .buildAndExpand(category)
                                .toUriString();
                Map<String, Object> playlist = playlistService.generateUnrankedPlaylist(category, syncUrl);

                String filename = "accsaber-reloaded-unranked-" + category.replace("_", "-") + ".bplist";

                return ResponseEntity.ok()
                                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                                .body(playlist);
        }
}
