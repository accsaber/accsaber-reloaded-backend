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

import com.accsaber.backend.config.CdnProperties;
import com.accsaber.backend.model.dto.request.clan.CreateClanRequest;
import com.accsaber.backend.model.dto.request.clan.UpdateClanRequest;
import com.accsaber.backend.model.dto.response.clan.ClanAuditEntryResponse;
import com.accsaber.backend.model.dto.response.clan.ClanResponse;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.clan.ClanService;
import com.accsaber.backend.service.media.MediaFormat;
import com.accsaber.backend.service.media.MediaProcessingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/clans")
@RequiredArgsConstructor
@Tag(name = "Clans")
public class ClanController {

    public static final String CLAN_ICON_SUBDIR = "clan-icons";

    private final ClanService clanService;
    private final MediaProcessingService mediaProcessingService;
    private final CdnProperties cdn;

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

    @Operation(summary = "Upload the clan icon",
            description = "Founder only. Send a square image. Use the returned URL. It changes on every upload.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/{clanId}/icon", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ClanResponse> uploadIcon(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID clanId,
            @RequestPart("file") MultipartFile file) {
        clanService.assertCanCustomize(clanId, principal.getUserId());
        String url = mediaProcessingService.storeImage(file, CLAN_ICON_SUBDIR, clanId.toString(), MediaFormat.GIF,
                cdn.getAvatarMaxDimension());
        return ResponseEntity.ok(clanService.setIcon(clanId, principal.getUserId(), url));
    }

    @Operation(summary = "Remove the clan icon", description = "Founder only.")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{clanId}/icon")
    public ResponseEntity<ClanResponse> removeIcon(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID clanId) {
        ClanResponse clan = clanService.setIcon(clanId, principal.getUserId(), null);
        mediaProcessingService.deleteIfExists(CLAN_ICON_SUBDIR, clanId.toString());
        return ResponseEntity.ok(clan);
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
