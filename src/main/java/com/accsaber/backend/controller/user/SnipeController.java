package com.accsaber.backend.controller.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.response.score.SnipeComparisonResponse;
import com.accsaber.backend.service.snipe.SnipeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@Tag(name = "Snipe")
public class SnipeController {

    private static final int MAX_PAGE_SIZE = 100;

    private final SnipeService snipeService;

    @Operation(summary = "Get scores closest to a target player", description = "Returns paginated map difficulties where the target player outscores the sniper, ordered by smallest score gap first. "
            + "Each entry includes both players' active scores so the frontend can render the comparison. "
            + "Use `category` to limit results to a category (e.g. true_acc, standard_acc, tech_acc, overall).")
    @GetMapping("/{sniperId}/closest-to/{targetId}")
    public ResponseEntity<Page<SnipeComparisonResponse>> getClosestScores(
            @Parameter(description = "Steam ID of the sniping player") @PathVariable Long sniperId,
            @Parameter(description = "Steam ID of the target player") @PathVariable Long targetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Optional category code; omit for all categories") @RequestParam(required = false) String category) {
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
        return ResponseEntity.ok(snipeService.findClosestScores(sniperId, targetId, category, pageable));
    }
}
