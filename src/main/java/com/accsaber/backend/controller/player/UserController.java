package com.accsaber.backend.controller.player;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.response.player.UserResponse;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.service.player.UserService;
import com.accsaber.backend.service.score.ScoreService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@Tag(name = "Players")
public class UserController {

    private final UserService userService;
    private final ScoreService scoreService;

    @Operation(summary = "Get user profile", description = "Returns a player profile by Steam ID")
    @GetMapping("/{steamId}")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long steamId) {
        return ResponseEntity.ok(userService.findBySteamId(steamId));
    }

    @Operation(summary = "Get user scores", description = "Paginated list of a player's active scores, optionally filtered by category")
    @GetMapping("/{steamId}/scores")
    public ResponseEntity<Page<ScoreResponse>> getUserScores(
            @PathVariable Long steamId,
            @RequestParam(required = false) UUID categoryId,
            @PageableDefault(size = 20, sort = "ap", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(scoreService.findByUser(steamId, categoryId, pageable));
    }
}
