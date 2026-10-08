package com.accsaber.backend.controller.ranking;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.map.CreateMapDifficultyRequest;
import com.accsaber.backend.model.dto.request.map.ImportMapFromLeaderboardIdsRequest;
import com.accsaber.backend.model.dto.response.map.MapDifficultyResponse;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.security.StaffPrincipals;
import com.accsaber.backend.service.map.MapImportService;
import com.accsaber.backend.service.map.MapService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/ranking/maps")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RANKING')")
@Tag(name = "Ranking")
public class RankingMapImportController {

        private final MapImportService mapImportService;
        private final MapService mapService;

        @Operation(summary = "Import a difficulty to queue", description = "Pulls metadata from BeatLeader and "
                + "BeatSaver.")
        @PostMapping("/import")
        public ResponseEntity<MapDifficultyResponse> importMapDifficulty(
                        @Valid @RequestBody ImportMapFromLeaderboardIdsRequest request,
                        Authentication authentication) {
                MapDifficultyResponse response = mapImportService.importByLeaderboardIds(
                                request, StaffPrincipals.staffIdOf(authentication), MapDifficultyStatus.QUEUE);
                return ResponseEntity.created(URI.create("/v1/maps/difficulties/" + response.getId()))
                                .body(response);
        }

        @Operation(summary = "Import a difficulty by hand", description = "You fill in every field. Use when the "
                + "external APIs are down.")
        @PostMapping("/import/manual")
        public ResponseEntity<MapDifficultyResponse> importMapDifficultyManual(
                        @Valid @RequestBody CreateMapDifficultyRequest request,
                        Authentication authentication) {
                MapDifficultyResponse response = mapService.importMapDifficulty(request,
                                StaffPrincipals.staffIdOf(authentication));
                return ResponseEntity.created(URI.create("/v1/maps/difficulties/" + response.getId()))
                                .body(response);
        }

        @Operation(summary = "Backfill map metadata", description = "Fills in BPM, notes, bombs, walls and duration "
                + "from BeatSaver where missing.")
        @PostMapping("/backfill-metadata")
        public ResponseEntity<Void> backfillMetadata() {
                mapImportService.backfillMetadata();
                return ResponseEntity.accepted().build();
        }
}
