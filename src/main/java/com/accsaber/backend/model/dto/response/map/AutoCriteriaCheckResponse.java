package com.accsaber.backend.model.dto.response.map;

import java.util.List;

import com.accsaber.backend.model.entity.AutoCriteriaStatus;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AutoCriteriaCheckResponse {
    AutoCriteriaStatus status;
    List<String> failures;
}
