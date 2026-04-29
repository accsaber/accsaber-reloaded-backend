package com.accsaber.backend.controller.user;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.model.dto.response.player.LeaderboardResponse;
import com.accsaber.backend.model.dto.response.player.XpLeaderboardResponse;
import com.accsaber.backend.model.entity.user.UserRelationType;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.player.UserRelationService;
import com.accsaber.backend.service.stats.LeaderboardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/leaderboards")
@RequiredArgsConstructor
@Tag(name = "Leaderboards")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;
    private final UserRelationService userRelationService;

    @Operation(summary = "Global leaderboard", description = "Paginated global rankings for a category, sorted by ranking ascending. Optionally filter by player name search, HMD, inactive status, or restrict to one of the authenticated player's relation types (follower/rival/blocked).")
    @GetMapping("/{categoryId}")
    public ResponseEntity<Page<LeaderboardResponse>> getGlobal(
            @PathVariable UUID categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String hmd,
            @RequestParam(defaultValue = "true") boolean inactiveUsers,
            @RequestParam(required = false) UserRelationType relation,
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PageableDefault(size = 20, sort = "ranking", direction = Sort.Direction.ASC) Pageable pageable) {
        if (relation != null) {
            List<Long> filter = userRelationService.findActiveTargetUserIds(requirePrincipal(principal).getUserId(),
                    relation);
            return ResponseEntity.ok(
                    leaderboardService.getGlobalFiltered(categoryId, search, hmd, inactiveUsers, filter, pageable));
        }
        return ResponseEntity.ok(leaderboardService.getGlobal(categoryId, search, hmd, inactiveUsers, pageable));
    }

    @Operation(summary = "Country leaderboard", description = "Paginated rankings filtered by country for a category, sorted by AP descending. Optionally filter by player name search, HMD, inactive status, or restrict to one of the authenticated player's relation types.")
    @GetMapping("/{categoryId}/country/{country}")
    public ResponseEntity<Page<LeaderboardResponse>> getByCountry(
            @PathVariable UUID categoryId,
            @PathVariable String country,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String hmd,
            @RequestParam(defaultValue = "true") boolean inactiveUsers,
            @RequestParam(required = false) UserRelationType relation,
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PageableDefault(size = 20, sort = "ranking", direction = Sort.Direction.ASC) Pageable pageable) {
        if (relation != null) {
            List<Long> filter = userRelationService.findActiveTargetUserIds(requirePrincipal(principal).getUserId(),
                    relation);
            return ResponseEntity.ok(leaderboardService.getByCountryFiltered(
                    categoryId, country, search, hmd, inactiveUsers, filter, pageable));
        }
        return ResponseEntity.ok(
                leaderboardService.getByCountry(categoryId, country, search, hmd, inactiveUsers, pageable));
    }

    @Operation(summary = "XP leaderboard", description = "Paginated XP rankings, sorted by total XP descending. Optional country, name search, HMD, inactive, or relation-type filter (auth required for relation).")
    @GetMapping("/xp")
    public ResponseEntity<Page<XpLeaderboardResponse>> getXpLeaderboard(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String hmd,
            @RequestParam(defaultValue = "true") boolean inactiveUsers,
            @RequestParam(required = false) UserRelationType relation,
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PageableDefault(size = 20) Pageable pageable) {
        if (relation != null) {
            List<Long> filter = userRelationService.findActiveTargetUserIds(requirePrincipal(principal).getUserId(),
                    relation);
            return ResponseEntity.ok(leaderboardService.getXpLeaderboardFiltered(
                    country, search, hmd, inactiveUsers, filter, pageable));
        }
        return ResponseEntity.ok(leaderboardService.getXpLeaderboard(country, search, hmd, inactiveUsers, pageable));
    }

    private PlayerUserDetails requirePrincipal(PlayerUserDetails principal) {
        if (principal == null) {
            throw new UnauthorizedException("Player authentication required to filter by relation");
        }
        return principal;
    }
}
