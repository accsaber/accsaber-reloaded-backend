package com.accsaber.backend.controller.user;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
import com.accsaber.backend.model.entity.user.UserRelationType;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.player.UserRelationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Relations")
public class UserRelationController {

    private final UserRelationService relationService;

    @Operation(summary = "List authenticated player's relations (followers/rivals/blocked)")
    @GetMapping("/me/relations")
    public ResponseEntity<Page<UserRelationResponse>> getMyRelations(
            @RequestParam(required = false) UserRelationType type,
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PageableDefault(size = 20) Pageable pageable) {
        Long userId = requirePrincipal(principal).getUserId();
        return ResponseEntity.ok(relationService.findByUser(userId, type, true, pageable));
    }

    @Operation(summary = "Create a relation (follower, rival, or blocked) targeting another player")
    @PostMapping("/me/relations")
    public ResponseEntity<UserRelationResponse> createRelation(
            @Valid @RequestBody UserRelationRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long userId = requirePrincipal(principal).getUserId();
        UserRelationResponse response = relationService.create(userId, request.getTargetUserId(), request.getType());
        return ResponseEntity.status(201).body(response);
    }

    @Operation(summary = "Delete (soft) one of the authenticated player's relations")
    @DeleteMapping("/me/relations/{relationId}")
    public ResponseEntity<Void> deleteRelation(
            @PathVariable UUID relationId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long userId = requirePrincipal(principal).getUserId();
        relationService.delete(userId, relationId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List a player's public relations (follower/rival only). Blocked list is private.")
    @GetMapping("/{userId}/relations")
    public ResponseEntity<Page<UserRelationResponse>> getUserRelations(
            @PathVariable Long userId,
            @RequestParam(required = false) UserRelationType type,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(relationService.findByUser(userId, type, false, pageable));
    }

    private PlayerUserDetails requirePrincipal(PlayerUserDetails principal) {
        if (principal == null) {
            throw new UnauthorizedException("Player authentication required");
        }
        return principal;
    }
}
