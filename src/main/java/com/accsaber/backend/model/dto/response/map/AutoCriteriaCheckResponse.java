package com.accsaber.backend.model.dto.response.map;

import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.entity.AutoCriteriaStatus;
import com.accsaber.backend.model.entity.map.Difficulty;

import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class AutoCriteriaCheckResponse {
    Difficulty difficulty;
    UUID categoryId;
    AutoCriteriaStatus status;
    List<String> failures;
}
