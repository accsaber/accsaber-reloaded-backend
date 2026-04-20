package com.accsaber.backend.controller.playlist;

import java.util.Map;

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
