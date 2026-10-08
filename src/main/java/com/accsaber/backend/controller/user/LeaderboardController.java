package com.accsaber.backend.controller.user;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
import com.accsaber.backend.service.infra.CategoryService;
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
        private final CategoryService categoryService;

        @Operation(summary = "Get a leaderboard", description = "Category UUID or code, like true_acc. country takes "
                + "a two letter code and ranks by country position. Filters: name, headset, inactiveUsers=false, "
                + "relation which needs a login.")
        @GetMapping("/{category}")
        public ResponseEntity<Page<LeaderboardResponse>> getBoard(
                        @PathVariable String category,
                        @RequestParam(required = false) String country,
                        @RequestParam(required = false) String search,
                        @RequestParam(required = false) String hmd,
                        @RequestParam(defaultValue = "true") boolean inactiveUsers,
                        @RequestParam(required = false) UserRelationType relation,
                        @AuthenticationPrincipal PlayerUserDetails principal,
                        @PageableDefault(size = 20) Pageable pageable) {
                UUID categoryId = categoryService.resolveId(category);
                if (relation != null) {
                        List<Long> filter = userRelationService.findRelationFilterUserIds(
                                        requirePrincipal(principal).getUserId(),
                                        relation);
                        return ResponseEntity.ok(leaderboardService.getBoardFiltered(
                                        categoryId, country, search, hmd, inactiveUsers, filter, pageable));
                }
                return ResponseEntity.ok(
                                leaderboardService.getBoard(categoryId, country, search, hmd, inactiveUsers, pageable));
        }

        @Operation(summary = "XP leaderboard", description = "Same filters as the other leaderboards.")
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
                        List<Long> filter = userRelationService.findRelationFilterUserIds(
                                        requirePrincipal(principal).getUserId(),
                                        relation);
                        return ResponseEntity.ok(leaderboardService.getXpLeaderboardFiltered(
                                        country, search, hmd, inactiveUsers, filter, pageable));
                }
                return ResponseEntity
                                .ok(leaderboardService.getXpLeaderboard(country, search, hmd, inactiveUsers, pageable));
        }

        private PlayerUserDetails requirePrincipal(PlayerUserDetails principal) {
                if (principal == null) {
                        throw new UnauthorizedException("Player authentication required to filter by relation");
                }
                return principal;
        }
}
