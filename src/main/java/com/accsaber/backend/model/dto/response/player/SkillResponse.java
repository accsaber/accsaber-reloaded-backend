package com.accsaber.backend.model.dto.response.player;

import java.util.List;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SkillResponse {

    String userId;
    List<SkillCategoryResponse> skills;
}
