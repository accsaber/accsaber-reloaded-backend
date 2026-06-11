package com.accsaber.backend.controller.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.service.media.CdnSyncService;
import com.accsaber.backend.service.media.MediaProcessingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin/cdn")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin CDN")
public class AdminCdnController {

    private final CdnSyncService cdnSyncService;
    private final MediaProcessingService mediaProcessingService;

    @Operation(summary = "Backfill all active map covers into the CDN")
    @PostMapping("/backfill/maps")
    public ResponseEntity<Void> backfillMaps(@RequestParam(defaultValue = "false") boolean force) {
        cdnSyncService.backfillAllMapCovers(force);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Backfill all active user avatars into the CDN")
    @PostMapping("/backfill/avatars")
    public ResponseEntity<Void> backfillAvatars(@RequestParam(defaultValue = "false") boolean force) {
        cdnSyncService.backfillAllUserAvatars(force);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "chmod every file under the CDN storage path to rw-r--r-- (and dirs to rwxr-xr-x)")
    @PostMapping("/repair-permissions")
    public ResponseEntity<Integer> repairPermissions() {
        return ResponseEntity.ok(mediaProcessingService.repairAllPermissions());
    }

    @Operation(summary = "Regenerate the static (.png/.gif) sibling for every .avif on disk and sync entity URLs accordingly. Pass skipAvatars=true if you've already run the avatar backfill.")
    @PostMapping("/regenerate-static-variants")
    public ResponseEntity<Void> regenerateStaticVariants(
            @RequestParam(defaultValue = "false") boolean skipAvatars) {
        cdnSyncService.regenerateAllStaticVariants(skipAvatars);
        return ResponseEntity.accepted().build();
    }
}
