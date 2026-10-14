package com.accsaber.backend.model.dto.response;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CdnLimitsResponse {
    private long maxUploadBytes;
    private int uploadMaxDimension;
    private List<String> allowedMimeTypes;
}
