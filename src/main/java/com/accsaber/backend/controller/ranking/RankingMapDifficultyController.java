package com.accsaber.backend.controller.ranking;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.map.ApproveReweightRequest;
import com.accsaber.backend.model.dto.request.map.ApproveUnrankRequest;
import com.accsaber.backend.model.dto.request.map.BulkReweightRequest;
import com.accsaber.backend.model.dto.request.map.BulkUnrankRequest;
import com.accsaber.backend.model.dto.request.map.LinkLeaderboardAliasRequest;
import com.accsaber.backend.model.dto.request.map.RefreshMapDifficultyRequest;
import com.accsaber.backend.model.dto.request.map.UpdateMapCategoryRequest;
import com.accsaber.backend.model.dto.request.map.UpdateMapComplexityRequest;
import com.accsaber.backend.model.dto.request.map.UpdateMapStatusRequest;
import com.accsaber.backend.model.dto.response.map.AutoCriteriaCheckResponse;
import com.accsaber.backend.model.dto.response.map.LeaderboardAliasResponse;
import com.accsaber.backend.model.dto.response.map.MapDifficultyResponse;
import com.accsaber.backend.security.StaffPrincipals;
import com.accsaber.backend.service.map.AutoCriteriaService;
import com.accsaber.backend.service.map.MapDifficultyLeaderboardAliasService;
import com.accsaber.backend.service.map.MapImportService;
import com.accsaber.backend.service.map.MapService;
import com.accsaber.backend.service.map.ReweightService;
import com.accsaber.backend.service.map.UnrankService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/ranking/maps/difficulties")
@RequiredArgsConstructor
@Tag(name = "Ranking")
public class RankingMapDifficultyController {

        private final MapService mapService;
        private final MapImportService mapImportService;
        private final ReweightService reweightService;
        private final UnrankService unrankService;
        private final AutoCriteriaService autoCriteriaService;
        private final MapDifficultyLeaderboardAliasService leaderboardAliasService;

        @Operation(summary = "Set difficulty status", description = "ranking_head or admin only.")
        @PatchMapping("/{difficultyId}/status")
        @PreAuthorize("hasRole('RANKING_HEAD')")
        public ResponseEntity<MapDifficultyResponse> updateStatus(
                        @PathVariable UUID difficultyId,
                        @Valid @RequestBody UpdateMapStatusRequest request,
                        Authentication authentication) {
                return ResponseEntity.ok(mapService.updateStatus(difficultyId, request,
                                StaffPrincipals.staffIdOf(authentication)));
        }

        @Operation(summary = "Move to another category", description = "QUEUE or QUALIFIED only.")
        @PatchMapping("/{difficultyId}/category")
        @PreAuthorize("hasRole('RANKING_HEAD')")
        public ResponseEntity<MapDifficultyResponse> updateCategory(
                        @PathVariable UUID difficultyId,
                        @Valid @RequestBody UpdateMapCategoryRequest request,
                        Authentication authentication) {
                return ResponseEntity.ok(mapService.updateCategory(difficultyId, request.getCategoryId(),
                                StaffPrincipals.staffIdOf(authentication)));
        }

        @Operation(summary = "Set complexity by hand", description = "On a ranked map this is a reweight "
                + "and everything gets adjusted in the background. Either way the map gets pinned.")
        @PostMapping("/{difficultyId}/complexity")
        @PreAuthorize("hasRole('RANKING_HEAD')")
        public ResponseEntity<MapDifficultyResponse> updateComplexity(
                        @PathVariable UUID difficultyId,
                        @Valid @RequestBody UpdateMapComplexityRequest request,
                        Authentication authentication) {
                return ResponseEntity.ok(reweightService.setComplexityByHand(difficultyId, request.getComplexity(),
                                request.getReason(),
                                StaffPrincipals.linkedUserIdOf(authentication),
                                StaffPrincipals.staffIdOf(authentication)));
        }

        @Operation(summary = "Pin a complexity", description = "The complexity script leaves "
                + "pinned maps alone. Setting a complexity by hand pins it already.")
        @PatchMapping("/{difficultyId}/complexity-pin")
        @PreAuthorize("hasRole('RANKING_HEAD')")
        public ResponseEntity<MapDifficultyResponse> pinComplexity(
                        @PathVariable UUID difficultyId,
                        @RequestParam boolean pinned,
                        Authentication authentication) {
                return ResponseEntity.ok(mapService.setComplexityPinned(difficultyId, pinned,
                                StaffPrincipals.staffIdOf(authentication)));
        }

        @Operation(summary = "Refresh from BeatLeader and BeatSaver", description = "Updates "
                + "leaderboard IDs, max score and map info. QUEUE or QUALIFIED only.")
        @PostMapping("/{difficultyId}/refresh")
        @PreAuthorize("hasRole('RANKING_HEAD')")
        public ResponseEntity<MapDifficultyResponse> refresh(
                        @PathVariable UUID difficultyId,
                        @Valid @RequestBody RefreshMapDifficultyRequest request,
                        Authentication authentication) {
                return ResponseEntity.ok(mapImportService.refreshMapDifficulty(difficultyId, request,
                                StaffPrincipals.staffIdOf(authentication)));
        }

