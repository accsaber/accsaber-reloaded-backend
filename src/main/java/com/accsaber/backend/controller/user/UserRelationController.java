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

    @Operation(summary = "List your relations", description = "Everyone you have added, whether as someone you follow, a rival, "
            + "or someone you have blocked. Pass type to narrow it to one kind. This is the only place your blocked list shows "
            + "up, since it never appears on the public route.")
    @GetMapping("/me/relations")
    public ResponseEntity<Page<UserRelationResponse>> getMyRelations(
            @RequestParam(required = false) UserRelationType type,
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PageableDefault(size = 20) Pageable pageable) {
        Long userId = requirePrincipal(principal).getUserId();
        return ResponseEntity.ok(relationService.findByUser(userId, type, true, pageable));
    }

    @Operation(summary = "Get scores from the people you follow", description = "A feed of current scores from the players you "
            + "follow or have as rivals, best AP first, which is the basis of a friends activity view. Narrow it with type, "
            + "though blocked is rejected here for obvious reasons. Set includePrincipal=true to fold your own scores in "
            + "alongside theirs. Takes the same category, search and sort options as the player scores route.")
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

    @Operation(summary = "Add a relation", description = "Follow someone, mark them as a rival, or block them, depending on the "
            + "type you send. Relations are one directional, so adding someone does not add you to their list.")
    @PostMapping("/me/relations")
    public ResponseEntity<UserRelationResponse> createRelation(
            @Valid @RequestBody UserRelationRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long userId = requirePrincipal(principal).getUserId();
        UserRelationResponse response = relationService.create(userId, request.getTargetUserId(), request.getType());
        return ResponseEntity.status(201).body(response);
    }

    @Operation(summary = "Remove a relation", description = "Unfollow, drop a rival, or unblock, by the relation id rather than "
            + "the other player's id. The row is only marked inactive rather than actually deleted, so re-adding the same "
            + "person later picks the old one back up.")
    @DeleteMapping("/me/relations/{relationId}")
    public ResponseEntity<Void> deleteRelation(
            @PathVariable UUID relationId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long userId = requirePrincipal(principal).getUserId();
        relationService.delete(userId, relationId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List another player's relations", description = "Who a player has added, or who has added them, "
            + "depending on direction. Outgoing is the default and means people they added; incoming means people who added "
            + "them. Blocked never shows up here at all, so for your own blocked list use the me route. Bear in mind outgoing "
            + "is gated by that player's own privacy settings, so an empty page can mean they have chosen to keep it to "
            + "themselves rather than that they have nobody. Incoming works the other way round: a player who keeps their "
            + "own following or rivals list private still shows up in someone else's incoming list, but as an anonymous "
            + "Hidden Follower or Hidden Rival entry with hidden set to true and no id, avatar or country on it.")
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
