package com.accsaber.backend.controller.playlist;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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

        @Operation(summary = "Download category playlist", description = "Returns a Beat Saber playlist JSON file containing all ranked maps for the specified category. "
                        + "The syncURL field allows mod managers to auto-refresh the playlist.")
        @GetMapping(produces = "application/json")
        public ResponseEntity<Map<String, Object>> getPlaylist(
                        @Parameter(description = "Category code (e.g. true_acc, standard_acc, tech_acc)") @RequestParam String category) {

                String syncUrl = ServletUriComponentsBuilder.fromCurrentRequest().toUriString();
                Map<String, Object> playlist = playlistService.generatePlaylist(category, syncUrl);

                String filename = "accsaber-reloaded-" + category.replace("_", "-") + ".json";

                return ResponseEntity.ok()
                                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                                .body(playlist);
        }
}
