package com.accsaber.backend.controller.playlist;

import java.util.Map;
import java.util.Optional;

import java.util.UUID;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.entity.map.Batch;
import com.accsaber.backend.repository.map.BatchRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.accsaber.backend.service.playlist.PlaylistService;

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
        private final BatchRepository batchRepository;

        @Deprecated
        @Operation(summary = "Download category playlist (query param)", deprecated = true, description = "Returns a Beat Saber playlist JSON file containing all ranked maps for the specified category. "
                        + "The syncURL field allows mod managers to auto-refresh the playlist. "
                        + "Prefer the /{category} path variant for standalone Beat Saber compatibility.")
        @GetMapping(produces = "application/json")
        public ResponseEntity<Map<String, Object>> getPlaylist(
                        @Parameter(description = "Category code (e.g. true_acc, standard_acc, tech_acc)") @RequestParam String category) {
                return buildPlaylistResponse(category);
        }

        @Operation(summary = "Download category playlist", description = "Returns a Beat Saber playlist JSON file containing all ranked maps for the specified category. "
                        + "The syncURL field allows mod managers to auto-refresh the playlist. "
                        + "This path-based variant is compatible with standalone Beat Saber.")
        @GetMapping(value = "/{category}", produces = "application/json")
        public ResponseEntity<Map<String, Object>> getPlaylistByPath(
                        @Parameter(description = "Category code (e.g. true_acc, standard_acc, tech_acc)") @PathVariable String category) {
                return buildPlaylistResponse(category);
        }

        @Operation(summary = "Download category unranked playlist", description = "Returns a Beat Saber playlist JSON file containing all queued and qualified maps for the specified category. "
                        + "The syncURL field allows mod managers to auto-refresh the playlist. "
                        + "This path-based variant is compatible with standalone Beat Saber.")
        @GetMapping(value = "/unranked/{category}", produces = "application/json")
        public ResponseEntity<Map<String, Object>> getUnrankedPlaylistByPath(
                        @Parameter(description = "Category code (e.g. true_acc, standard_acc, tech_acc)") @PathVariable String category) {
                return buildUnrankedPlaylistResponse(category);
        }

        @Operation(summary = "Download snipe playlist (every snipable map, all categories)", description = "Returns a Beat Saber playlist JSON file containing every map where the target player outscores the sniper, ordered by closest accuracy gap first. "
                        + "Playlist image is the target player's avatar. Path-only URL so the syncURL works with standalone Beat Saber.")
        @GetMapping(value = "/snipe/{sniperId}/{targetId}", produces = "application/json")
        public ResponseEntity<Map<String, Object>> getSnipePlaylist(
                        @Parameter(description = "Steam ID of the sniping player") @PathVariable Long sniperId,
                        @Parameter(description = "Steam ID of the target player") @PathVariable Long targetId) {
                return buildSnipePlaylistResponse(sniperId, targetId, 0, null);
        }

        @Operation(summary = "Download snipe playlist (custom size)", description = "Same as the base snipe playlist but capped at the requested map count (1+). Pass 0 for unlimited. Path-only URL, standalone-compatible.")
        @GetMapping(value = "/snipe/{sniperId}/{targetId}/{size}", produces = "application/json")
        public ResponseEntity<Map<String, Object>> getSnipePlaylistBySize(
                        @Parameter(description = "Steam ID of the sniping player") @PathVariable Long sniperId,
                        @Parameter(description = "Steam ID of the target player") @PathVariable Long targetId,
                        @Parameter(description = "Map count cap (0 = unlimited)") @PathVariable int size) {
                return buildSnipePlaylistResponse(sniperId, targetId, size, null);
        }

        @Operation(summary = "Download snipe playlist (custom size + category)", description = "Snipe playlist filtered to a single category (e.g. true_acc, standard_acc, tech_acc, overall). Pass size=0 for unlimited. Path-only URL, standalone-compatible.")
        @GetMapping(value = "/snipe/{sniperId}/{targetId}/{size}/{category}", produces = "application/json")
        public ResponseEntity<Map<String, Object>> getSnipePlaylistBySizeAndCategory(
                        @Parameter(description = "Steam ID of the sniping player") @PathVariable Long sniperId,
                        @Parameter(description = "Steam ID of the target player") @PathVariable Long targetId,
                        @Parameter(description = "Map count cap (0 = unlimited)") @PathVariable int size,
                        @Parameter(description = "Category code") @PathVariable String category) {
                return buildSnipePlaylistResponse(sniperId, targetId, size, category);
        }

        @Operation(summary = "Download batch release playlist", description = "Returns a Beat Saber playlist JSON file containing all maps of the specified batch release.")
        @GetMapping(value = "/batch/{batchId}", produces = "application/json")
        public ResponseEntity<Map<String, Object>> getBatchPlaylist(
                        @Parameter(description = "ID of batch") @PathVariable UUID batchId) {
                return buildBatchPlaylistResponse(batchId);
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

        private ResponseEntity<Map<String, Object>> buildSnipePlaylistResponse(Long sniperId, Long targetId,
                        int size, String category) {
                Optional<String> categoryParam = Optional.ofNullable(category).filter(c -> !c.isBlank());
                String syncUrl = categoryParam
                                .map(c -> ServletUriComponentsBuilder.fromCurrentContextPath()
                                                .path("/v1/playlists/snipe/{sniperId}/{targetId}/{size}/{category}")
                                                .buildAndExpand(sniperId, targetId, size, c)
                                                .toUriString())
                                .orElseGet(() -> ServletUriComponentsBuilder.fromCurrentContextPath()
                                                .path("/v1/playlists/snipe/{sniperId}/{targetId}/{size}")
                                                .buildAndExpand(sniperId, targetId, size)
                                                .toUriString());
                Map<String, Object> playlist = playlistService.generateSnipePlaylist(sniperId, targetId, category, size,
                                syncUrl);

                String filenameSuffix = categoryParam.map(c -> "-" + c.replace("_", "-")).orElse("");
                String filename = "accsaber-snipe-" + sniperId + "-" + targetId + filenameSuffix + ".bplist";

                return ResponseEntity.ok()
                                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                                .header("Cache-Control", "no-store")
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
