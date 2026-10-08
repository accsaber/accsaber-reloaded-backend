package com.accsaber.backend.controller.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.service.media.MediaProcessingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin/cdn")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin - Operations")
public class AdminCdnController {

    private final MediaProcessingService mediaProcessingService;

    @Operation(summary = "Fix CDN file perms")
    @PostMapping("/repair-permissions")
    public ResponseEntity<Integer> repairPermissions() {
        return ResponseEntity.ok(mediaProcessingService.repairAllPermissions());
    }

}
