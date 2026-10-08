package com.accsaber.backend.controller.user;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.model.dto.request.UserRelationRequest;
import com.accsaber.backend.model.dto.response.player.UserRelationResponse;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.entity.user.UserRelationType;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.infra.CategoryService;
import com.accsaber.backend.service.player.UserRelationService;
import com.accsaber.backend.service.score.ScoreService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@Tag(name = "Players")
public class UserRelationController {

    private final UserRelationService relationService;
    private final ScoreService scoreService;
    private final CategoryService categoryService;

    @Operation(summary = "Your relations", description = "Only place your blocked list shows up. Pass type to "
            + "filter.")
    @GetMapping("/me/relations")
    public ResponseEntity<Page<UserRelationResponse>> getMyRelations(
            @RequestParam(required = false) UserRelationType type,
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PageableDefault(size = 20) Pageable pageable) {
        Long userId = requirePrincipal(principal).getUserId();
        return ResponseEntity.ok(relationService.findByUser(userId, type, true, pageable));
    }

    @Operation(summary = "Friends' scores", description = "Best AP first. Blocked type gives "
            + "an error. includePrincipal=true adds your own scores.")
    @GetMapping("/me/relations/scores")
    public ResponseEntity<Page<ScoreResponse>> getRelationScores(
            @RequestParam(required = false) UserRelationType type,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean includePrincipal,
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PageableDefault(size = 20, sort = "ap", direction = Sort.Direction.DESC) Pageable pageable) {
        Long userId = requirePrincipal(principal).getUserId();
        return ResponseEntity.ok(scoreService.findByUserRelations(userId, type,
                categoryService.resolveId(categoryId), search, includePrincipal, pageable));
    }

    @Operation(summary = "Follow, rival or block", description = "One directional. They do not get you on their list.")
    @PostMapping("/me/relations")
    public ResponseEntity<UserRelationResponse> createRelation(
            @Valid @RequestBody UserRelationRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long userId = requirePrincipal(principal).getUserId();
        UserRelationResponse response = relationService.create(userId, request.getTargetUserId(), request.getType());
        return ResponseEntity.status(201).body(response);
    }

    @Operation(summary = "Remove a relation", description = "Takes the relation ID, not the player's ID.")
    @DeleteMapping("/me/relations/{relationId}")
    public ResponseEntity<Void> deleteRelation(
            @PathVariable UUID relationId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long userId = requirePrincipal(principal).getUserId();
        relationService.delete(userId, relationId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Someone else's relations", description = "Outgoing by default, incoming is who "
            + "added them. Blocked never shows. Outgoing obeys their privacy settings. Private followers show up "
            + "in incoming as hidden entries with no details.")
    @GetMapping("/{userId}/relations")
    public ResponseEntity<Page<UserRelationResponse>> getUserRelations(
            @PathVariable Long userId,
            @RequestParam(required = false) UserRelationType type,
            @RequestParam(defaultValue = "outgoing") String direction,
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PageableDefault(size = 20) Pageable pageable) {
        Long viewerId = principal != null ? principal.getUserId() : null;
        if ("incoming".equalsIgnoreCase(direction)) {
            return ResponseEntity.ok(relationService.findByTarget(userId, type, viewerId, pageable));
        }
        return ResponseEntity.ok(relationService.findByUser(userId, type, false, viewerId, pageable));
    }

    private PlayerUserDetails requirePrincipal(PlayerUserDetails principal) {
        if (principal == null) {
            throw new UnauthorizedException("Player authentication required");
        }
        return principal;
    }
}
