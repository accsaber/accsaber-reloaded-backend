package com.accsaber.backend.controller.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.response.score.SnipeComparisonResponse;
import com.accsaber.backend.model.entity.score.SnipeSort;
import com.accsaber.backend.model.entity.score.SnipeUnplayed;
import com.accsaber.backend.service.snipe.SnipeQuery;
import com.accsaber.backend.service.snipe.SnipeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@Tag(name = "Players")
public class SnipeController {

    private static final int MAX_PAGE_SIZE = 100;

    private final SnipeService snipeService;

    @Operation(summary = "Snipe targets", description = "Maps where the target "
            + "beats the sniper, smallest gap first, with both scores. Unplayed maps come back with no sniper "
            + "score.")
    @GetMapping("/{sniperId}/closest-to/{targetId}")
    public ResponseEntity<Page<SnipeComparisonResponse>> getClosestScores(
            @Parameter(description = "User ID of the sniper") @PathVariable Long sniperId,
            @Parameter(description = "User ID of the target") @PathVariable Long targetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Category code, leave out for all") @RequestParam(required = false) String category,
            @Parameter(description = "GAP, AP_GAP, TARGET_AP, YOUR_AP or RANK_GAP") @RequestParam(defaultValue = "GAP") SnipeSort sort,
            @Parameter(description = "ASC or DESC, each sort has a default") @RequestParam(required = false) Sort.Direction direction,
            @Parameter(description = "EXCLUDE = played only, INCLUDE = add unplayed, ONLY = unplayed only") @RequestParam(required = false) SnipeUnplayed unplayed) {
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
        SnipeQuery query = new SnipeQuery(sniperId, targetId, category, sort, direction, unplayed);
        return ResponseEntity.ok(snipeService.findSnipeComparisons(query, pageable));
    }
}
