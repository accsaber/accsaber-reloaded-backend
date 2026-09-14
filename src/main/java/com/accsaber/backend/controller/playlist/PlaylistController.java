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

        @Operation(summary = "Download the playlist for a category", description = "Every ranked map in a category as a Beat "
                        + "Saber playlist file. Drop it in your playlists folder or hand the URL to a mod manager, and the "
                        + "syncURL inside it means it will keep itself up to date as more maps get ranked.")
        @GetMapping(value = "/{category}", produces = "application/json")
        public ResponseEntity<Map<String, Object>> getPlaylistByPath(
                        @Parameter(description = "Category code (e.g. true_acc, standard_acc, tech_acc)") @PathVariable String category) {
                return buildPlaylistResponse(category);
        }

        @Operation(summary = "Download the maps a player is missing", description = "The same idea as the category playlist but "
                        + "only the ranked maps the player has not scored on yet, so it shrinks as they work through it. Pass "
                        + "overall as the category if you want every category rather than just one.")
        @GetMapping(value = "/missing/{userId}/{category}", produces = "application/json")
        public ResponseEntity<Map<String, Object>> getMissingPlaylistByPath(
                        @Parameter(description = "User ID of the player") @PathVariable Long userId,
                        @Parameter(description = "Category code (e.g. true_acc, standard_acc, tech_acc, overall)") @PathVariable String category) {
                return buildMissingPlaylistResponse(category, userId);
        }

        @Operation(summary = "Download the queued and qualified maps", description = "Everything currently sitting in the queue "
                        + "or qualified for a category, so the maps on their way to being ranked but not there yet. Worth "
                        + "having if you like playing them before they start counting.")
        @GetMapping(value = "/unranked/{category}", produces = "application/json")
        public ResponseEntity<Map<String, Object>> getUnrankedPlaylistByPath(
                        @Parameter(description = "Category code (e.g. true_acc, standard_acc, tech_acc)") @PathVariable String category) {
                return buildUnrankedPlaylistResponse(category);
        }

        @Operation(summary = "Download a snipe playlist", description = "Every map where the target player is ahead of you, "
                        + "closest gap first, so the ones you have the best shot at taking back come up early. Pass sort if "
                        + "you would rather order it by the AP going spare, by either player's AP or by the leaderboard gap, "
                        + "and direction to flip any of those around. The playlist picture is the target's avatar. This form "
                        + "gives you all of them across every category.")
        @GetMapping(value = "/snipe/{sniperId}/{targetId}", produces = "application/json")
        public ResponseEntity<Map<String, Object>> getSnipePlaylist(
                        @Parameter(description = "User ID of the sniping player") @PathVariable Long sniperId,
                        @Parameter(description = "User ID of the target player") @PathVariable Long targetId,
                        @Parameter(description = "GAP, AP_GAP, TARGET_AP, YOUR_AP or RANK_GAP") @RequestParam(defaultValue = "GAP") SnipeSort sort,
                        @Parameter(description = "ASC or DESC; each sort has its own sensible default") @RequestParam(required = false) Sort.Direction direction,
                        @Parameter(description = "EXCLUDE (only maps you have played), INCLUDE (add the ones you have not) or ONLY (just those)") @RequestParam(required = false) SnipeUnplayed unplayed) {
                return buildSnipePlaylistResponse(new SnipeQuery(sniperId, targetId, null, sort, direction, unplayed), 0);
        }

        @Operation(summary = "Download a snipe playlist with a size cap", description = "The same snipe playlist but stopping "
                        + "after however many maps you ask for, which keeps it manageable when the target is a long way ahead "
                        + "of you. Pass 0 if you actually want all of them.")
        @GetMapping(value = "/snipe/{sniperId}/{targetId}/{size}", produces = "application/json")
        public ResponseEntity<Map<String, Object>> getSnipePlaylistBySize(
                        @Parameter(description = "User ID of the sniping player") @PathVariable Long sniperId,
                        @Parameter(description = "User ID of the target player") @PathVariable Long targetId,
                        @Parameter(description = "Map count cap (0 = unlimited)") @PathVariable int size,
                        @Parameter(description = "GAP, AP_GAP, TARGET_AP, YOUR_AP or RANK_GAP") @RequestParam(defaultValue = "GAP") SnipeSort sort,
                        @Parameter(description = "ASC or DESC; each sort has its own sensible default") @RequestParam(required = false) Sort.Direction direction,
                        @Parameter(description = "EXCLUDE (only maps you have played), INCLUDE (add the ones you have not) or ONLY (just those)") @RequestParam(required = false) SnipeUnplayed unplayed) {
                return buildSnipePlaylistResponse(new SnipeQuery(sniperId, targetId, null, sort, direction, unplayed), size);
        }

        @Operation(summary = "Download a snipe playlist for one category", description = "A snipe playlist narrowed to a single "
                        + "category, still capped by size. Pass 0 for size to lift the cap, and overall as the category if you "
                        + "wanted every category after all.")
        @GetMapping(value = "/snipe/{sniperId}/{targetId}/{size}/{category}", produces = "application/json")
        public ResponseEntity<Map<String, Object>> getSnipePlaylistBySizeAndCategory(
                        @Parameter(description = "User ID of the sniping player") @PathVariable Long sniperId,
                        @Parameter(description = "User ID of the target player") @PathVariable Long targetId,
                        @Parameter(description = "Map count cap (0 = unlimited)") @PathVariable int size,
                        @Parameter(description = "Category code") @PathVariable String category,
                        @Parameter(description = "GAP, AP_GAP, TARGET_AP, YOUR_AP or RANK_GAP") @RequestParam(defaultValue = "GAP") SnipeSort sort,
                        @Parameter(description = "ASC or DESC; each sort has its own sensible default") @RequestParam(required = false) Sort.Direction direction,
                        @Parameter(description = "EXCLUDE (only maps you have played), INCLUDE (add the ones you have not) or ONLY (just those)") @RequestParam(required = false) SnipeUnplayed unplayed) {
                return buildSnipePlaylistResponse(new SnipeQuery(sniperId, targetId, category, sort, direction, unplayed),
                                size);
        }

        @Operation(summary = "Download a player's own scores as a playlist", description = "The maps behind a slice of a "
                        + "player's score list, taking the same category, search, sorting and paging as the scores route "
                        + "itself. Whatever the list is showing is what you get, so sort by accuracy ascending with a size of "
                        + "25 and you have a playlist of the 25 scores you should go back and clean up, or sort by when the "
                        + "score was set and you have the ones you have not touched in years.")
        @GetMapping(value = "/scores/{userId}", produces = "application/json")
        public ResponseEntity<Map<String, Object>> getUserScoresPlaylist(
                        @Parameter(description = "User ID of the player") @PathVariable Long userId,
                        @Parameter(description = "Category UUID or code to narrow the scores to") @RequestParam(required = false) String categoryId,
                        @Parameter(description = "Song name search") @RequestParam(required = false) String search,
                        @PageableDefault(size = 25, sort = "ap", direction = Sort.Direction.DESC) Pageable pageable) {
                return buildUserScoresPlaylistResponse(userId, categoryId, search, pageable);
        }

        @Operation(summary = "Download a batch release as a playlist", description = "All the maps that went ranked together in "
                        + "one batch. Handy right after a release when you want to play through the new set.")
        @GetMapping(value = "/batch/{batchId}", produces = "application/json")
        public ResponseEntity<Map<String, Object>> getBatchPlaylist(
                        @Parameter(description = "ID of batch") @PathVariable UUID batchId) {
                return buildBatchPlaylistResponse(batchId);
        }

        @Operation(summary = "Download a clan war pool as a playlist", description = "Every map in a war's pool, live "
                        + "once the pool locks so both clans can practise it before the fighting starts.")
        @GetMapping(value = "/clan-war/{warId}", produces = "application/json")
        public ResponseEntity<Map<String, Object>> getClanWarPlaylist(
                        @Parameter(description = "ID of the war") @PathVariable UUID warId) {
                ClanWarPoolService.PlaylistSource source = clanWarPoolService.playlistSource(warId);
                String syncUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                                .path("/v1/playlists/clan-war/{warId}")
                                .buildAndExpand(warId)
                                .toUriString();
                return ResponseEntity.ok(playlistService.generateClanWarPlaylist(source, syncUrl));
        }

        @Operation(summary = "Download a campaign as a playlist", description = "Every map used in a campaign, so you can grab "
                        + "the lot up front rather than downloading each one as you reach it. The person who made the campaign "
                        + "has to have turned playlist export on, and you get a 422 back if they have not.")
        @GetMapping(value = "/campaign/{campaignId}", produces = "application/json")
        public ResponseEntity<Map<String, Object>> getCampaignPlaylist(
                        @Parameter(description = "ID of campaign") @PathVariable UUID campaignId) {
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
