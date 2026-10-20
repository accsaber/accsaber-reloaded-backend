package com.accsaber.backend.controller.playlist;

import java.util.Map;
import java.util.Optional;

import java.util.UUID;

import com.accsaber.backend.model.entity.score.SnipeSort;
import com.accsaber.backend.model.entity.score.SnipeUnplayed;
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
import com.accsaber.backend.service.playlist.PlaylistService.PlaylistFile;
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

        private static final String NO_STORE = "no-store";

        private final PlaylistService playlistService;
        private final CategoryService categoryService;
        private final SnipeService snipeService;
        private final ClanWarPoolService clanWarPoolService;

        @Operation(summary = "Category playlist", description = "Keeps itself up to date through "
                + "its syncURL.")
        @GetMapping(value = "/{category}", produces = "application/json")
        public ResponseEntity<Map<String, Object>> getPlaylistByPath(
                        @Parameter(description = "Category code, like true_acc") @PathVariable String category) {
                return bplist(playlistService.generatePlaylist(category, syncUrl("/v1/playlists/{category}", category)),
                                "accsaber-reloaded-" + category.replace("_", "-") + ".bplist", null);
        }

        @Operation(summary = "Unplayed ranked maps", description = "Pass overall for every category.")
        @GetMapping(value = "/missing/{userId}/{category}", produces = "application/json")
        public ResponseEntity<Map<String, Object>> getMissingPlaylistByPath(
                        @Parameter(description = "Player user ID") @PathVariable Long userId,
                        @Parameter(description = "Category code or overall") @PathVariable String category) {
                return bplist(playlistService.generateMissingPlaylist(userId, category,
                                syncUrl("/v1/playlists/missing/{userId}/{category}", userId, category)),
                                "accsaber-reloaded-missing-" + userId + "-" + category.replace("_", "-") + ".bplist",
                                "public, max-age=300");
        }

        @Operation(summary = "Queue and qualified maps")
        @GetMapping(value = "/unranked/{category}", produces = "application/json")
        public ResponseEntity<Map<String, Object>> getUnrankedPlaylistByPath(
                        @Parameter(description = "Category code, like true_acc") @PathVariable String category) {
                return bplist(playlistService.generateUnrankedPlaylist(category,
                                syncUrl("/v1/playlists/unranked/{category}", category)),
                                "accsaber-reloaded-unranked-" + category.replace("_", "-") + ".bplist", null);
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
                String syncUrl = ServletUriComponentsBuilder.fromCurrentRequest().toUriString();
                return bplist(playlistService.generateUserScoresPlaylist(
                                userId, categoryService.resolveId(categoryId), search, pageable, syncUrl),
                                "accsaber-scores-" + userId + ".bplist", NO_STORE);
        }

        @Operation(summary = "Batch playlist")
        @GetMapping(value = "/batch/{batchId}", produces = "application/json")
        public ResponseEntity<Map<String, Object>> getBatchPlaylist(
                        @Parameter(description = "Batch ID") @PathVariable UUID batchId) {
                return bplist(playlistService.generateBatchPlaylist(batchId,
                                syncUrl("/v1/playlists/batch/{batchId}", batchId)), null);
        }

        @Operation(summary = "Clan war pool playlist", description = "Live once the pool locks.")
        @GetMapping(value = "/clan-war/{warId}", produces = "application/json")
        public ResponseEntity<Map<String, Object>> getClanWarPlaylist(
                        @Parameter(description = "War ID") @PathVariable UUID warId) {
                return bplist(playlistService.generateClanWarPlaylist(clanWarPoolService.playlistSource(warId),
                                syncUrl("/v1/playlists/clan-war/{warId}", warId)), null);
        }

        @Operation(summary = "Campaign playlist", description = "The creator has to turn on playlist "
                + "export. You get a 422 if they did not.")
        @GetMapping(value = "/campaign/{campaignId}", produces = "application/json")
        public ResponseEntity<Map<String, Object>> getCampaignPlaylist(
                        @Parameter(description = "Campaign ID") @PathVariable UUID campaignId) {
                return bplist(playlistService.generateCampaignPlaylist(campaignId,
                                syncUrl("/v1/playlists/campaign/{campaignId}", campaignId)), NO_STORE);
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
                return bplist(playlist, "accsaber-snipe-" + query.sniperId() + "-" + query.targetId() + filenameSuffix
                                + ".bplist", NO_STORE);
        }

        private static String syncUrl(String path, Object... uriVariables) {
                return ServletUriComponentsBuilder.fromCurrentContextPath()
                                .path(path)
                                .buildAndExpand(uriVariables)
                                .toUriString();
        }

        private static ResponseEntity<Map<String, Object>> bplist(PlaylistFile file, String cacheControl) {
                return bplist(file.playlist(), file.filename(), cacheControl);
        }

        private static ResponseEntity<Map<String, Object>> bplist(Map<String, Object> body, String filename,
                        String cacheControl) {
                ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"");
                if (cacheControl != null) {
                        response.header("Cache-Control", cacheControl);
                }
                return response.body(body);
        }
}
