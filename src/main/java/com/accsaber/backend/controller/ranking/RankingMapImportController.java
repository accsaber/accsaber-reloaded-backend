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
@Tag(name = "Ranking - Map Import")
public class RankingMapImportController {

        private final MapImportService mapImportService;
        private final MapService mapService;

        @Operation(summary = "Import a map difficulty (Queue)", description = "Auto-fetches metadata from BeatLeader and BeatSaver, then creates the map difficulty in queue status")
        @PostMapping("/import")
        public ResponseEntity<MapDifficultyResponse> importMapDifficulty(
                        @Valid @RequestBody ImportMapFromLeaderboardIdsRequest request,
                        Authentication authentication) {
                MapDifficultyResponse response = mapImportService.importByLeaderboardIds(
                                request, StaffPrincipals.staffIdOf(authentication), MapDifficultyStatus.QUEUE);
                return ResponseEntity.created(URI.create("/v1/maps/difficulties/" + response.getId()))
                                .body(response);
        }

        @Operation(summary = "Manual import a map difficulty", description = "Import with all fields provided manually (fallback when external APIs are unavailable)")
        @PostMapping("/import/manual")
        public ResponseEntity<MapDifficultyResponse> importMapDifficultyManual(
                        @Valid @RequestBody CreateMapDifficultyRequest request,
                        Authentication authentication) {
                MapDifficultyResponse response = mapService.importMapDifficulty(request,
                                StaffPrincipals.staffIdOf(authentication));
                return ResponseEntity.created(URI.create("/v1/maps/difficulties/" + response.getId()))
                                .body(response);
        }
}
