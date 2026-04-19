package com.accsaber.backend.controller.ranking;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.map.ApproveReweightRequest;
import com.accsaber.backend.model.dto.request.map.ApproveUnrankRequest;
import com.accsaber.backend.model.dto.request.map.BulkReweightRequest;
import com.accsaber.backend.model.dto.request.map.BulkUnrankRequest;
import com.accsaber.backend.model.dto.request.map.CriteriaWebhookRequest;
import com.accsaber.backend.model.dto.request.map.UpdateMapCategoryRequest;
import com.accsaber.backend.model.dto.request.map.UpdateMapComplexityRequest;
import com.accsaber.backend.model.dto.request.map.UpdateMapStatusRequest;
import com.accsaber.backend.model.dto.response.map.MapDifficultyResponse;
import com.accsaber.backend.security.StaffUserDetails;
import com.accsaber.backend.service.map.CriteriaService;
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
@Tag(name = "Ranking - Map Difficulty Management")
public class RankingMapDifficultyController {

        private final MapService mapService;
        private final ReweightService reweightService;
        private final UnrankService unrankService;
        private final CriteriaService criteriaService;

        @Operation(summary = "Update difficulty status", description = "Manually transition a map difficulty status (ranking_head/admin only)")
        @PatchMapping("/{difficultyId}/status")
        @PreAuthorize("hasRole('RANKING_HEAD')")
        public ResponseEntity<MapDifficultyResponse> updateStatus(
                        @PathVariable UUID difficultyId,
                        @Valid @RequestBody UpdateMapStatusRequest request,
                        @AuthenticationPrincipal StaffUserDetails userDetails) {
                return ResponseEntity.ok(mapService.updateStatus(difficultyId, request,
                                userDetails.getStaffUser().getId()));
        }

        @Operation(summary = "Change difficulty category", description = "Reassigns a QUEUE or QUALIFIED difficulty to a different category. Not allowed on RANKED difficulties.")
        @PatchMapping("/{difficultyId}/category")
        @PreAuthorize("hasRole('RANKING_HEAD')")
        public ResponseEntity<MapDifficultyResponse> updateCategory(
                        @PathVariable UUID difficultyId,
                        @Valid @RequestBody UpdateMapCategoryRequest request,
                        @AuthenticationPrincipal StaffUserDetails userDetails) {
                return ResponseEntity.ok(mapService.updateCategory(difficultyId, request.getCategoryId(),
                                userDetails.getStaffUser().getId()));
        }

        @Operation(summary = "Set difficulty complexity", description = "Versioned complexity update - deactivates current and inserts new version")
        @PostMapping("/{difficultyId}/complexity")
        @PreAuthorize("hasRole('RANKING_HEAD')")
        public ResponseEntity<MapDifficultyResponse> updateComplexity(
                        @PathVariable UUID difficultyId,
                        @Valid @RequestBody UpdateMapComplexityRequest request,
                        @AuthenticationPrincipal StaffUserDetails userDetails) {
                return ResponseEntity.ok(mapService.updateComplexity(difficultyId, request,
                                userDetails.getLinkedUserId(), userDetails.getStaffUser().getId()));
        }

        @Operation(summary = "Deactivate a map difficulty", description = "Soft-removes a map difficulty from the ranking system (ranking_head/admin only)")
        @PatchMapping("/{difficultyId}/deactivate")
        @PreAuthorize("hasRole('RANKING_HEAD')")
        public ResponseEntity<Void> deactivate(
                        @PathVariable UUID difficultyId,
                        @AuthenticationPrincipal StaffUserDetails userDetails) {
                mapService.deactivate(difficultyId, userDetails.getStaffUser().getId());
                return ResponseEntity.noContent().build();
        }

        @Operation(summary = "Approve and apply a reweight", description = "Sets new complexity on a RANKED difficulty and recalculates scores asynchronously")
        @PostMapping("/{difficultyId}/reweight")
        @PreAuthorize("hasRole('RANKING_HEAD')")
        public ResponseEntity<MapDifficultyResponse> reweight(
                        @PathVariable UUID difficultyId,
                        @Valid @RequestBody ApproveReweightRequest request,
                        @AuthenticationPrincipal StaffUserDetails userDetails) {
                return ResponseEntity.ok(reweightService.reweight(difficultyId, request.getComplexity(),
                                request.getReason(), userDetails.getLinkedUserId(),
                                userDetails.getStaffUser().getId()));
        }

        @Operation(summary = "Approve and apply an unrank", description = "Moves a RANKED difficulty back to QUEUE status")
        @PostMapping("/{difficultyId}/unrank")
        @PreAuthorize("hasRole('RANKING_HEAD')")
        public ResponseEntity<MapDifficultyResponse> unrank(
                        @PathVariable UUID difficultyId,
                        @Valid @RequestBody ApproveUnrankRequest request,
                        @AuthenticationPrincipal StaffUserDetails userDetails) {
                return ResponseEntity.ok(unrankService.unrank(difficultyId, request.getReason(),
                                userDetails.getStaffUser().getId()));
        }

        @Operation(summary = "Bulk unrank", description = "Move multiple RANKED difficulties back to QUEUE in one request")
        @PostMapping("/bulk-unrank")
        @PreAuthorize("hasRole('RANKING_HEAD')")
        public ResponseEntity<List<MapDifficultyResponse>> bulkUnrank(
                        @Valid @RequestBody BulkUnrankRequest request,
                        @AuthenticationPrincipal StaffUserDetails userDetails) {
                return ResponseEntity.ok(unrankService.unrankBatch(request.getItems(),
                                userDetails.getStaffUser().getId()));
        }

        @Operation(summary = "Bulk reweight", description = "Sets new complexities on multiple RANKED difficulties with a shared reason and recalculates all scores asynchronously")
        @PostMapping("/bulk-reweight")
        @PreAuthorize("hasRole('RANKING_HEAD')")
        public ResponseEntity<Void> bulkReweight(
                        @Valid @RequestBody BulkReweightRequest request,
                        @AuthenticationPrincipal StaffUserDetails userDetails) {
                reweightService.bulkReweight(request.getItems(), request.getReason(),
                                userDetails.getLinkedUserId(), userDetails.getStaffUser().getId());
                return ResponseEntity.accepted().build();
        }

        @Operation(summary = "Recalculate scores for a difficulty", description = "Recalculates all scores based on the current active complexity. Skips if AP values are unchanged.")
        @PostMapping("/{difficultyId}/recalculate")
        @PreAuthorize("hasRole('RANKING_HEAD')")
        public ResponseEntity<Void> recalculate(@PathVariable UUID difficultyId) {
                reweightService.recalculateDifficulty(difficultyId);
                return ResponseEntity.accepted().build();
        }

        @Operation(summary = "Criteria checker webhook", description = "External service endpoint to update criteria pass/fail status on a difficulty")
        @PostMapping("/criteria")
        @PreAuthorize("hasRole('SERVICE')")
        public ResponseEntity<Void> updateCriteria(@Valid @RequestBody CriteriaWebhookRequest request) {
                criteriaService.updateCriteriaStatus(request.getMapDifficultyId(), request.getStatus());
                return ResponseEntity.noContent().build();
        }
}
