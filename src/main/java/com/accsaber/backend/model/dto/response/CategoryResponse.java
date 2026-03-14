package com.accsaber.backend.model.dto.response;

import java.util.UUID;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CategoryResponse {

    UUID id;
    String code;
    String name;
    String description;
    CurveResponse scoreCurve;
    CurveResponse weightCurve;
    boolean countForOverall;
}
