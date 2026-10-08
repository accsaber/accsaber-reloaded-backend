package com.accsaber.backend.controller.user;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.response.player.UserResponse;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.player.UserService;
import com.accsaber.backend.service.stats.StatisticsService;
import com.accsaber.backend.util.ProfileUrlResolver;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@Tag(name = "Players")
public class UserLinkController {

    private final UserService userService;
    private final StatisticsService statisticsService;
    private final DuplicateUserService duplicateUserService;
    private final ProfileUrlResolver profileUrlResolver;

    @Operation(summary = "Player from a profile link", description = "BeatLeader or ScoreSaber URL, or the "
            + "bare ID. Merged accounts give back the primary. Pass statistics=true for category stats.")
    @GetMapping("/link")
    public ResponseEntity<UserResponse> getUserByLink(
            @RequestParam String url,
            @RequestParam(defaultValue = "false") boolean statistics) {
        String platformId = profileUrlResolver.resolve(url);
        Long userId = duplicateUserService.resolvePrimaryUserId(Long.parseLong(platformId));
        UserResponse user = userService.findByUserId(userId);
        if (statistics) {
            user = user.withStatistics(statisticsService.findCategoryStatsByUser(userId));
        }
        return ResponseEntity.ok(user);
    }
}
