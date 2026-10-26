package com.accsaber.backend.controller.clan;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.accsaber.backend.model.dto.request.clan.CreateClanRequest;
import com.accsaber.backend.model.dto.request.clan.UpdateClanPacifismRequest;
import com.accsaber.backend.model.dto.request.clan.UpdateClanRequest;
import com.accsaber.backend.model.dto.response.clan.ClanAuditEntryResponse;
import com.accsaber.backend.model.dto.response.clan.ClanResponse;
import com.accsaber.backend.model.entity.clan.ClanMedia;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.clan.ClanMediaService;
import com.accsaber.backend.service.clan.ClanService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/clans")
@RequiredArgsConstructor
@Tag(name = "Clans")
public class ClanController {

    private final ClanService clanService;
    private final ClanMediaService clanMediaService;

    @Operation(summary = "List clans",
            description = "Search by name or tag. Sort by name, createdAt, level or members.")
    @GetMapping
    public ResponseEntity<Page<ClanResponse>> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(clanService.list(search, pageable));
    }

    @Operation(summary = "Start a clan",
            description = "You need to be clanless and past your join cooldown. Name and tag must be free.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<ClanResponse> create(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @Valid @RequestBody CreateClanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clanService.create(principal.getUserId(), request));
    }

    @Operation(summary = "Get a clan", description = "Slug or id both work.")
    @GetMapping("/{slugOrId}")
    public ResponseEntity<ClanResponse> get(@PathVariable String slugOrId) {
        return ResponseEntity.ok(clanService.get(slugOrId));
    }

    @Operation(summary = "Edit your clan",
            description = "Founder only. Send only what changes. Renaming moves the slug.")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{clanId}")
    public ResponseEntity<ClanResponse> update(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID clanId,
            @Valid @RequestBody UpdateClanRequest request) {
        return ResponseEntity.ok(clanService.update(clanId, principal.getUserId(), request));
    }

    @Operation(summary = "Upload the clan icon or banner",
            description = "Founder only. The icon is a square image, the banner the wide artwork at the top of the "
                    + "clan page. Use the returned URL. It changes on every upload.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/{clanId}/{media:icon|banner}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ClanResponse> uploadMedia(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID clanId,
            @PathVariable ClanMedia media,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(clanMediaService.upload(clanId, principal.getUserId(), media, file));
    }

    @Operation(summary = "Turn pacifism on or off",
            description = "Founder only. A pacifist clan cannot declare war, get declared on or help in a war. "
                    + "Turning it on needs no open attacks, and works straight away or 7 days after the clan's last "
                    + "attack. Defending does not count. Turning it off is immediate.")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{clanId}/pacifism")
    public ResponseEntity<ClanResponse> pacifism(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID clanId,
            @Valid @RequestBody UpdateClanPacifismRequest request) {
        return ResponseEntity.ok(clanService.setPacifism(clanId, principal.getUserId(), request.enabled()));
    }

    @Operation(summary = "Remove the clan icon or banner", description = "Founder only.")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{clanId}/{media:icon|banner}")
    public ResponseEntity<ClanResponse> removeMedia(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID clanId,
            @PathVariable ClanMedia media) {
        return ResponseEntity.ok(clanMediaService.remove(clanId, principal.getUserId(), media));
    }

    @Operation(summary = "Disband a clan",
            description = "Founder only. Members leave with no cooldown. Name, tag and slug free up.")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{clanId}")
    public ResponseEntity<Void> disband(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID clanId) {
        clanService.disband(clanId, principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Clan audit log",
            description = "Members only. Newest first.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{clanId}/audit")
    public ResponseEntity<Page<ClanAuditEntryResponse>> audit(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID clanId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(clanService.audit(clanId, principal.getUserId(), pageable));
    }
}
