package com.accsaber.backend.controller.media;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.config.CdnProperties;
import com.accsaber.backend.service.media.MediaProcessingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/cdn")
@RequiredArgsConstructor
@Tag(name = "CDN")
public class CdnLimitsController {

    private final CdnProperties cdn;

    @Operation(summary = "Upload limits and accepted MIME types for the CDN")
    @GetMapping("/limits")
    public ResponseEntity<CdnLimitsResponse> getLimits() {
        return ResponseEntity.ok(CdnLimitsResponse.builder()
                .maxUploadBytes(cdn.getMaxUploadBytes())
                .uploadMaxDimension(cdn.getUploadMaxDimension())
                .allowedMimeTypes(MediaProcessingService.ALLOWED_MIME.stream().sorted().toList())
                .build());
    }

    @Getter
    @Builder
    public static class CdnLimitsResponse {
        private long maxUploadBytes;
        private int uploadMaxDimension;
        private List<String> allowedMimeTypes;
    }
}
