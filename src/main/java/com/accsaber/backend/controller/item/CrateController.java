package com.accsaber.backend.controller.item;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.model.dto.response.item.CrateOpenResponse;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.item.CrateService;
import com.accsaber.backend.service.item.ItemMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/users/me/crates")
@RequiredArgsConstructor
@Tag(name = "Crates")
public class CrateController {

    private final CrateService crateService;

    @Operation(summary = "Open one of my owned crate item links and receive a random reward")
    @PostMapping("/{linkId}/open")
    public ResponseEntity<CrateOpenResponse> open(
            @PathVariable UUID linkId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long me = requirePrincipal(principal).getUserId();
        return ResponseEntity.ok(ItemMapper.toCrateOpenResponse(crateService.openCrate(me, linkId)));
    }

    private PlayerUserDetails requirePrincipal(PlayerUserDetails principal) {
        if (principal == null) {
            throw new UnauthorizedException("Player authentication required");
        }
        return principal;
    }
}
