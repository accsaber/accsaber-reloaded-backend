package com.accsaber.backend.controller.media;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.config.CdnProperties;
import com.accsaber.backend.model.dto.response.CdnLimitsResponse;
import com.accsaber.backend.service.media.MediaProcessingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/cdn")
@RequiredArgsConstructor
@Tag(name = "Platform")
public class CdnLimitsController {

    private final CdnProperties cdn;

    @Operation(summary = "Upload limits", description = "Max size and allowed types per upload kind.")
    @GetMapping("/limits")
    public ResponseEntity<CdnLimitsResponse> getLimits() {
        return ResponseEntity.ok(CdnLimitsResponse.builder()
                .maxUploadBytes(cdn.getMaxUploadBytes())
                .uploadMaxDimension(cdn.getUploadMaxDimension())
                .allowedMimeTypes(MediaProcessingService.ALLOWED_MIME.stream().sorted().toList())
                .build());
    }
}