        @Operation(summary = "Deactivate a difficulty", description = "ranking_head or admin only.")
        @PatchMapping("/{difficultyId}/deactivate")
        @PreAuthorize("hasRole('RANKING_HEAD')")
        public ResponseEntity<Void> deactivate(
                        @PathVariable UUID difficultyId,
                        Authentication authentication) {
                mapService.deactivate(difficultyId, StaffPrincipals.staffIdOf(authentication));
                return ResponseEntity.noContent().build();
        }

        @Operation(summary = "Apply a reweight", description = "RANKED only. Scores get adjusted in the "
                + "background.")
        @PostMapping("/{difficultyId}/reweight")
        @PreAuthorize("hasRole('RANKING_HEAD')")
        public ResponseEntity<MapDifficultyResponse> reweight(
                        @PathVariable UUID difficultyId,
                        @Valid @RequestBody ApproveReweightRequest request,
                        Authentication authentication) {
                return ResponseEntity.ok(reweightService.reweight(difficultyId, request.getComplexity(),
                                request.getReason(),
                                StaffPrincipals.linkedUserIdOf(authentication),
                                StaffPrincipals.staffIdOf(authentication)));
        }

        @Operation(summary = "Apply an unrank", description = "Moves a RANKED difficulty back to QUEUE.")
        @PostMapping("/{difficultyId}/unrank")
        @PreAuthorize("hasRole('RANKING_HEAD')")
        public ResponseEntity<MapDifficultyResponse> unrank(
                        @PathVariable UUID difficultyId,
                        @Valid @RequestBody ApproveUnrankRequest request,
                        Authentication authentication) {
                return ResponseEntity.ok(unrankService.unrank(difficultyId, request.getReason(),
                                StaffPrincipals.staffIdOf(authentication)));
        }

        @Operation(summary = "Bulk unrank")
        @PostMapping("/bulk-unrank")
        @PreAuthorize("hasRole('RANKING_HEAD')")
        public ResponseEntity<List<MapDifficultyResponse>> bulkUnrank(
                        @Valid @RequestBody BulkUnrankRequest request,
                        Authentication authentication) {
                return ResponseEntity.ok(unrankService.unrankBatch(request.getItems(),
                                StaffPrincipals.staffIdOf(authentication)));
        }

        @Operation(summary = "Bulk reweight", description = "RANKED only, one shared reason. Scores get adjusted in "
                + "the background.")
        @PostMapping("/bulk-reweight")
        @PreAuthorize("hasRole('RANKING_HEAD')")
        public ResponseEntity<Void> bulkReweight(
                        @Valid @RequestBody BulkReweightRequest request,
                        Authentication authentication) {
                reweightService.bulkReweight(request.getItems(), request.getReason(),
                                StaffPrincipals.linkedUserIdOf(authentication),
                                StaffPrincipals.staffIdOf(authentication));
                return ResponseEntity.accepted().build();
        }

        @Operation(summary = "Recalculate a difficulty's scores", description = "Uses the current complexity. "
                + "Skips when AP does not change.")
        @PostMapping("/{difficultyId}/recalculate")
        @PreAuthorize("hasRole('RANKING_HEAD')")
        public ResponseEntity<Void> recalculate(@PathVariable UUID difficultyId) {
                reweightService.recalculateDifficulty(difficultyId);
                return ResponseEntity.accepted().build();
        }

        @Operation(summary = "Run auto criteria", description = "Gives pass or fail with details, or "
                + "UNAVAILABLE if the checker could not read the map.")
        @PostMapping("/{difficultyId}/auto-criteria-check")
        @PreAuthorize("hasRole('RANKING')")
        public ResponseEntity<AutoCriteriaCheckResponse> runAutoCriteriaCheck(@PathVariable UUID difficultyId) {
                return ResponseEntity.ok(autoCriteriaService.runCheck(difficultyId));
        }

        @Operation(summary = "Leaderboard aliases", description = "Alternate BeatLeader or ScoreSaber uploads "
                + "that count toward this difficulty.")
        @GetMapping("/{difficultyId}/leaderboard-aliases")
        @PreAuthorize("hasRole('RANKING')")
        public ResponseEntity<List<LeaderboardAliasResponse>> listLeaderboardAliases(@PathVariable UUID difficultyId) {
                return ResponseEntity.ok(leaderboardAliasService.list(difficultyId));
        }

        @Operation(summary = "Add a leaderboard alias", description = "Needs a note identical "
                + "map with the same max score. Its existing scores get backfilled once.")
        @PostMapping("/{difficultyId}/leaderboard-aliases")
        @PreAuthorize("hasRole('RANKING_HEAD')")
        public ResponseEntity<List<LeaderboardAliasResponse>> linkLeaderboardAlias(
                        @PathVariable UUID difficultyId,
                        @Valid @RequestBody LinkLeaderboardAliasRequest request,
                        Authentication authentication) {
                return ResponseEntity.ok(leaderboardAliasService.linkAlias(difficultyId, request,
                                StaffPrincipals.staffIdOf(authentication)));
        }

        @Operation(summary = "Remove a leaderboard alias", description = "Imported scores stay. New scores from that "
                + "leaderboard stop counting.")
        @DeleteMapping("/{difficultyId}/leaderboard-aliases/{aliasId}")
        @PreAuthorize("hasRole('RANKING_HEAD')")
        public ResponseEntity<Void> unlinkLeaderboardAlias(
                        @PathVariable UUID difficultyId,
                        @PathVariable UUID aliasId) {
                leaderboardAliasService.unlinkAlias(difficultyId, aliasId);
                return ResponseEntity.noContent().build();
        }
}
